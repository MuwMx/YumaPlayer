/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.DIVE_HOLD_UNTIL_P
import moe.rukamori.archivetune.audiodsp.INCOMING_START_P
import moe.rukamori.archivetune.audiodsp.SOLO_OUTGOING_PROGRESS
import moe.rukamori.archivetune.audiodsp.incomingStageGain
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import moe.rukamori.archivetune.audiodsp.outgoingStageGain

class StagedCrossfadeCurveTest {

    @Test
    fun outgoing_playsAloneAtFullLevelThroughTheSoloWindow() {
        // The incoming deck is silent until INCOMING_START_P; the outgoing stays at unity until it is
        // fully closed (DIVE_HOLD_UNTIL_P), so the two overlap between those points.
        for (step in 0..(INCOMING_START_P * 1000f).toInt()) {
            val p = step / 1000f
            assertEquals("outgoing must stay at unity during the solo window at p=$p", 1.0f, outgoingStageGain(p), 1e-4f)
            assertEquals("incoming must be silent during the solo window at p=$p", 0.0f, incomingStageGain(p), 1e-4f)
        }
        for (step in 0..(DIVE_HOLD_UNTIL_P * 1000f).toInt()) {
            val p = step / 1000f
            assertEquals("outgoing must stay at unity until the hold ends at p=$p", 1.0f, outgoingStageGain(p), 1e-4f)
        }
    }

    @Test
    fun both_curves_reachTheirEndsAtFullFade() {
        assertEquals(0.0f, outgoingStageGain(1f), 1e-4f)
        assertEquals(1.0f, incomingStageGain(1f), 1e-4f)
    }

    @Test
    fun theRawSumOvershootsUnityBecauseTheCurvesOverlapAndTheDeckCapBoundsIt() {
        // The staged curves overlap on purpose, so their raw sum peaks above 1. ExoDeck caps the outgoing
        // deck by what the incoming one has already taken; CrossfadeSumBoundTest covers that cap.
        var worst = 0f
        for (step in 0..1000) {
            val p = step / 1000f
            worst = maxOf(worst, outgoingStageGain(p) + incomingStageGain(p))
        }
        assertTrue("the curves are expected to overlap, the raw sum was $worst", worst > 1.0f)
        assertTrue("the raw sum must stay well below the clipping range, it was $worst", worst < 1.33f)
    }

    @Test
    fun theCornerStartsClosingWhileTheOutgoingIsStillAtUnity() {
        val soloEnd = SOLO_OUTGOING_PROGRESS / 2f
        assertEquals("outgoing must still be at unity mid solo", 1.0f, outgoingStageGain(soloEnd), 1e-4f)
        assertTrue(
            "the corner must already be closing, otherwise the solo phase is silent",
            outgoingLowPassHz(soloEnd) < 20_000.0,
        )
    }

    @Test
    fun both_curves_moveMonotonically() {
        var previousOut = Float.MAX_VALUE
        var previousIn = -1f
        for (step in 0..1000) {
            val p = step / 1000f
            val out = outgoingStageGain(p)
            val inc = incomingStageGain(p)
            assertTrue("outgoing rose at $p", out <= previousOut + 1e-5f)
            assertTrue("incoming rose at $p", inc >= previousIn - 1e-5f)
            previousOut = out
            previousIn = inc
        }
    }

    @Test
    fun curves_areFlatAtTheHoldJunction() {
        // Smoothstep has zero slope at its start, so the solo window must hand over flat or the
        // outgoing would kink at exactly the progress where the second deck begins to come in.
        val step = 0.001f
        val before = outgoingStageGain(DIVE_HOLD_UNTIL_P - step)
        val at = outgoingStageGain(DIVE_HOLD_UNTIL_P)
        val after = outgoingStageGain(DIVE_HOLD_UNTIL_P + step)
        assertTrue("slope before the hold is $before->$at", abs(at - before) < 1e-4f)
        assertTrue("slope after the hold is $at->$after", abs(after - at) < 1e-4f)
    }

    @Test
    fun curves_clampOutsideTheUnitRange() {
        assertEquals(outgoingStageGain(0f), outgoingStageGain(-0.5f), 1e-6f)
        assertEquals(incomingStageGain(1f), incomingStageGain(1.5f), 1e-6f)
    }
}
