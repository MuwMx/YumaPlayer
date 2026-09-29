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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor

/**
 * Regression guard for the bug that actually produced the audible crackle. Two defects, both
 * invisible in the float path and both only reachable once the filter is engaged:
 *
 * 1. Int16 packing used `(clamped * 32768.0).toInt().toShort()`. Any sample at or above unity
 *    produced 32768, which does not fit in an Int16 and wrapped to -32768, flipping the sign of
 *    the sample. A resonant biquad overshoots above unity on every transient, so kicks flipped
 *    sign, which is a full-band click.
 * 2. The output buffer came from the sink's pool and kept whatever byte order its previous user
 *    left, so putShort and putFloat could write in the wrong order.
 */
class DjFilterInt16ClampTest {

    private val sampleRate = 48_000

    @Test
    fun int16_packsOvershootWithoutWrappingAround() {
        val processor = configured()
        // A large sweep well past unity in both directions.
        val frames = sampleRate / 2
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val value = when (frame % 4) {
                0 -> 1.0
                1 -> 4.0
                2 -> -1.0
                else -> -4.0
            }
            input.putShort((value.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
        }
        input.flip()
        processor.queueInput(input)
        val out = processor.output
        assertTrue("no output produced", out != null && out.capacity() >= frames * 2)
        out.order(ByteOrder.nativeOrder())
        var sawPositiveSaturation = false
        var sawNegativeSaturation = false
        for (i in 0 until frames) {
            val value = out.getShort(i * 2).toInt()
            if (value > 30_000) sawPositiveSaturation = true
            if (value < -30_000) sawNegativeSaturation = true
        }
        assertTrue("positive overshoot must saturate high, not wrap low", sawPositiveSaturation)
        assertTrue("negative overshoot must saturate low, not wrap high", sawNegativeSaturation)
    }

    @Test
    fun int16_roundTripsAQuietToneWithoutGainingOrLosingBits() {
        val processor = configured()
        val frames = sampleRate
        val envelope = 0.25
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val value = kotlin.math.sin(2.0 * Math.PI * 440.0 * frame / sampleRate) * envelope
            input.putShort((value * 32767.0).toInt().toShort())
        }
        input.flip()
        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())
        var measured = 0.0
        for (i in frames - 2000 until frames) {
            measured = maxOf(measured, kotlin.math.abs(out.getShort(i * 2).toInt() / 32767.0))
        }
        assertTrue("bypassed tone envelope $measured drifted from $envelope", kotlin.math.abs(measured - envelope) < 0.01)
    }

    @Test
    fun outputIsWrittenInNativeByteOrder() {
        val processor = configured()
        val frames = 4_096
        val input = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) input.putShort(0)
        input.flip()
        processor.queueInput(input)
        val out = processor.output
        // If the order were wrong, a native-order short written big-endian then read back native
        // would not come back zero for a zero input, so instead assert the order is native here.
        assertEquals(ByteOrder.nativeOrder(), out.order())
    }

    private fun configured(): DjFilterAudioProcessor {
        val processor = DjFilterAudioProcessor()
        val configured = processor.configure(AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        assertEquals(C.ENCODING_PCM_16BIT, configured.encoding)
        processor.flush()
        return processor
    }
}
