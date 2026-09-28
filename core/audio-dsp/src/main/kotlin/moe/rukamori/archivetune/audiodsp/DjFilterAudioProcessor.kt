package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class DjFilterAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var lowPassCutoffHz: Double = BYPASS_CUTOFF_HZ

    @Volatile
    var bassGainDb: Double = 0.0

    @Volatile
    var gain: Double = 1.0

    val isBypassed: Boolean
        get() = lowPassCutoffHz >= BYPASS_CUTOFF_HZ &&
            abs(bassGainDb) < 0.01 &&
            abs(gain - 1.0) < 1e-4

    fun clearAutomation() {
        lowPassCutoffHz = BYPASS_CUTOFF_HZ
        bassGainDb = 0.0
        gain = 1.0
    }

    private var channelCount = 0
    private var sampleRate = 0
    private var lowPassState = Array(0) { DoubleArray(4) }
    private var shelfState = Array(0) { DoubleArray(4) }
    private val lowPassCoeff = DoubleArray(5)
    private val shelfCoeff = DoubleArray(5)
    private var cachedCutoffHz = -1.0
    private var cachedBassDb = Double.NaN

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != STEREO_CHANNELS) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        lowPassState = Array(channelCount) { DoubleArray(4) }
        shelfState = Array(channelCount) { DoubleArray(4) }
        cachedCutoffHz = -1.0
        cachedBassDb = Double.NaN
        return inputAudioFormat
    }

    override fun isActive(): Boolean = sampleRate != 0 && channelCount != 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) return
        val frames = inputBuffer.remaining() / (BYTES_PER_SAMPLE * channelCount)
        if (frames == 0) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val cutoff = lowPassCutoffHz
        val bassDb = bassGainDb
        val level = gain
        if (cutoff < BYPASS_CUTOFF_HZ) refreshLowPass(cutoff)
        if (abs(bassDb) >= 0.01) refreshLowShelf(bassDb)
        val filtering = cutoff < BYPASS_CUTOFF_HZ
        val shelving = abs(bassDb) >= 0.01
        val applyGain = abs(level - 1.0) >= 1e-4
        for (frame in 0 until frames) {
            for (channel in 0 until channelCount) {
                var sample = inputBuffer.short.toDouble() / 32768.0
                if (filtering) sample = runBiquad(lowPassCoeff, lowPassState[channel], sample)
                if (shelving) sample = runBiquad(shelfCoeff, shelfState[channel], sample)
                if (applyGain) sample *= level
                output.putShort((sample.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        resetDelayLines()
        cachedCutoffHz = -1.0
        cachedBassDb = Double.NaN
    }

    override fun onReset() {
        clearAutomation()
        resetDelayLines()
        cachedCutoffHz = -1.0
        cachedBassDb = Double.NaN
        channelCount = 0
        sampleRate = 0
    }

    private fun resetDelayLines() {
        for (state in lowPassState) state.fill(0.0)
        for (state in shelfState) state.fill(0.0)
    }

    private fun runBiquad(coeff: DoubleArray, state: DoubleArray, input: Double): Double {
        val output = coeff[0] * input + coeff[1] * state[0] + coeff[2] * state[1] -
            coeff[3] * state[2] - coeff[4] * state[3]
        state[1] = state[0]
        state[0] = input
        state[3] = state[2]
        state[2] = output
        return output
    }

    private fun refreshLowPass(hz: Double) {
        if (abs(hz - cachedCutoffHz) < 1.0) return
        cachedCutoffHz = hz
        val corner = hz.coerceIn(30.0, sampleRate * 0.45) / sampleRate
        val w0 = 2.0 * PI * corner
        val alpha = sin(w0) / (2.0 * BUTTERWORTH_Q)
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha
        lowPassCoeff[0] = ((1.0 - cosW0) / 2.0) / a0
        lowPassCoeff[1] = (1.0 - cosW0) / a0
        lowPassCoeff[2] = lowPassCoeff[0]
        lowPassCoeff[3] = (-2.0 * cosW0) / a0
        lowPassCoeff[4] = (1.0 - alpha) / a0
    }

    private fun refreshLowShelf(db: Double) {
        if (abs(db - cachedBassDb) < 0.05) return
        cachedBassDb = db
        val amplitude = 10.0.pow(db / 20.0).coerceIn(0.001, 4.0)
        val shelfA = sqrt(amplitude)
        val w0 = 2.0 * PI * BASS_CROSSOVER_HZ / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt((shelfA + 1.0 / shelfA) * (1.0 / SHELF_SLOPE - 1.0) + 2.0)
        val blend = 2.0 * sqrt(shelfA) * alpha
        val a0 = (shelfA + 1.0) + (shelfA - 1.0) * cosW0 + blend
        shelfCoeff[0] = (shelfA * ((shelfA + 1.0) - (shelfA - 1.0) * cosW0 + blend)) / a0
        shelfCoeff[1] = (2.0 * shelfA * ((shelfA - 1.0) - (shelfA + 1.0) * cosW0)) / a0
        shelfCoeff[2] = (shelfA * ((shelfA + 1.0) - (shelfA - 1.0) * cosW0 - blend)) / a0
        shelfCoeff[3] = (-2.0 * ((shelfA - 1.0) + (shelfA + 1.0) * cosW0)) / a0
        shelfCoeff[4] = ((shelfA + 1.0) + (shelfA - 1.0) * cosW0 - blend) / a0
    }

    companion object {
        const val BYPASS_CUTOFF_HZ = 20_000.0
        const val SWEEP_TARGET_HZ = 400.0
        const val FULL_CUT_DB = -24.0
        const val BASS_CROSSOVER_HZ = 200.0
        private const val STEREO_CHANNELS = 2
        private const val BYTES_PER_SAMPLE = 2
        private const val BUTTERWORTH_Q = 0.70710678
        private const val SHELF_SLOPE = 1.0
    }
}
