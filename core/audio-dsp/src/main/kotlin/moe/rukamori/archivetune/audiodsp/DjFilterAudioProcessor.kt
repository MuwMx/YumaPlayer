/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.audiodsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    private var highPassState = Array(0) { DoubleArray(4) }
    private var shelfState = Array(0) { DoubleArray(4) }
    private val lowPassCoeff = DoubleArray(5)
    private val highPassCoeff = DoubleArray(5)
    private val shelfCoeff = DoubleArray(5)
    private var bypassLogged = false
    private var cachedCutoffHz = -1.0
    private var cachedHighPassHz = -1.0
    private var cachedBassDb = Double.NaN

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT &&
            inputAudioFormat.encoding != ENCODING_24BIT_PACKED &&
            inputAudioFormat.encoding != ENCODING_32BIT
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
        highPassState = Array(channelCount) { DoubleArray(4) }
        shelfState = Array(channelCount) { DoubleArray(4) }
        resetCoefficientTracking()
        bypassLogged = false
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            if (!bypassLogged) {
                bypassLogged = true
                Timber.tag("DjFilter").w(
                    "bypass: processor never received a supported format, copying audio unfiltered",
                )
            }
            if (inputBuffer.hasRemaining()) {
                val output = replaceOutputBuffer(inputBuffer.remaining())
                output.order(ByteOrder.nativeOrder())
                output.put(inputBuffer)
                output.flip()
            }
            return
        }
        val bytesPerSample = bytesPerSampleFor(encoding)
        val frames = inputBuffer.remaining() / (bytesPerSample * channelCount)
        if (frames == 0) return
        val output = replaceOutputBuffer(frames * bytesPerSample * channelCount)
        output.order(ByteOrder.nativeOrder())
        // Coefficients are snapshotted once per block and the whole block runs on them, so a
        // parameter can never change halfway through the samples it is filtering. Recomputing
        // mid-block is what the reference avoids and what this loop used to do every 64 frames.
        val cutoff = lowPassCutoffHz
        val highPass = highPassHz
        val bassDb = bassGainDb
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
                var sample = readSample(inputBuffer, encoding)
                if (filtering) {
                    sample = runBiquad(lowPassCoeff, lowPassState[channel], sample)
                }
                if (highPassing) sample = runBiquad(highPassCoeff, highPassState[channel], sample)
                if (shelving) sample = runBiquad(shelfCoeff, shelfState[channel], sample)
                if (applyGain) sample *= level
                writeSample(output, encoding, sample)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun readSample(buffer: ByteBuffer, sampleEncoding: Int): Double = when (sampleEncoding) {
        C.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
        ENCODING_24BIT_PACKED -> {
            val low = buffer.get().toInt() and 0xFF
            val mid = buffer.get().toInt() and 0xFF
            val high = buffer.get().toInt()
            val packed = low or (mid shl 8) or (high shl 16)
            (if (packed and PCM_24BIT_SIGN != 0) packed or PCM_24BIT_SIGN_MASK else packed) / PCM_24BIT_SCALE
        }
        ENCODING_32BIT -> buffer.int.toDouble() / PCM_32BIT_SCALE
        else -> buffer.short.toDouble() / PCM_16BIT_SCALE
    }

    private fun writeSample(buffer: ByteBuffer, sampleEncoding: Int, sample: Double) {
        val clamped = sample.coerceIn(-1.0, 1.0)
        when (sampleEncoding) {
            C.ENCODING_PCM_FLOAT -> buffer.putFloat(clamped.toFloat())
            ENCODING_24BIT_PACKED -> {
                val packed = (clamped * 8388607.0).toInt().coerceIn(-8388608, 8388607)
                buffer.put((packed and 0xFF).toByte())
                buffer.put(((packed shr 8) and 0xFF).toByte())
                buffer.put(((packed shr 16) and 0xFF).toByte())
            }
            ENCODING_32BIT -> {
                val packed = (clamped * 2147483647.0).toLong().coerceIn(-2147483648L, 2147483647L).toInt()
                buffer.putInt(packed)
            }
            else -> {
                val packed = (clamped * 32767.0).toInt().coerceIn(-32768, 32767)
                buffer.putShort(packed.toShort())
            }
        }
    }

    private fun bytesPerSampleFor(sampleEncoding: Int): Int = when (sampleEncoding) {
        C.ENCODING_PCM_FLOAT, ENCODING_32BIT -> 4
        ENCODING_24BIT_PACKED -> 3
        else -> 2
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
    }

    private fun resetDelayLines() {
        for (state in lowPassState) state.fill(0.0)
        for (state in highPassState) state.fill(0.0)
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
        computeLowPass(lowPassCoeff, hz, BUTTERWORTH_Q)
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
        const val SWEEP_TARGET_HZ = 1200.0
        const val FULL_CUT_DB = -24.0
        const val BASS_CROSSOVER_HZ = 200.0
        // Mirror android.media.AudioFormat; these are compile-time constants absent from media3 C.
        private const val ENCODING_24BIT_PACKED = 21
        private const val ENCODING_32BIT = 22
        private const val PCM_16BIT_SCALE = 32768.0
        private const val PCM_24BIT_SCALE = 8388608.0
        private const val PCM_32BIT_SCALE = 2147483648.0
        private const val PCM_24BIT_SIGN = 0x800000
        private const val PCM_24BIT_SIGN_MASK = -0x1000000
        private const val BUTTERWORTH_Q = 0.70710678
        private const val SHELF_SLOPE = 1.0
    }
}
