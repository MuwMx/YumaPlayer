/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

// exclusive_pass.cpp

// an aaudio stream only mochy plays to: exclusive is only a request, so the stream is kept only when android granted
// exactly the rate, format and channels asked for. aaudio is looked up at runtime, so this library still loads on android 7

#include <jni.h>
#include <aaudio/AAudio.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "ExclusivePass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

// the aaudio calls we need, found by name; the ones newer android added stay null on older phones
struct AAudioApi {
    bool ready = false;
    aaudio_result_t (*createBuilder)(AAudioStreamBuilder **) = nullptr;
    void (*setDeviceId)(AAudioStreamBuilder *, int32_t) = nullptr;
    void (*setSharingMode)(AAudioStreamBuilder *, aaudio_sharing_mode_t) = nullptr;
    void (*setPerformanceMode)(AAudioStreamBuilder *, aaudio_performance_mode_t) = nullptr;
    void (*setFormat)(AAudioStreamBuilder *, aaudio_format_t) = nullptr;
    void (*setSampleRate)(AAudioStreamBuilder *, int32_t) = nullptr;
    void (*setChannelCount)(AAudioStreamBuilder *, int32_t) = nullptr;
    void (*setDirection)(AAudioStreamBuilder *, aaudio_direction_t) = nullptr;
    void (*setUsage)(AAudioStreamBuilder *, aaudio_usage_t) = nullptr;
    void (*setContentType)(AAudioStreamBuilder *, aaudio_content_type_t) = nullptr;
    void (*setDataCallback)(AAudioStreamBuilder *, AAudioStream_dataCallback, void *) = nullptr;
    void (*setErrorCallback)(AAudioStreamBuilder *, AAudioStream_errorCallback, void *) = nullptr;
    aaudio_result_t (*openStream)(AAudioStreamBuilder *, AAudioStream **) = nullptr;
    aaudio_result_t (*deleteBuilder)(AAudioStreamBuilder *) = nullptr;
    aaudio_result_t (*start)(AAudioStream *) = nullptr;
    aaudio_result_t (*pause)(AAudioStream *) = nullptr;
    aaudio_result_t (*flush)(AAudioStream *) = nullptr;
    aaudio_result_t (*stop)(AAudioStream *) = nullptr;
    aaudio_result_t (*close)(AAudioStream *) = nullptr;
    aaudio_result_t (*waitForStateChange)(AAudioStream *, aaudio_stream_state_t, aaudio_stream_state_t *, int64_t) = nullptr;
    aaudio_sharing_mode_t (*sharingMode)(AAudioStream *) = nullptr;
    int32_t (*sampleRate)(AAudioStream *) = nullptr;
    int32_t (*channelCount)(AAudioStream *) = nullptr;
    aaudio_format_t (*format)(AAudioStream *) = nullptr;
    int32_t (*deviceId)(AAudioStream *) = nullptr;
    int32_t (*capacity)(AAudioStream *) = nullptr;
    int32_t (*hardwareSampleRate)(AAudioStream *) = nullptr;
    aaudio_format_t (*hardwareFormat)(AAudioStream *) = nullptr;
};

