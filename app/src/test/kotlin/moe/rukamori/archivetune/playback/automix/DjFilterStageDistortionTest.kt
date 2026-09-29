/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor

class DjFilterStageDistortionTest {

    private val sampleRate = 48_000

    @Test
    fun lowShelf_atDeepCut_doesNotRingOnLowFrequencyTransient() {
        val processor = configured()
        processor.bassGainDb = DjFilterAudioProcessor.FULL_CUT_DB
        val ringOut = ringAfterBurst(processor)
        println("shelf -24dB: ringOut=$ringOut")
        assertTrue("shelf rings on the transient: $ringOut", ringOut < 0.005)
    }

    @Test
    fun highPass_atEntryCut_doesNotRingOnLowFrequencyTransient() {
        val processor = configured()
        processor.highPassHz = 1200.0
        val ringOut = ringAfterBurst(processor)
        println("hp 1200Hz: ringOut=$ringOut")
        assertTrue("high-pass rings on the transient: $ringOut", ringOut < 0.005)
    }

    @Test
    fun lowShelf_atDeepCut_hasLowHarmonicDistortion() {
        val processor = configured()
        processor.bassGainDb = DjFilterAudioProcessor.FULL_CUT_DB
        val thd = harmonicDistortion(processor, 60.0)
        println("shelf -24dB @60Hz: thd=$thd")
        assertTrue("shelf distortion at 60Hz: $thd", thd < 0.02)
    }

    @Test
    fun highPass_atEntryCut_hasLowHarmonicDistortion() {
        val processor = configured()
        processor.highPassHz = 1200.0
        val thd = harmonicDistortion(processor, 60.0)
        println("hp 1200Hz @60Hz: thd=$thd")
        assertTrue("high-pass distortion at 60Hz: $thd", thd < 0.02)
    }

    private fun configured(): DjFilterAudioProcessor {
        val processor = DjFilterAudioProcessor()
        val configured = processor.configure(AudioFormat(sampleRate, 1, C.ENCODING_PCM_FLOAT))
        assertTrue("configure rejected", configured.encoding == C.ENCODING_PCM_FLOAT)
        return processor
    }

    private fun ringAfterBurst(processor: DjFilterAudioProcessor): Double {
        processor.flush()
        val burstFrames = 2_400
        val tailFrames = 4_800
        val total = burstFrames + tailFrames
        val buffer = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until total) {
            val value = if (frame < burstFrames) {
                0.5 * sin(2.0 * PI * 60.0 * frame / sampleRate) * exp(-frame / 600.0)
            } else {
                0.0
            }
            buffer.putFloat(value.toFloat())
        }
        buffer.flip()
        processor.queueInput(buffer)
        val out = processor.output ?: return 0.0
        out.position(0)
        out.limit(out.capacity())
        var tailEnergy = 0.0
        for (index in burstFrames until total) {
            val value = out.getFloat(index * 4).toDouble()
            tailEnergy += value * value
        }
        return sqrt(tailEnergy / tailFrames)
    }

    private fun harmonicDistortion(processor: DjFilterAudioProcessor, hz: Double): Double {
        processor.flush()
        val total = 24_000
        val settle = 12_000
        val buffer = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until total) {
            buffer.putFloat((0.5 * sin(2.0 * PI * hz * frame / sampleRate)).toFloat())
        }
        buffer.flip()
        processor.queueInput(buffer)
        val out = processor.output ?: return 0.0
        out.position(0)
        out.limit(out.capacity())
        val window = (total - settle).toDouble()
        var fundamental = 0.0
        var harmonics = 0.0
        for (harmonic in 1..5) {
            var re = 0.0
            var im = 0.0
            for (index in settle until total) {
                val value = out.getFloat(index * 4).toDouble()
                val phase = 2.0 * PI * hz * harmonic * index / sampleRate
                re += value * cos(phase)
                im += value * sin(phase)
            }
            val magnitude = 2.0 * sqrt(re * re + im * im) / window
            if (harmonic == 1) fundamental = magnitude else harmonics += magnitude * magnitude
        }
        return if (fundamental > 0.0) sqrt(harmonics) / fundamental else 0.0
    }
}
