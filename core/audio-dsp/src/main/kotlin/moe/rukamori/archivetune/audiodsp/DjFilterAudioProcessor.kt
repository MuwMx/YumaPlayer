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
import kotlin.math.abs

@UnstableApi
class DjFilterAudioProcessor : BaseAudioProcessor() {
    companion object {
        init {
            System.loadLibrary("yuma_analysis")
        }

        const val BYPASS_CUTOFF_HZ = 20_000.0
        const val BYPASS_HIGH_PASS_HZ = 20.0
        const val MIN_ACTIVE_HIGH_PASS_HZ = 30.0
        const val SWEEP_TARGET_HZ = 1200.0
        const val FULL_CUT_DB = -24.0
        const val BASS_CROSSOVER_HZ = 200.0
    }

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
    private var nativeFilterState = DoubleArray(0)
    private var bypassLogged = false

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
        nativeFilterState = DoubleArray(channelCount * 3 * 4)
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

        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frames = inputBuffer.remaining() / (bytesPerSample * channelCount)
        if (frames == 0) return

        val output = replaceOutputBuffer(frames * bytesPerSample * channelCount)
        output.order(ByteOrder.nativeOrder())

        processDirectBufferNative(
            inputBuffer,
            inputBuffer.position(),
            output,
            output.position(),
            frames,
            channelCount,
            encoding,
            sampleRate,
            lowPassCutoffHz,
            highPassHz,
            bassGainDb,
            gain,
            nativeFilterState
        )

        inputBuffer.position(inputBuffer.limit())
        output.position(output.position() + frames * bytesPerSample * channelCount)
        output.flip()
    }

    override fun onFlush() {
        nativeFilterState.fill(0.0)
    }

    override fun onReset() {
        clearAutomation()
        nativeFilterState = DoubleArray(0)
        channelCount = 0
        sampleRate = 0
        encoding = C.ENCODING_INVALID
    }

    private external fun processDirectBufferNative(
        inputBuf: ByteBuffer,
        inputPos: Int,
        outputBuf: ByteBuffer,
        outputPos: Int,
        frames: Int,
        channels: Int,
        encoding: Int,
        sampleRate: Int,
        cutoffHz: Double,
        highPassHz: Double,
        bassDb: Double,
        gain: Double,
        filterState: DoubleArray
    )
}