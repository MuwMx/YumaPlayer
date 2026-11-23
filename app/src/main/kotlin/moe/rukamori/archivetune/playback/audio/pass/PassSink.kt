/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
class PassSink(private val pass: AudioPass) : AudioSink {

    private var listener: AudioSink.Listener? = null
    private var attributes = AudioAttributes.DEFAULT
    private var skipSilence = false
    private var playing = false
    private var muted = false

    private var input: Format? = null
    private var source: Format? = null
    private var sourceBits = 16
    private var transport: PassTransport? = null
    private var transportPlaying = false

    private var startUs = C.TIME_UNSET
    private var writtenFrames = 0L
    private var queuedFrames = 0L
    private var pending: ByteBuffer? = null
    private var pendingSource: ByteBuffer? = null
    private var draining = false
    private var exact = true

    fun canTake(format: Format, source: Format?): Boolean = pass.canTake(format, source, this)

    fun configure(format: Format, source: Format?) {
        val bits = PassPcm.sourceBits(format.pcmEncoding, source?.pcmEncoding)
        val open = transport
        if (open != null && (open.sampleRate != format.sampleRate || open.channels != format.channelCount || open.bits < bits)) {
            closeTransport()
        }
        input = format
        this.source = source
        sourceBits = bits
        pending = null
        pendingSource = null
        draining = false
        transport?.let { active ->
            exact = true
            pass.playing(active, bits, source, exact)
        }
    }

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) = configure(inputFormat, null)

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val format = input ?: return true
        draining = false
        val output = transport ?: openTransport(format)
        if (startUs == C.TIME_UNSET) {
            startUs = presentationTimeUs
        } else if (pending == null) {
            val expected = startUs + framesToUs(queuedFrames, format.sampleRate)
            if (abs(presentationTimeUs - expected) > 200_000) {
                startUs += presentationTimeUs - expected
                listener?.onPositionDiscontinuity()
            }
        }
        if (pending == null || pendingSource !== buffer) {
            pending = convert(buffer, format, output)
            pendingSource = buffer
        }
        val out = pending!!
        val frameBytes = output.channels * output.bits / 8
        writtenFrames += output.write(out) / frameBytes
        if (playing && !transportPlaying) startPlaying(output)
        if (out.hasRemaining()) return false
        buffer.position(buffer.limit())
        queuedFrames += out.limit() / frameBytes
        pending = null
        pendingSource = null
        return true
    }

    private fun openTransport(format: Format): PassTransport {
        val opened = pass.open(format, source, this)
        transport = opened
        transportPlaying = false
        exact = true
        pass.playing(opened, sourceBits, source, exact)
        return opened
    }

    private fun startPlaying(output: PassTransport) {
        output.play()
        transportPlaying = true
    }

    private var scratch: ByteBuffer? = null

    private fun convert(buffer: ByteBuffer, format: Format, output: PassTransport): ByteBuffer {
        val input = buffer.duplicate()
        val frames = input.remaining() / (PassPcm.bytesPerSample(format.pcmEncoding) * format.channelCount)
        val size = frames * output.channels * output.bits / 8
        val out = scratch?.takeIf { it.capacity() >= size } ?: ByteBuffer.allocateDirect(maxOf(size, 16_384)).order(ByteOrder.LITTLE_ENDIAN).also { scratch = it }
        out.clear()
        val wasExact = PassPcm.convert(input, format.pcmEncoding, format.channelCount, sourceBits, output.bits, out, muted)
        out.flip()
        if (!wasExact && exact) {
            exact = false
            pass.playing(output, sourceBits, source, false)
        }
        return out
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val output = transport ?: return AudioSink.CURRENT_POSITION_NOT_SET
        val format = input ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (startUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val played = runCatching { output.playedFrames() }.getOrDefault(writtenFrames)
        return startUs + framesToUs(minOf(played, writtenFrames), format.sampleRate)
    }

    override fun playToEndOfStream() {
        draining = true
        val output = transport ?: return
        pending?.let { out ->
            val frameBytes = output.channels * output.bits / 8
            writtenFrames += output.write(out) / frameBytes
            if (!out.hasRemaining()) {
                pendingSource?.let { it.position(it.limit()) }
                queuedFrames += out.limit() / frameBytes
                pending = null
                pendingSource = null
            }
        }
        if (playing && !transportPlaying) startPlaying(output)
    }

    override fun isEnded(): Boolean {
        if (!draining || pending != null) return false
        val output = transport ?: return true
        return runCatching { output.playedFrames() >= writtenFrames }.getOrDefault(true)
    }

    override fun hasPendingData(): Boolean {
        val output = transport ?: return false
        return runCatching { output.playedFrames() < writtenFrames }.getOrDefault(false)
    }

    override fun play() {
        playing = true
        transport?.let { if (writtenFrames > 0 && !transportPlaying) startPlaying(it) }
    }

    override fun pause() {
        playing = false
        if (transportPlaying) runCatching { transport?.pause() }
        transportPlaying = false
    }

    override fun flush() {
        runCatching { transport?.flush() }
        transportPlaying = false
        startUs = C.TIME_UNSET
        writtenFrames = 0
        queuedFrames = 0
        pending = null
        pendingSource = null
        draining = false
    }

    override fun reset() {
        flush()
        closeTransport()
        input = null
        source = null
    }

    override fun release() = reset()

    private fun closeTransport() {
        val open = transport ?: return
        transport = null
        transportPlaying = false
        startUs = C.TIME_UNSET
        writtenFrames = 0
        queuedFrames = 0
        pending = null
        pendingSource = null
        draining = false
        runCatching { open.release() }
        pass.release(this)
    }

    override fun setVolume(volume: Float) {
        muted = volume <= 0.0001f
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

    override fun getFormatSupport(format: Format): Int =
        if (format.pcmEncoding in PassPcm.Encodings) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun handleDiscontinuity() = Unit
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipSilence = skipSilenceEnabled
    }
    override fun getSkipSilenceEnabled(): Boolean = skipSilence
    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        attributes = audioAttributes
    }
    override fun getAudioAttributes(): AudioAttributes = attributes
    override fun setAudioSessionId(audioSessionId: Int) = Unit
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
    override fun getAudioTrackBufferSizeUs(): Long =
        transport?.let { t -> input?.let { framesToUs(t.capacityFrames, it.sampleRate) } } ?: C.TIME_UNSET
    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit

    private fun framesToUs(frames: Long, rate: Int): Long = frames * 1_000_000L / rate
}
