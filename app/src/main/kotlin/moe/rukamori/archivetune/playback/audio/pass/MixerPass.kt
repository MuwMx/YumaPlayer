/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import moe.rukamori.archivetune.playback.audio.AudioOutputs
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class MixerPass private constructor(
    private val audio: AudioManager,
    private val device: AudioDeviceInfo,
    private val mixer: AudioMixerAttributes,
    private val track: AudioTrack,
    override val bits: Int,
) : PassTransport {

    override val sampleRate = mixer.format.sampleRate
    override val channels = track.channelCount
    override val deviceName = device.productName?.toString()?.removePrefix("USB-Audio - ")?.trim().orEmpty().ifEmpty { "USB DAC" }
    override val capacityFrames = track.bufferSizeInFrames.toLong()

    @Volatile private var failure: String? = null
    private var nextCheck = 0L
    private var lastHead = 0L
    private var wraps = 0L

    private val routing = AudioRouting.OnRoutingChangedListener { router ->
        val routed = (router as? AudioTrack)?.routedDevice
        if (routed != null && routed.id != device.id) failure = "the dac stopped being the output"
    }
    private val mixerChanged = AudioManager.OnPreferredMixerAttributesChangedListener { _, changed, value ->
        if (changed.id == device.id && value != mixer) failure = "something else changed the dac's mixer"
    }

    init {
        track.addOnRoutingChangedListener(routing, Handler(Looper.getMainLooper()))
        audio.addOnPreferredMixerAttributesChangedListener(Runnable::run, mixerChanged)
    }

    override fun write(buffer: ByteBuffer): Int {
        check()
        val written = track.write(buffer, buffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
        if (written < 0) throw PassFailure("the dac refused the write ($written)")
        return written
    }

    override fun play() {
        check()
        track.play()
        nextCheck = SystemClock.elapsedRealtime() + FirstCheckMs
    }

    override fun pause() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    override fun flush() {
        pause()
        track.flush()
        lastHead = 0
        wraps = 0
    }

    override fun playedFrames(): Long {
        val head = track.playbackHeadPosition.toLong() and 0xffffffffL
        if (head < lastHead) {
            if (lastHead - head > (1L shl 31)) {
                wraps += 1L shl 32
            } else {
                return wraps + lastHead
            }
        }
        lastHead = head
        return wraps + head
    }

    override fun release() {
        runCatching { track.removeOnRoutingChangedListener(routing) }
        runCatching { audio.removeOnPreferredMixerAttributesChangedListener(mixerChanged) }
        runCatching { track.release() }
        runCatching {
            if (audio.getPreferredMixerAttributes(AudioOutputs.MediaAttributes, device) == mixer) {
                audio.clearPreferredMixerAttributes(AudioOutputs.MediaAttributes, device)
            }
        }
    }

    private fun check() {
        failure?.let { throw PassFailure(it) }
        val now = SystemClock.elapsedRealtime()
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING || now < nextCheck) return
        nextCheck = now + CheckMs
        val routed = track.routedDevice
        if ((routed != null && routed.id != device.id) || audio.getPreferredMixerAttributes(AudioOutputs.MediaAttributes, device) != mixer) {
            failure = "the dac isn't bit-perfect any more"
            throw PassFailure(failure!!)
        }
    }

    companion object {
        private const val FirstCheckMs = 600L
        private const val CheckMs = 250L

        fun pick(audio: AudioManager, device: AudioDeviceInfo, rate: Int, channels: Int, sourceBits: Int): AudioMixerAttributes? {
            val mask = channelMask(channels)
            return runCatching { audio.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
                .filter {
                    it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                        it.format.sampleRate == rate && it.format.channelMask == mask
                }
                .mapNotNull { attr -> integerBits(attr.format.encoding)?.takeIf { it >= sourceBits }?.let { attr to it } }
                .minByOrNull { it.second }?.first
        }

        fun open(audio: AudioManager, device: AudioDeviceInfo, rate: Int, channels: Int, sourceBits: Int): MixerPass {
            val attr = pick(audio, device, rate, channels, sourceBits)
                ?: throw PassFailure("the dac has no bit-perfect $rate Hz ${sourceBits}-bit")
            if (!audio.setPreferredMixerAttributes(AudioOutputs.MediaAttributes, device, attr)) {
                throw PassFailure("android wouldn't set the dac's mixer")
            }
            val bits = integerBits(attr.format.encoding)!!
            var track: AudioTrack? = null
            try {
                val frameBytes = channels * bits / 8
                val minimum = AudioTrack.getMinBufferSize(rate, attr.format.channelMask, attr.format.encoding)
                if (minimum <= 0) throw PassFailure("no track at $rate Hz ${bits}-bit")
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioOutputs.MediaAttributes)
                    .setAudioFormat(attr.format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minimum * 2, rate / 2 * frameBytes))
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(device) ||
                    track.sampleRate != rate || track.audioFormat != attr.format.encoding || track.channelCount != channels
                ) {
                    throw PassFailure("the track didn't open as asked")
                }
                track.setVolume(1f)
                return MixerPass(audio, device, attr, track, bits)
            } catch (error: Exception) {
                runCatching { track?.release() }
                runCatching { audio.clearPreferredMixerAttributes(AudioOutputs.MediaAttributes, device) }
                throw error as? PassFailure ?: PassFailure("the track didn't open", error)
            }
        }

        private fun channelMask(channels: Int) = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        private fun integerBits(encoding: Int): Int? = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> null
        }
    }
}
