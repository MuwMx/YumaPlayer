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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

@UnstableApi
class ReverbAudioProcessor(private val config: AudioFxConfigProvider) : AudioProcessor {

    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var sampleRate = 0
    private var channels = 0

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var combsL: Array<Comb> = emptyArray()
    private var combsR: Array<Comb> = emptyArray()
    private var allpassL: Array<Allpass> = emptyArray()
    private var allpassR: Array<Allpass> = emptyArray()
    private var preDelay = FloatArray(1)
    private var preDelayIndex = 0
    private var lowCutState = 0f
    private var lowCutInput = 0f

    private var amount = 0f
    private var wet1 = 0f
    private var wet2 = 0f
    private var dryGain = 1f
    private var mix = 0f
    private var roomEmpty = true

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (channels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val params = config.reverb
        val target = if (params.enabled) 1f else 0f
        val out = replaceOutputBuffer(bytes)
        if (target == 0f && amount == 0f) {
            if (!roomEmpty) clearRoom()
            out.put(inputBuffer)
            out.flip()
            return
        }
        roomEmpty = false
        val frames = bytes / (2 * channels)

        val feedback = params.roomSize.coerceIn(0f, 1f) * ScaleRoom + OffsetRoom
        val damp = params.damping.coerceIn(0f, 1f) * ScaleDamp
        for (c in combsL) c.set(feedback, damp)
        for (c in combsR) c.set(feedback, damp)
        val delayFrames = (params.preDelayMs.coerceIn(0f, MaxPreDelayMs) / 1000f * sampleRate).roundToInt()
            .coerceIn(0, preDelay.size - 1)
        val lowCut = exp(-2.0 * PI * params.lowCutHz.coerceIn(10f, 2000f) / sampleRate).toFloat()

        val width = params.width.coerceIn(0f, 1f)
        val wetLevel = params.wet.coerceIn(0f, 1f) * ScaleWet
        val targetWet1 = wetLevel * (width / 2f + 0.5f)
        val targetWet2 = wetLevel * ((1f - width) / 2f)
        val targetDry = params.dry.coerceIn(0f, 1f)
        val targetMix = params.mix.coerceIn(0f, 1f)

        val step = 1f / frames
        val toggleStep = 1f / (sampleRate * ToggleSeconds)
        val dWet1 = (targetWet1 - wet1) * step
        val dWet2 = (targetWet2 - wet2) * step
        val dDry = (targetDry - dryGain) * step
        val dMix = (targetMix - mix) * step

        val scale = 1f / 32768f
        for (f in 0 until frames) {
            val inL = inputBuffer.getShort() * scale
            val inR = if (channels == 2) inputBuffer.getShort() * scale else inL

            val mono = (inL + inR) * 0.5f
            preDelay[preDelayIndex] = mono
            val delayedIndex = if (preDelayIndex >= delayFrames) preDelayIndex - delayFrames
            else preDelayIndex - delayFrames + preDelay.size
            val delayed = preDelay[delayedIndex]
            preDelayIndex = if (preDelayIndex + 1 == preDelay.size) 0 else preDelayIndex + 1

            lowCutState = lowCut * (lowCutState + delayed - lowCutInput)
            lowCutInput = delayed
            val wetInput = lowCutState * FixedGain

            var outL = 0f
            var outR = 0f
            for (c in combsL) outL += c.process(wetInput)
            for (c in combsR) outR += c.process(wetInput)
            for (a in allpassL) outL = a.process(outL)
            for (a in allpassR) outR = a.process(outR)

            amount = if (target > amount) minOf(target, amount + toggleStep) else maxOf(target, amount - toggleStep)
            wet1 += dWet1
            wet2 += dWet2
            dryGain += dDry
            mix += dMix

            val revL = outL * wet1 + outR * wet2
            val revR = outR * wet1 + outL * wet2
            val blendL = inL * (1f - mix) * dryGain + revL * mix
            val blendR = inR * (1f - mix) * dryGain + revR * mix

            out.putShort(toPcm(inL + (blendL - inL) * amount))
            if (channels == 2) out.putShort(toPcm(inR + (blendR - inR) * amount))
        }
        out.flip()
    }

    private fun build(sampleRate: Int) {
        val scale = sampleRate / 44100f
        combsL = Array(CombTuning.size) { Comb((CombTuning[it] * scale).roundToInt()) }
        combsR = Array(CombTuning.size) { Comb(((CombTuning[it] + StereoSpread) * scale).roundToInt()) }
        allpassL = Array(AllpassTuning.size) { Allpass((AllpassTuning[it] * scale).roundToInt()) }
        allpassR = Array(AllpassTuning.size) { Allpass(((AllpassTuning[it] + StereoSpread) * scale).roundToInt()) }
        preDelay = FloatArray((MaxPreDelayMs / 1000f * sampleRate).roundToInt() + 1)
        preDelayIndex = 0
        lowCutState = 0f
        lowCutInput = 0f
        clearRoom()
    }

    private fun clearRoom() {
        for (c in combsL) c.clear()
        for (c in combsR) c.clear()
        for (a in allpassL) a.clear()
        for (a in allpassR) a.clear()
        preDelay.fill(0f)
        preDelayIndex = 0
        lowCutState = 0f
        lowCutInput = 0f
        roomEmpty = true
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
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        val format = pendingFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET &&
            (format.sampleRate != sampleRate || format.channelCount != channels)
        ) {
            sampleRate = format.sampleRate
            channels = format.channelCount
            build(sampleRate)
        } else {
            clearRoom()
        }
        val params = config.reverb
        amount = if (params.enabled) 1f else 0f
        val width = params.width.coerceIn(0f, 1f)
        val wetLevel = params.wet.coerceIn(0f, 1f) * ScaleWet
        wet1 = wetLevel * (width / 2f + 0.5f)
        wet2 = wetLevel * ((1f - width) / 2f)
        dryGain = params.dry.coerceIn(0f, 1f)
        mix = params.mix.coerceIn(0f, 1f)
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channels = 0
    }

    private class Comb(size: Int) {
        private val line = FloatArray(size.coerceAtLeast(1))
        private var index = 0
        private var store = 0f
        private var feedback = 0.84f
        private var damp1 = 0.2f
        private var damp2 = 0.8f

        fun set(feedback: Float, damp: Float) {
            this.feedback = feedback
            damp1 = damp
            damp2 = 1f - damp
        }

        fun process(input: Float): Float {
            val output = line[index]
            store = output * damp2 + store * damp1
            line[index] = input + store * feedback
            index = if (index + 1 == line.size) 0 else index + 1
            return output
        }

        fun clear() {
            line.fill(0f)
            store = 0f
        }
    }

    private class Allpass(size: Int) {
        private val line = FloatArray(size.coerceAtLeast(1))
        private var index = 0

        fun process(input: Float): Float {
            val buffered = line[index]
            line[index] = input + buffered * 0.5f
            index = if (index + 1 == line.size) 0 else index + 1
            return buffered - input
        }

        fun clear() = line.fill(0f)
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private val CombTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val AllpassTuning = intArrayOf(556, 441, 341, 225)
        private const val StereoSpread = 23
        private const val FixedGain = 0.015f
        private const val ScaleWet = 3f
        private const val ScaleDamp = 0.4f
        private const val ScaleRoom = 0.28f
        private const val OffsetRoom = 0.7f
        private const val MaxPreDelayMs = 100f
        private const val ToggleSeconds = 0.15f
    }
}
