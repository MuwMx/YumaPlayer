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
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor

/**
 * The only case that has never been measured: the filter while its cutoff is moving. Static
 * response is clean, and a single coefficient change per block is roughly one percent, so the
 * sweep has to be fed in the same block-by-block shape the player uses to be meaningful.
 */
class DjFilterSweepInFlightTest {

    private val sampleRate = 48_000
    private val blockFrames = 1_024
    private val totalBlocks = 400
    private val kickHz = 55.0
    private val beatFrames = 24_000

    @Test
    fun movingCutoff_doesNotAddDiscontinuityBeyondAFilteredStaticCase() {
        val sweeping = run { sweepingCutoff(it) }
        val held = run { 1_200.0 }
        val bypass = run { 20_000.0 }

        println("peak |dx|  bypass=${fmt(bypass.peakDelta)}  static1200=${fmt(held.peakDelta)}  sweeping=${fmt(sweeping.peakDelta)}")
        println("click count bypass=${bypass.clicks}  static1200=${held.clicks}  sweeping=${sweeping.clicks}")
        println("max |dx| ratio sweeping/static = ${sweeping.peakDelta / held.peakDelta}")

        org.junit.Assert.assertTrue(
            "a moving corner must not be worse than a static one: " +
                "${sweeping.peakDelta} vs ${held.peakDelta}",
            sweeping.peakDelta <= held.peakDelta * 1.05,
        )
        org.junit.Assert.assertTrue(
            "a moving corner must not add clicks: ${sweeping.clicks} vs ${held.clicks}",
            sweeping.clicks <= held.clicks + 2,
        )
    }

    @Test
    fun movingCutoff_doesNotAddEnergyAboveTheStaticCase() {
        val sweeping = run { sweepingCutoff(it) }
        val held = run { 1_200.0 }
        println("rms sweeping=${fmt(sweeping.rms)}  static1200=${fmt(held.rms)}  ratio=${sweeping.rms / held.rms}")
        org.junit.Assert.assertTrue(
            "sweeping rms ${sweeping.rms} vs static ${held.rms}",
            sweeping.rms <= held.rms * 1.02,
        )
    }

    private fun sweepingCutoff(blockIndex: Int): Double {
        val t = blockIndex.toDouble() / totalBlocks
        return 20_000.0 * (1_200.0 / 20_000.0).pow(t)
    }

    private fun run(cutoffAt: (Int) -> Double): Metrics {
        val processor = DjFilterAudioProcessor()
        processor.configure(AudioFormat(sampleRate, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()

        var peakDelta = 0.0
        var energy = 0.0
        var samples = 0L
        var clicks = 0
        var previous = 0.0

        for (b in 0 until totalBlocks) {
            val cutoff = cutoffAt(b)
            processor.lowPassCutoffHz = cutoff
            val input = kickBlock(b * blockFrames)
            processor.queueInput(input)
            val out = processor.output
            if (out == null) continue
            val capacity = out.capacity()
            for (i in 0 until minOf(blockFrames, capacity / 4)) {
                val value = out.getFloat(i * 4).toDouble()
                val delta = abs(value - previous)
                if (delta > peakDelta) peakDelta = delta
                if (delta > 0.05) clicks++
                energy += value * value
                samples++
                previous = value
            }
            previous = 0.0
        }
        return Metrics(peakDelta, if (samples > 0) kotlin.math.sqrt(energy / samples) else 0.0, clicks)
    }

    private fun kickBlock(startFrame: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(blockFrames * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until blockFrames) {
            val absolute = startFrame + frame
            val sinceBeat = absolute % beatFrames
            val value = if (sinceBeat < 8_000) {
                0.8 * sin(2.0 * PI * kickHz * absolute / sampleRate) * exp(-sinceBeat / 900.0)
            } else {
                0.0
            }
            buffer.putFloat(value.toFloat())
        }
        buffer.flip()
        return buffer
    }

    private fun fmt(value: Double): String = "%.6f".format(value)

    private data class Metrics(val peakDelta: Double, val rms: Double, val clicks: Int)

    private fun Double.pow(exponent: Double): Double = kotlin.math.exp(kotlin.math.ln(this) * exponent)
}
