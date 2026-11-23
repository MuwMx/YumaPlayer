/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

// usb_pass.cpp

// mochy's own usb audio driver: android hands over the dac's file descriptor, this takes the dac from the kernel's driver
// and streams isochronous packets at the song's own rate, nothing mixed or scaled on the way

#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <unistd.h>
#include <poll.h>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <android/log.h>

#define TAG "UsbPass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

// the ints kotlin hands over per plan, in UsbPass.kt's order
constexpr int kPlanInts = 18;

// urbs in flight and how long each one plays: about 64 ms queued at the dac, so a worker woken late in the background or with the screen off doesn't run it dry (issue 234)
constexpr int kSlots = 8;
constexpr uint32_t kUrbUs = 8000;
constexpr int kMaxPackets = 64;

struct Plan {
    int control, streaming, alt, uac2, clock, selector, selectorPin;
    int endpoint, attributes, maxPacket, interval, rateControl;
    int feedback, feedbackMaxPacket;
    int rate, channels, subslot, bits;
};

Plan planAt(const jint *v) {
    return Plan{v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14], v[15], v[16], v[17]};
}

int control(int fd, uint8_t type, uint8_t request, uint16_t value, uint16_t index, void *data, uint16_t length) {
    usbdevfs_ctrltransfer transfer{};
    transfer.bRequestType = type;
    transfer.bRequest = request;
    transfer.wValue = value;
    transfer.wIndex = index;
    transfer.wLength = length;
    transfer.timeout = 1000;
    transfer.data = data;
    int result = ioctl(fd, USBDEVFS_CONTROL, &transfer);
    return result < 0 ? -errno : result;
}

// one urb and the buffer it sends; it stays allocated until the kernel has handed it back
struct Slot {
    usbdevfs_urb *urb = nullptr;
    std::vector<uint8_t> bytes;
    uint64_t musicFrames = 0;
    uint64_t generation = 0;
    bool inFlight = false;

    Slot(int packets, size_t packetBytes) : bytes(static_cast<size_t>(packets) * packetBytes) {
        urb = static_cast<usbdevfs_urb *>(calloc(1, sizeof(usbdevfs_urb) + packets * sizeof(usbdevfs_iso_packet_desc)));
    }
    ~Slot() { free(urb); }
};

class Stream {
public:
    int fd;
    Plan plan{};
    int chosen = -1;
    bool highSpeed = true;
    uint32_t periodUs = 1000;
    int packetsPerUrb = 1;
    size_t frameBytes = 4;
    std::vector<int> claimed;

    // what kotlin wrote and the worker hasn't sent yet, guarded by lock
    std::mutex lock;
    std::condition_variable changed;
    std::vector<uint8_t> ring;
    size_t ringStart = 0, ringSize = 0;
    uint64_t generation = 0;
    uint64_t played = 0;
    bool playing = false;
    bool closing = false;
    std::string error;

    std::vector<Slot *> slots;
    Slot *feedbackSlot = nullptr;
    double nominal = 0, pace = 0, remainder = 0;
    std::thread worker;

    explicit Stream(int fd) : fd(fd) {}

    ~Stream() {
        for (Slot *s : slots) delete s;
        delete feedbackSlot;
    }

