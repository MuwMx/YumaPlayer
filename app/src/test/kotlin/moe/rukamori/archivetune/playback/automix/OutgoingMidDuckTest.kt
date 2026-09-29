/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.MID_DUCK_MAX_DB
import moe.rukamori.archivetune.audiodsp.RISE
import moe.rukamori.archivetune.audiodsp.outgoingMidDuckDb
import moe.rukamori.archivetune.audiodsp.outgoingMidDuckGain

class OutgoingMidDuckTest {

    private val steps = 1000

    @Test
    fun midDuck_isSilentAtStartAndFullAtEnd() {
        assertTrue("start must be untouched", abs(outgoingMidDuckDb(0f)) < 1e-9)
        assertTrue("end must be ${-MID_DUCK_MAX_DB}dB", abs(outgoingMidDuckDb(1f) + MID_DUCK_MAX_DB) < 1e-9)
    }

    @Test
    fun midDuck_decreasesMonotonically() {
        var previous = 0.0
        for (i in 0..steps) {
            val value = outgoingMidDuckDb(i.toFloat() / steps)
            assertTrue("not monotonic at $i: $previous -> $value", value <= previous + 1e-9)
            previous = value
        }
    }

    @Test
    fun midDuck_cutsCoherentOvershootBelowUnduckedSum() {
        var duckedPeak = 0.0
        var unduckedPeak = 0.0
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val fall = cos(progress * PI / 2.0)
            duckedPeak = maxOf(duckedPeak, RISE(progress) + fall * outgoingMidDuckGain(progress))
            unduckedPeak = maxOf(unduckedPeak, RISE(progress) + fall)
        }
        val duckedDb = 20.0 * kotlin.math.log10(duckedPeak)
        val unduckedDb = 20.0 * kotlin.math.log10(unduckedPeak)
        println("coherent sum peak: unducked=$unduckedPeak (%.2f dB) ducked=$duckedPeak (%.2f dB)".format(unduckedDb, duckedDb))
        assertTrue("duck must lower the coherent sum", duckedPeak < unduckedPeak)
        assertTrue("coherent overshoot still $duckedDb dB", duckedPeak <= 1.25)
    }

    @Test
    fun midDuck_doesNotTouchBassSwapCurves() {
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            assertTrue("mid duck must stay in [-6,0]", outgoingMidDuckDb(progress) <= 0.0)
            assertTrue("mid duck floor", outgoingMidDuckDb(progress) >= -MID_DUCK_MAX_DB - 1e-9)
        }
    }

    @Test
    fun midDuck_matchesOrchardSinSquaredShape() {
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val expected = -MID_DUCK_MAX_DB * sin(progress * PI / 2.0) * sin(progress * PI / 2.0)
            assertTrue("shape drift at $i", abs(outgoingMidDuckDb(progress) - expected) < 1e-9)
        }
    }
}
