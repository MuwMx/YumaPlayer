/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0

    @Volatile private var design: ParametricEQ? = null
    @Volatile private var designVersion = 0
    private var builtVersion = -1

    private var bank: FilterBank? = null
    private var fadingFrom: FilterBank? = null
    private var fading = false
    private var fadePos = 0
    private var fadeLength = 0
    private var snapNextDesign = true

    private var work = DoubleArray(0)
    private var fadeWork = DoubleArray(0)
    private var pcm = ShortArray(0)
    private val limiter = PeakLimiter()

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var tailPending = false
    private val tapId = System.identityHashCode(this)

    fun applyProfile(parametricEQ: ParametricEQ) {
        design = parametricEQ
        designVersion++
    }

    fun disable() {
        design = null
        designVersion++
    }

    fun isEnabled(): Boolean = design != null

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..8) {
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) {
            if (tailPending) drainTail()
            return
        }
        if (channels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        syncDesign()
        if (bank == null && !fading && limiter.isResting && !AudioTap.wanted) {
            limiter.delayPcm(inputBuffer, replaceOutputBuffer(bytes))
            outputBuffer.flip()
            return
        }
        val frames = bytes / (2 * channels)
        val count = frames * channels
        ensureWork(count)
        val scale = 1.0 / 32768.0
        inputBuffer.asShortBuffer().get(pcm, 0, count)
        inputBuffer.position(inputBuffer.position() + count * 2)
        for (i in 0 until count) work[i] = pcm[i] * scale
        render(frames, replaceOutputBuffer(bytes))
    }

    private fun render(frames: Int, out: ByteBuffer) {
        syncDesign()
        val count = frames * channels
        if (fading) {
            System.arraycopy(work, 0, fadeWork, 0, count)
            fadingFrom?.process(fadeWork, frames)
        }
        bank?.process(work, frames)
        if (fading) mixFade(frames)
        limiter.process(work, frames)

        if (AudioTap.wanted) AudioTap.write(tapId, work, frames, channels, sampleRate)

        for (i in 0 until count) pcm[i] = toPcm(work[i])
        out.asShortBuffer().put(pcm, 0, count)
        out.position(out.position() + count * 2)
        out.flip()
    }

    private fun drainTail() {
        val frames = limiter.latencyFrames
        val bytes = frames * channels * 2
        val out = replaceOutputBuffer(bytes)
        val count = frames * channels
        ensureWork(count)
        work.fill(0.0)
        limiter.process(work, frames)
        for (i in 0 until count) pcm[i] = toPcm(work[i])
        out.asShortBuffer().put(pcm, 0, count)
        out.position(out.position() + count * 2)
        out.flip()
        tailPending = false
    }

    private fun mixFade(frames: Int) {
        val inv = 1.0 / fadeLength
        var i = 0
        for (f in 0 until frames) {
            val t = ((fadePos + f) * inv).coerceIn(0.0, 1.0)
            val fromGain = 1.0 - t
            val toGain = t
            for (c in 0 until channels) {
                work[i] = fadeWork[i] * fromGain + work[i] * toGain
                i++
            }
        }
        fadePos += frames
        if (fadePos >= fadeLength) {
            fading = false
            fadingFrom = null
        }
    }

    private fun syncDesign() {
        if (builtVersion == designVersion) return
        builtVersion = designVersion
        val next = design?.let { FilterBank.build(it, sampleRate, channels) }
        if (snapNextDesign || sampleRate <= 0) {
            bank = next
            fading = false
            fadingFrom = null
            snapNextDesign = false
            return
        }
        next?.adoptState(bank)
        fadingFrom = bank
        bank = next
        fading = fadingFrom != null || bank != null
        fadePos = 0
        fadeLength = (sampleRate * FadeSeconds).toInt().coerceAtLeast(1)
    }

    private fun ensureWork(count: Int) {
        if (work.size < count) {
            work = DoubleArray(count)
            fadeWork = DoubleArray(count)
            pcm = ShortArray(count)
        }
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
        tailPending = limiter.latencyFrames > 0
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER && !tailPending

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        tailPending = false
        val format = pendingFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET &&
            (format.sampleRate != sampleRate || format.channelCount != channels)
        ) {
            sampleRate = format.sampleRate
            channels = format.channelCount
            bank = null
            builtVersion = -1
            limiter.configure(sampleRate, channels)
        }
        bank?.reset()
        limiter.reset()
        fading = false
        fadingFrom = null
        if (bank == null) snapNextDesign = true
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channels = 0
        bank = null
        builtVersion = -1
        snapNextDesign = true
    }

    private class FilterBank(
        private val count: Int,
        private val channels: Int,
        private val coefficients: DoubleArray,
        private val gain: Double,
    ) {
        private val state = DoubleArray(count * channels * 4)

        fun process(buffer: DoubleArray, frames: Int) {
            for (k in 0 until count) {
                val o = k * Biquad.Stride
                val b0 = coefficients[o]
                val b1 = coefficients[o + 1]
                val b2 = coefficients[o + 2]
                val a1 = coefficients[o + 3]
                val a2 = coefficients[o + 4]
                for (c in 0 until channels) {
                    val s = (k * channels + c) * 4
                    var x1 = state[s]
                    var x2 = state[s + 1]
                    var y1 = state[s + 2]
                    var y2 = state[s + 3]
                    var i = c
                    for (f in 0 until frames) {
                        val x = buffer[i]
                        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                        x2 = x1
                        x1 = x
                        y2 = y1
                        y1 = y
                        buffer[i] = y
                        i += channels
                    }
                    state[s] = x1
                    state[s + 1] = x2
                    state[s + 2] = y1
                    state[s + 3] = y2
                }
            }
            if (gain != 1.0) {
                for (i in 0 until frames * channels) buffer[i] *= gain
            }
        }

        fun adoptState(previous: FilterBank?) {
            if (previous == null || previous.channels != channels) return
            val shared = minOf(count, previous.count) * channels * 4
            System.arraycopy(previous.state, 0, state, 0, shared)
        }

        fun reset() = state.fill(0.0)

        companion object {
            fun build(eq: ParametricEQ, sampleRate: Int, channels: Int): FilterBank? {
                if (sampleRate <= 0 || channels <= 0) return null
                val bands = eq.bands.filter { Biquad.isAudible(it) && it.frequency < sampleRate / 2.0 }
                val gain = 10.0.pow(eq.preamp / 20.0)
                if (bands.isEmpty() && gain == 1.0) return null
                val coefficients = DoubleArray(bands.size * Biquad.Stride)
                bands.forEachIndexed { index, band ->
                    Biquad.coefficients(band, sampleRate, coefficients, index * Biquad.Stride)
                }
                return FilterBank(bands.size, channels, coefficients, gain)
            }
        }
    }

    companion object {
        private const val FadeSeconds = 0.03
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}

internal fun toPcm(sample: Double): Short =
    Math.round(sample * 32768.0).coerceIn(-32768L, 32767L).toShort()

internal fun toPcm(sample: Float): Short =
    Math.round(sample * 32768f).coerceIn(-32768, 32767).toShort()