AAudioApi &api() {
    static AAudioApi a;
    static std::once_flag once;
    std::call_once(once, [] {
        void *lib = dlopen("libaaudio.so", RTLD_NOW);
        if (!lib) return;
        auto find = [lib](const char *name) { return dlsym(lib, name); };
        a.createBuilder = reinterpret_cast<decltype(a.createBuilder)>(find("AAudio_createStreamBuilder"));
        a.setDeviceId = reinterpret_cast<decltype(a.setDeviceId)>(find("AAudioStreamBuilder_setDeviceId"));
        a.setSharingMode = reinterpret_cast<decltype(a.setSharingMode)>(find("AAudioStreamBuilder_setSharingMode"));
        a.setPerformanceMode = reinterpret_cast<decltype(a.setPerformanceMode)>(find("AAudioStreamBuilder_setPerformanceMode"));
        a.setFormat = reinterpret_cast<decltype(a.setFormat)>(find("AAudioStreamBuilder_setFormat"));
        a.setSampleRate = reinterpret_cast<decltype(a.setSampleRate)>(find("AAudioStreamBuilder_setSampleRate"));
        a.setChannelCount = reinterpret_cast<decltype(a.setChannelCount)>(find("AAudioStreamBuilder_setChannelCount"));
        a.setDirection = reinterpret_cast<decltype(a.setDirection)>(find("AAudioStreamBuilder_setDirection"));
        a.setUsage = reinterpret_cast<decltype(a.setUsage)>(find("AAudioStreamBuilder_setUsage"));
        a.setContentType = reinterpret_cast<decltype(a.setContentType)>(find("AAudioStreamBuilder_setContentType"));
        a.setDataCallback = reinterpret_cast<decltype(a.setDataCallback)>(find("AAudioStreamBuilder_setDataCallback"));
        a.setErrorCallback = reinterpret_cast<decltype(a.setErrorCallback)>(find("AAudioStreamBuilder_setErrorCallback"));
        a.openStream = reinterpret_cast<decltype(a.openStream)>(find("AAudioStreamBuilder_openStream"));
        a.deleteBuilder = reinterpret_cast<decltype(a.deleteBuilder)>(find("AAudioStreamBuilder_delete"));
        a.start = reinterpret_cast<decltype(a.start)>(find("AAudioStream_requestStart"));
        a.pause = reinterpret_cast<decltype(a.pause)>(find("AAudioStream_requestPause"));
        a.flush = reinterpret_cast<decltype(a.flush)>(find("AAudioStream_requestFlush"));
        a.stop = reinterpret_cast<decltype(a.stop)>(find("AAudioStream_requestStop"));
        a.close = reinterpret_cast<decltype(a.close)>(find("AAudioStream_close"));
        a.waitForStateChange = reinterpret_cast<decltype(a.waitForStateChange)>(find("AAudioStream_waitForStateChange"));
        a.sharingMode = reinterpret_cast<decltype(a.sharingMode)>(find("AAudioStream_getSharingMode"));
        a.sampleRate = reinterpret_cast<decltype(a.sampleRate)>(find("AAudioStream_getSampleRate"));
        a.channelCount = reinterpret_cast<decltype(a.channelCount)>(find("AAudioStream_getChannelCount"));
        a.format = reinterpret_cast<decltype(a.format)>(find("AAudioStream_getFormat"));
        a.deviceId = reinterpret_cast<decltype(a.deviceId)>(find("AAudioStream_getDeviceId"));
        a.capacity = reinterpret_cast<decltype(a.capacity)>(find("AAudioStream_getBufferCapacityInFrames"));
        a.hardwareSampleRate = reinterpret_cast<decltype(a.hardwareSampleRate)>(find("AAudioStream_getHardwareSampleRate"));
        a.hardwareFormat = reinterpret_cast<decltype(a.hardwareFormat)>(find("AAudioStream_getHardwareFormat"));
        a.ready = a.createBuilder && a.setSharingMode && a.setPerformanceMode && a.setFormat && a.setSampleRate &&
                  a.setChannelCount && a.setDirection && a.setDataCallback && a.openStream && a.deleteBuilder && a.start &&
                  a.pause && a.flush && a.stop && a.close && a.sharingMode && a.sampleRate && a.channelCount && a.format;
    });
    return a;
}

// the integer containers aaudio names; 24 and 32-bit only exist from android 12
constexpr aaudio_format_t kI24 = 3;
constexpr aaudio_format_t kI32 = 4;

struct Exclusive {
    AAudioStream *stream = nullptr;
    size_t frameBytes = 4;
    std::mutex lock;
    std::vector<uint8_t> ring;
    size_t ringStart = 0, ringSize = 0;
    uint64_t played = 0;
    bool playing = false;
    std::atomic<bool> lost{false};
};