    bool claim(int iface) {
        for (int c : claimed) if (c == iface) return true;
        // the kernel's usb audio driver lets go, and the interface is ours until we hand it back
        usbdevfs_disconnect_claim take{};
        take.interface = static_cast<unsigned>(iface);
        take.flags = 0;
        if (ioctl(fd, USBDEVFS_DISCONNECT_CLAIM, &take) < 0) {
            unsigned int number = static_cast<unsigned>(iface);
            if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &number) < 0) return false;
        }
        claimed.push_back(iface);
        return true;
    }

    void giveBack() {
        for (auto it = claimed.rbegin(); it != claimed.rend(); ++it) {
            unsigned int number = static_cast<unsigned>(*it);
            ioctl(fd, USBDEVFS_RELEASEINTERFACE, &number);
            // android's own driver takes it again, so the dac is an ordinary output once more
            usbdevfs_ioctl command{};
            command.ifno = *it;
            command.ioctl_code = USBDEVFS_CONNECT;
            command.data = nullptr;
            ioctl(fd, USBDEVFS_IOCTL, &command);
        }
        claimed.clear();
    }

    bool setAlt(int iface, int alt) {
        usbdevfs_setinterface set{};
        set.interface = static_cast<unsigned>(iface);
        set.altsetting = static_cast<unsigned>(alt);
        return ioctl(fd, USBDEVFS_SETINTERFACE, &set) == 0;
    }

    // sets the dac's clock to the song's rate and reads it back: a dac that quietly stays at another rate would play the song too fast or slow
    bool setRate(const Plan &p) {
        if (p.uac2) {
            if (p.selector > 0 && p.selectorPin > 0) {
                uint8_t pin = static_cast<uint8_t>(p.selectorPin);
                control(fd, 0x21, 0x01, 0x0100, static_cast<uint16_t>((p.selector << 8) | p.control), &pin, 1);
            }
            uint16_t index = static_cast<uint16_t>((p.clock << 8) | p.control);
            uint32_t wanted = static_cast<uint32_t>(p.rate), now = 0;
            if (control(fd, 0xa1, 0x01, 0x0100, index, &now, 4) == 4 && now == wanted) return true;
            control(fd, 0x21, 0x01, 0x0100, index, &wanted, 4);
            now = 0;
            bool ok = control(fd, 0xa1, 0x01, 0x0100, index, &now, 4) == 4 && now == wanted;
            // many uac2 dacs need a few milliseconds for their pll to lock after a rate change, else the first audio bursts with static
            if (ok) usleep(15000);
            return ok;
        }
        if (!p.rateControl) return true;
        uint8_t wanted[3] = {static_cast<uint8_t>(p.rate), static_cast<uint8_t>(p.rate >> 8), static_cast<uint8_t>(p.rate >> 16)};
        uint8_t now[3] = {};
        control(fd, 0x22, 0x01, 0x0100, static_cast<uint16_t>(p.endpoint), wanted, 3);
        // some uac1 dacs take the rate but can't say it back; only a different answer counts as refused
        int read = control(fd, 0xa2, 0x81, 0x0100, static_cast<uint16_t>(p.endpoint), now, 3);
        bool ok = read != 3 || memcmp(now, wanted, 3) == 0;
        if (ok) usleep(15000);
        return ok;
    }

    bool configure(const Plan &p) {
        uint32_t period = (highSpeed ? 125u : 1000u) << (p.interval - 1);
        double perPacket = static_cast<double>(p.rate) * period / 1e6;
        size_t bytes = static_cast<size_t>(p.channels) * p.subslot;
        // room for one frame more than nominal, which is as far as a dac's feedback ever asks
        if ((static_cast<size_t>(std::ceil(perPacket)) + 1) * bytes > static_cast<size_t>(p.maxPacket)) return false;
        if (!claim(p.control) || !claim(p.streaming)) return false;
        if (!setAlt(p.streaming, 0)) return false;
        if (p.uac2 && !setRate(p)) return false;
        if (!setAlt(p.streaming, p.alt)) return false;
        if (!p.uac2 && !setRate(p)) return false;
        plan = p;
        periodUs = period;
        frameBytes = bytes;
        nominal = perPacket;
        pace = perPacket;
        packetsPerUrb = static_cast<int>(std::min<uint32_t>(std::max<uint32_t>(kUrbUs / period, 1), kMaxPackets));
        return true;
    }

    // frames for the next packet, carrying the fraction over so 44.1 khz in 1 ms packets comes out exact
    size_t nextPacketFrames() {
        remainder += pace;
        double whole = std::floor(remainder);
        remainder -= whole;
        return static_cast<size_t>(whole);
    }

    // the dac says how fast it really plays; both usb speeds' number formats are tried, and anything far from the nominal rate is ignored
    void readFeedback(const uint8_t *data, int length) {
        double perFrame;
        if (length >= 4) {
            uint32_t v = data[0] | (data[1] << 8) | (data[2] << 16) | (static_cast<uint32_t>(data[3]) << 24);
            perFrame = v / 65536.0;
        } else if (length == 3) {
            uint32_t v = data[0] | (data[1] << 8) | (data[2] << 16);
            perFrame = v / 16384.0;
        } else {
            return;
        }
        double framesPerUnit = highSpeed ? perFrame * (periodUs / 125.0) : perFrame * (periodUs / 1000.0);
        for (double candidate : {framesPerUnit, framesPerUnit * 4.0, framesPerUnit / 4.0}) {
            if (std::fabs(candidate - nominal) <= nominal * 0.02) {
                pace = candidate;
                return;
            }
        }
    }

    void fill(Slot *slot) {
        usbdevfs_urb *urb = slot->urb;
        size_t total = 0;
        size_t lengths[kMaxPackets];
        for (int i = 0; i < packetsPerUrb; ++i) {
            size_t frames = nextPacketFrames();
            lengths[i] = std::min(frames * frameBytes, static_cast<size_t>(plan.maxPacket) / frameBytes * frameBytes);
            total += lengths[i];
        }
        size_t music = 0;
        {
            std::lock_guard<std::mutex> guard(lock);
            if (playing) {
                music = std::min(total, ringSize) / frameBytes * frameBytes;
                size_t first = std::min(music, ring.size() - ringStart);
                memcpy(slot->bytes.data(), ring.data() + ringStart, first);
                memcpy(slot->bytes.data() + first, ring.data(), music - first);
                ringStart = (ringStart + music) % ring.size();
                ringSize -= music;
            }
            slot->generation = generation;
            changed.notify_all();
        }
        // a short queue or a pause goes out as silence, so the dac's clock never loses lock
        memset(slot->bytes.data() + music, 0, total - music);
        slot->musicFrames = music / frameBytes;

        memset(urb, 0, sizeof(usbdevfs_urb) + packetsPerUrb * sizeof(usbdevfs_iso_packet_desc));
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(plan.endpoint);
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = slot->bytes.data();
        urb->buffer_length = static_cast<int>(total);
        urb->number_of_packets = packetsPerUrb;
        urb->usercontext = slot;
        for (int i = 0; i < packetsPerUrb; ++i) urb->iso_frame_desc[i].length = static_cast<unsigned>(lengths[i]);
    }

    void fillFeedback() {
        usbdevfs_urb *urb = feedbackSlot->urb;
        memset(urb, 0, sizeof(usbdevfs_urb) + sizeof(usbdevfs_iso_packet_desc));
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = static_cast<unsigned char>(plan.feedback);
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer = feedbackSlot->bytes.data();
        urb->buffer_length = plan.feedbackMaxPacket;
        urb->number_of_packets = 1;
        urb->usercontext = feedbackSlot;
        urb->iso_frame_desc[0].length = static_cast<unsigned>(plan.feedbackMaxPacket);
    }

    bool submit(Slot *slot) {
        if (ioctl(fd, USBDEVFS_SUBMITURB, slot->urb) < 0) return false;
        slot->inFlight = true;
        return true;
    }

    void fail(const std::string &why) {
        std::lock_guard<std::mutex> guard(lock);
        if (error.empty()) error = why;
        playing = false;
        changed.notify_all();
    }

    void run() {
        // best effort: a late packet is a click, so the worker asks to be scheduled first, as urgently as android lets an app
        if (setpriority(PRIO_PROCESS, 0, -19) != 0) setpriority(PRIO_PROCESS, 0, -16);
        for (int i = 0; i < kSlots; ++i) slots.push_back(new Slot(packetsPerUrb, static_cast<size_t>(plan.maxPacket)));
        if (plan.feedback >= 0 && plan.feedbackMaxPacket > 0) feedbackSlot = new Slot(1, static_cast<size_t>(plan.feedbackMaxPacket));
        for (Slot *s : slots) {
            fill(s);
            if (!submit(s)) { fail("the dac refused the stream"); break; }
        }
        if (feedbackSlot) {
            fillFeedback();
            submit(feedbackSlot);
        }
        while (true) {
            {
                std::lock_guard<std::mutex> guard(lock);
                if (closing || !error.empty()) break;
            }
            pollfd waiter{fd, POLLOUT, 0};
            int ready = poll(&waiter, 1, 50);
            if (ready < 0 && errno != EINTR) { fail("lost the dac"); break; }
            if (waiter.revents & (POLLHUP | POLLERR)) { fail("the dac was unplugged"); break; }
            usbdevfs_urb *done = nullptr;
            while (ioctl(fd, USBDEVFS_REAPURBNDELAY, &done) == 0 && done) {
                Slot *slot = static_cast<Slot *>(done->usercontext);
                slot->inFlight = false;
                if (done->status == -ENODEV || done->status == -ESHUTDOWN) { fail("the dac was unplugged"); break; }
                if (slot == feedbackSlot) {
                    if (done->status == 0 && done->iso_frame_desc[0].status == 0) {
                        readFeedback(slot->bytes.data(), static_cast<int>(done->iso_frame_desc[0].actual_length));
                    }
                    fillFeedback();
                    submit(slot);
                    continue;
                }
                {
                    std::lock_guard<std::mutex> guard(lock);
                    if (slot->generation == generation) played += slot->musicFrames;
                    changed.notify_all();
                    if (closing) continue;
                }
                fill(slot);
                if (!submit(slot)) { fail("the dac stopped taking packets"); break; }
                done = nullptr;
            }
        }
        // the kernel still owns every urb in flight; each is cancelled and collected before its memory goes
        for (Slot *s : slots) if (s->inFlight) ioctl(fd, USBDEVFS_DISCARDURB, s->urb);
        if (feedbackSlot && feedbackSlot->inFlight) ioctl(fd, USBDEVFS_DISCARDURB, feedbackSlot->urb);
        for (int tries = 0; tries < 200; ++tries) {
            bool any = false;
            for (Slot *s : slots) any |= s->inFlight;
            if (feedbackSlot) any |= feedbackSlot->inFlight;
            if (!any) break;
            usbdevfs_urb *done = nullptr;
            if (ioctl(fd, USBDEVFS_REAPURBNDELAY, &done) == 0 && done) {
                static_cast<Slot *>(done->usercontext)->inFlight = false;
            } else {
                usleep(5000);
            }
        }
    }

    void close() {
        {
            std::lock_guard<std::mutex> guard(lock);
            closing = true;
            changed.notify_all();
        }
        if (worker.joinable()) worker.join();
        if (plan.alt > 0) setAlt(plan.streaming, 0);
        giveBack();
    }
};

