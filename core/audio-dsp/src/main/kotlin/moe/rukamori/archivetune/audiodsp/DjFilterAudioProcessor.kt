package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class DjFilterAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var lowPassCutoffHz: Double = BYPASS_CUTOFF_HZ

    @Volatile
    var highPassHz: Double = BYPASS_HIGH_PASS_HZ

    @Volatile
    var bassGainDb: Double = 0.0

    @Volatile
    var gain: Double = 1.0

    val isBypassed: Boolean
        get() = lowPassCutoffHz >= BYPASS_CUTOFF_HZ &&
            highPassHz <= BYPASS_HIGH_PASS_HZ &&
            abs(bassGainDb) < 0.01 &&
            abs(gain - 1.0) < 1e-4

    fun clearAutomation() {
        lowPassCutoffHz = BYPASS_CUTOFF_HZ
        highPassHz = BYPASS_HIGH_PASS_HZ
        bassGainDb = 0.0
        gain = 1.0
    }

    private var channelCount = 0
    private var sampleRate = 0
    private var encoding = C.ENCODING_INVALID
    private var lowPassState = Array(0) { DoubleArray(4) }
    private var lowPassStateAlt = Array(0) { DoubleArray(4) }
    private var highPassState = Array(0) { DoubleArray(4) }
    private var shelfState = Array(0) { DoubleArray(4) }
    private val lowPassCoeff = DoubleArray(5)
    private val lowPassCoeffAlt = DoubleArray(5)
    private val highPassCoeff = DoubleArray(5)
    private val shelfCoeff = DoubleArray(5)
    private var cachedCutoffHz = -1.0
    private var cachedHighPassHz = -1.0
    private var cachedBassDb = Double.NaN
    private var glideCounter = 0
    private var smoothedCutoffHz = BYPASS_CUTOFF_HZ
    private var smoothedHighPassHz = BYPASS_HIGH_PASS_HZ
    private var smoothedBassDb = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            Timber.tag("DjFilter").d("bypass: unsupported encoding=${inputAudioFormat.encoding}")
            channelCount = 0
            sampleRate = 0
            encoding = C.ENCODING_INVALID
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount < 1) {
            Timber.tag("DjFilter").d("bypass: unsupported channels=${inputAudioFormat.channelCount}")
            channelCount = 0
            sampleRate = 0
            encoding = C.ENCODING_INVALID
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        encoding = inputAudioFormat.encoding
        lowPassState = Array(channelCount) { DoubleArray(4) }
        lowPassStateAlt = Array(channelCount) { DoubleArray(4) }
        highPassState = Array(channelCount) { DoubleArray(4) }
        shelfState = Array(channelCount) { DoubleArray(4) }
        resetCoefficientTracking()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = sampleRate != 0 && channelCount != 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            if (inputBuffer.hasRemaining()) {
                val output = replaceOutputBuffer(inputBuffer.remaining())
                output.put(inputBuffer)
                output.flip()
            }
            return
        }
        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) BYTES_PER_FLOAT_SAMPLE else BYTES_PER_SAMPLE
        val frames = inputBuffer.remaining() / (bytesPerSample * channelCount)
        if (frames == 0) return
        val output = replaceOutputBuffer(frames * bytesPerSample * channelCount)
        glideCoefficients(frames)
        val cutoff = smoothedCutoffHz
        val highPass = smoothedHighPassHz
        val bassDb = smoothedBassDb
        val level = gain
        val filtering = cutoff < BYPASS_CUTOFF_HZ
        val highPassing = highPass > MIN_ACTIVE_HIGH_PASS_HZ
        val shelving = abs(bassDb) >= 0.01
        val applyGain = abs(level - 1.0) >= 1e-4
        if (filtering) refreshLowPass(cutoff)
        if (highPassing) refreshHighPass(highPass)
        if (shelving) refreshLowShelf(bassDb)
        for (frame in 0 until frames) {
            for (channel in 0 until channelCount) {
                if (isFloat) {
                    var sample = inputBuffer.float.toDouble()
                    if (filtering) {
                        sample = runBiquad(lowPassCoeffAlt, lowPassStateAlt[channel], sample)
                        sample = runBiquad(lowPassCoeff, lowPassState[channel], sample)
                    }
                    if (highPassing) sample = runBiquad(highPassCoeff, highPassState[channel], sample)
                    if (shelving) sample = runBiquad(shelfCoeff, shelfState[channel], sample)
                    if (applyGain) sample *= level
                    output.putFloat(sample.coerceIn(-1.0, 1.0).toFloat())
                } else {
                    var sample = inputBuffer.short.toDouble() / 32768.0
                    if (filtering) {
                        sample = runBiquad(lowPassCoeffAlt, lowPassStateAlt[channel], sample)
                        sample = runBiquad(lowPassCoeff, lowPassState[channel], sample)
                    }
                    if (highPassing) sample = runBiquad(highPassCoeff, highPassState[channel], sample)
                    if (shelving) sample = runBiquad(shelfCoeff, shelfState[channel], sample)
                    if (applyGain) sample *= level
                    output.putShort((sample.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        resetDelayLines()
        resetCoefficientTracking()
    }

    override fun onReset() {
        clearAutomation()
        resetDelayLines()
        resetCoefficientTracking()
        channelCount = 0
        sampleRate = 0
        encoding = C.ENCODING_INVALID
    }

    private fun resetCoefficientTracking() {
        cachedCutoffHz = -1.0
        cachedHighPassHz = -1.0
        cachedBassDb = Double.NaN
        glideCounter = 0
        smoothedCutoffHz = lowPassCutoffHz
        smoothedHighPassHz = highPassHz
        smoothedBassDb = bassGainDb
    }

    private fun resetDelayLines() {
        for (state in lowPassState) state.fill(0.0)
        for (state in lowPassStateAlt) state.fill(0.0)
        for (state in highPassState) state.fill(0.0)
        for (state in shelfState) state.fill(0.0)
    }

    private fun glideCoefficients(frames: Int) {
        glideCounter += frames
        while (glideCounter >= GLIDE_FRAMES) {
            glideCounter -= GLIDE_FRAMES
            smoothedCutoffHz = if (lowPassCutoffHz >= BYPASS_CUTOFF_HZ) {
                BYPASS_CUTOFF_HZ
            } else {
                glideLog(smoothedCutoffHz, lowPassCutoffHz)
            }
            smoothedHighPassHz = if (highPassHz <= MIN_ACTIVE_HIGH_PASS_HZ) {
                BYPASS_HIGH_PASS_HZ
            } else {
                glideLog(smoothedHighPassHz, highPassHz)
            }
            smoothedBassDb = if (abs(bassGainDb) < 0.01) {
                0.0
            } else {
                smoothedBassDb + (bassGainDb - smoothedBassDb) * GLIDE_DB_COEFFICIENT
            }
        }
    }

    private fun glideLog(from: Double, to: Double): Double {
        val startLn = ln(from.coerceAtLeast(MIN_GLIDE_HZ))
        val targetLn = ln(to.coerceAtLeast(MIN_GLIDE_HZ))
        return exp(startLn + (targetLn - startLn) * GLIDE_COEFFICIENT)
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
        computeLowPass(lowPassCoeff, hz, BUTTERWORTH_Q_STAGE_ONE)
        computeLowPass(lowPassCoeffAlt, hz, BUTTERWORTH_Q_STAGE_TWO)
    }

    private fun refreshHighPass(hz: Double) {
        if (abs(hz - cachedHighPassHz) < 1.0) return
        cachedHighPassHz = hz
        computeHighPass(highPassCoeff, hz, BUTTERWORTH_Q)
    }

    private fun computeLowPass(coeff: DoubleArray, hz: Double, q: Double) {
        val cosW0 = cosBiquadW0(hz)
        val alpha = sinBiquadW0(hz) / (2.0 * q)
        val a0 = 1.0 + alpha
        coeff[0] = ((1.0 - cosW0) / 2.0) / a0
        coeff[1] = (1.0 - cosW0) / a0
        coeff[2] = coeff[0]
        coeff[3] = (-2.0 * cosW0) / a0
        coeff[4] = (1.0 - alpha) / a0
    }

    private fun computeHighPass(coeff: DoubleArray, hz: Double, q: Double) {
        val cosW0 = cosBiquadW0(hz)
        val alpha = sinBiquadW0(hz) / (2.0 * q)
        val a0 = 1.0 + alpha
        coeff[0] = ((1.0 + cosW0) / 2.0) / a0
        coeff[1] = (-(1.0 + cosW0)) / a0
        coeff[2] = coeff[0]
        coeff[3] = (-2.0 * cosW0) / a0
        coeff[4] = (1.0 - alpha) / a0
    }

    private fun sinBiquadW0(hz: Double): Double = sin(2.0 * PI * (hz.coerceIn(30.0, sampleRate * 0.45) / sampleRate))

    private fun cosBiquadW0(hz: Double): Double = cos(2.0 * PI * (hz.coerceIn(30.0, sampleRate * 0.45) / sampleRate))

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
        const val BYPASS_HIGH_PASS_HZ = 20.0
        const val MIN_ACTIVE_HIGH_PASS_HZ = 30.0
        const val SWEEP_TARGET_HZ = 300.0
        const val FULL_CUT_DB = -24.0
        const val BASS_CROSSOVER_HZ = 200.0
        private const val BYTES_PER_SAMPLE = 2
        private const val BYTES_PER_FLOAT_SAMPLE = 4
        private const val BUTTERWORTH_Q = 0.70710678
        private const val BUTTERWORTH_Q_STAGE_ONE = 0.54119610
        private const val BUTTERWORTH_Q_STAGE_TWO = 1.30656296
        private const val SHELF_SLOPE = 1.0
        private const val MIN_GLIDE_HZ = 10.0
        private const val GLIDE_FRAMES = 64
        private const val GLIDE_COEFFICIENT = 0.05
        private const val GLIDE_DB_COEFFICIENT = 0.35
    }
}