aaudio_data_callback_result_t onData(AAudioStream *, void *user, void *audio, int32_t frames) {
    auto *e = static_cast<Exclusive *>(user);
    size_t want = static_cast<size_t>(frames) * e->frameBytes;
    size_t got = 0;
    auto *out = static_cast<uint8_t *>(audio);
    // the audio thread never waits: if kotlin holds the queue this instant, this one buffer is silence
    if (e->lock.try_lock()) {
        if (e->playing) {
            got = std::min(want, e->ringSize) / e->frameBytes * e->frameBytes;
            size_t first = std::min(got, e->ring.size() - e->ringStart);
            memcpy(out, e->ring.data() + e->ringStart, first);
            memcpy(out + first, e->ring.data(), got - first);
            e->ringStart = (e->ringStart + got) % e->ring.size();
            e->ringSize -= got;
            e->played += got / e->frameBytes;
        }
        e->lock.unlock();
    }
    memset(out + got, 0, want - got);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void onError(AAudioStream *, void *user, aaudio_result_t) {
    static_cast<Exclusive *>(user)->lost = true;
}

void throwState(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

Exclusive *exclusiveOf(jlong handle) { return reinterpret_cast<Exclusive *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveOpen(JNIEnv *env, jobject, jint rate, jint channels, jint bits, jint device) {
    AAudioApi &a = api();
    if (!a.ready) {
        throwState(env, "aaudio isn't on this phone");
        return 0;
    }
    aaudio_format_t format = bits == 16 ? AAUDIO_FORMAT_PCM_I16 : bits == 24 ? kI24 : kI32;
    auto *e = new Exclusive();
    e->frameBytes = static_cast<size_t>(channels) * (bits / 8);
    AAudioStreamBuilder *builder = nullptr;
    if (a.createBuilder(&builder) != AAUDIO_OK) {
        delete e;
        throwState(env, "aaudio wouldn't start");
        return 0;
    }
    if (device > 0 && a.setDeviceId) a.setDeviceId(builder, device);
    a.setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    a.setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    // exclusive streams only exist on the low latency path
    a.setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    a.setFormat(builder, format);
    a.setSampleRate(builder, rate);
    a.setChannelCount(builder, channels);
    if (a.setUsage) a.setUsage(builder, AAUDIO_USAGE_MEDIA);
    if (a.setContentType) a.setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    a.setDataCallback(builder, onData, e);
    if (a.setErrorCallback) a.setErrorCallback(builder, onError, e);
    aaudio_result_t opened = a.openStream(builder, &e->stream);
    a.deleteBuilder(builder);
    if (opened != AAUDIO_OK || !e->stream) {
        delete e;
        throwState(env, "the output wouldn't open");
        return 0;
    }
    bool exact = a.sharingMode(e->stream) == AAUDIO_SHARING_MODE_EXCLUSIVE && a.sampleRate(e->stream) == rate &&
                 a.channelCount(e->stream) == channels && a.format(e->stream) == format;
    // from android 14 the hardware's own format is readable: a stream converted on its way there isn't exact
    if (exact && a.hardwareSampleRate && a.hardwareFormat) {
        exact = a.hardwareSampleRate(e->stream) == rate && a.hardwareFormat(e->stream) == format;
    }
    if (!exact) {
        LOGI("asked for %d Hz %d-bit exclusive, android gave something else", rate, bits);
        a.close(e->stream);
        delete e;
        throwState(env, "android didn't grant the output exactly as asked");
        return 0;
    }
    // a second waits here, so the player can sleep through a slow background wake-up; pause and flush don't wait on it
    e->ring.resize(std::max<size_t>(static_cast<size_t>(rate), 4096) * e->frameBytes);
    if (a.start(e->stream) != AAUDIO_OK) {
        a.close(e->stream);
        delete e;
        throwState(env, "the output wouldn't start");
        return 0;
    }
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveDevice(JNIEnv *, jobject, jlong handle) {
    AAudioApi &a = api();
    return a.deviceId ? a.deviceId(exclusiveOf(handle)->stream) : 0;
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveCapacity(JNIEnv *, jobject, jlong handle) {
    Exclusive *e = exclusiveOf(handle);
    return static_cast<jint>(e->ring.size() / e->frameBytes);
}

JNIEXPORT jint JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveWrite(JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset, jint length) {
    Exclusive *e = exclusiveOf(handle);
    if (e->lost) {
        throwState(env, "the output went away");
        return 0;
    }
    auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
    if (!data) {
        throwState(env, "not a direct buffer");
        return 0;
    }
    std::lock_guard<std::mutex> guard(e->lock);
    size_t take = std::min(static_cast<size_t>(length), e->ring.size() - e->ringSize) / e->frameBytes * e->frameBytes;
    size_t end = (e->ringStart + e->ringSize) % e->ring.size();
    size_t first = std::min(take, e->ring.size() - end);
    memcpy(e->ring.data() + end, data + offset, first);
    memcpy(e->ring.data(), data + offset + first, take - first);
    e->ringSize += take;
    return static_cast<jint>(take);
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusivePlay(JNIEnv *env, jobject, jlong handle, jboolean playing) {
    Exclusive *e = exclusiveOf(handle);
    if (e->lost) {
        throwState(env, "the output went away");
        return;
    }
    // the stream itself keeps running: while paused it plays silence, so resuming has no start-up click
    std::lock_guard<std::mutex> guard(e->lock);
    e->playing = playing;
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveFlush(JNIEnv *, jobject, jlong handle) {
    Exclusive *e = exclusiveOf(handle);
    std::lock_guard<std::mutex> guard(e->lock);
    e->playing = false;
    e->ringStart = 0;
    e->ringSize = 0;
    e->played = 0;
}

JNIEXPORT jlong JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusivePlayed(JNIEnv *env, jobject, jlong handle) {
    Exclusive *e = exclusiveOf(handle);
    if (e->lost) {
        throwState(env, "the output went away");
        return 0;
    }
    std::lock_guard<std::mutex> guard(e->lock);
    return static_cast<jlong>(e->played);
}

JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_playback_audio_pass_PassNative_exclusiveClose(JNIEnv *, jobject, jlong handle) {
    Exclusive *e = exclusiveOf(handle);
    AAudioApi &a = api();
    a.stop(e->stream);
    a.close(e->stream);
    delete e;
}

}  // extern "C"