void throwState(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

Stream *streamOf(jlong handle) { return reinterpret_cast<Stream *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbOpen(JNIEnv *env, jobject, jint fd, jintArray plans, jint bcdUsb) {
    jsize count = env->GetArrayLength(plans);
    if (count <= 0 || count % kPlanInts != 0) {
        throwState(env, "no way to stream to this dac");
        return 0;
    }
    std::vector<jint> values(static_cast<size_t>(count));
    env->GetIntArrayRegion(plans, 0, count, values.data());
    auto *stream = new Stream(fd);
    int speed = ioctl(fd, USBDEVFS_GET_SPEED);
    stream->highSpeed = speed >= 0 ? speed >= 3 : bcdUsb >= 0x0200;
    for (int i = 0; i < count / kPlanInts; ++i) {
        Plan p = planAt(values.data() + i * kPlanInts);
        if (stream->configure(p)) {
            stream->chosen = i;
            break;
        }
        LOGI("plan %d (%d Hz, %d-bit in %d bytes) refused", i, p.rate, p.bits, p.subslot);
    }
    if (stream->chosen < 0) {
        stream->giveBack();
        delete stream;
        throwState(env, "the dac wouldn't run at this song's rate");
        return 0;
    }
    // a second of audio waits here, so the player can sleep through a slow background wake-up; pause and seek don't wait on it
    size_t ringBytes = std::max<size_t>(static_cast<size_t>(stream->plan.rate), 4096) * stream->frameBytes;
    stream->ring.resize(ringBytes);
    stream->worker = std::thread([stream] { stream->run(); });
    return reinterpret_cast<jlong>(stream);
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbChosen(JNIEnv *, jobject, jlong handle) {
    return streamOf(handle)->chosen;
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbCapacity(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    return static_cast<jint>(s->ring.size() / s->frameBytes);
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbWrite(JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset, jint length) {
    Stream *s = streamOf(handle);
    auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    if (!data) {
        throwState(env, "not a direct buffer");
        return 0;
    }
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return 0;
    }
    size_t room = s->ring.size() - s->ringSize;
    size_t take = std::min(static_cast<size_t>(length), room) / s->frameBytes * s->frameBytes;
    size_t end = (s->ringStart + s->ringSize) % s->ring.size();
    size_t first = std::min(take, s->ring.size() - end);
    memcpy(s->ring.data() + end, data + offset, first);
    memcpy(s->ring.data(), data + offset + first, take - first);
    s->ringSize += take;
    return static_cast<jint>(take);
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbPlay(JNIEnv *env, jobject, jlong handle, jboolean playing) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return;
    }
    s->playing = playing;
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbFlush(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    s->playing = false;
    s->ringStart = 0;
    s->ringSize = 0;
    s->played = 0;
    s->pace = s->nominal;
    s->remainder = 0;
    // packets already with the dac finish, but no longer count towards the new start
    s->generation++;
}

JNIEXPORT jlong JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbPlayed(JNIEnv *env, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    std::lock_guard<std::mutex> guard(s->lock);
    if (!s->error.empty()) {
        throwState(env, s->error.c_str());
        return 0;
    }
    return static_cast<jlong>(s->played);
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbControl(JNIEnv *env, jobject, jlong handle, jint type, jint request, jint value, jint index, jbyteArray data) {
    Stream *s = streamOf(handle);
    jsize length = env->GetArrayLength(data);
    std::vector<uint8_t> bytes(static_cast<size_t>(length));
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));
    int result = control(s->fd, static_cast<uint8_t>(type), static_cast<uint8_t>(request), static_cast<uint16_t>(value),
                         static_cast<uint16_t>(index), bytes.data(), static_cast<uint16_t>(length));
    if (result > 0 && (type & 0x80)) env->SetByteArrayRegion(data, 0, result, reinterpret_cast<const jbyte *>(bytes.data()));
    return result;
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_usbClose(JNIEnv *, jobject, jlong handle) {
    Stream *s = streamOf(handle);
    s->close();
    delete s;
}

}  // extern "C"
