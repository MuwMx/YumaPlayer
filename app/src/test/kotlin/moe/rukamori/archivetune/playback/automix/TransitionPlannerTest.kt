/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.audiodsp.TransitionPlanner
import moe.rukamori.archivetune.audiodsp.TransitionStyle
import moe.rukamori.archivetune.audiodsp.TransitionTier

class TransitionPlannerTest {

    @Test
    fun normalizedTempoRatio_foldsOctavesProperly() {
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(120.0, 120.0), 1e-6)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(70.0, 140.0), 1e-6)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(140.0, 70.0), 1e-6)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(60.0, 240.0), 1e-6)
        assertEquals(1.05, TransitionPlanner.normalizedTempoRatio(120.0, 126.0), 1e-6)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(0.0, 120.0), 1e-6)
        assertEquals(1.0, TransitionPlanner.normalizedTempoRatio(120.0, -10.0), 1e-6)
    }

    @Test
    fun calculateAdaptiveDuration_boundedWithinDocumentedRange() {
        val durationNormal = TransitionPlanner.calculateAdaptiveDurationSeconds(120.0, 120.0)
        assertTrue(durationNormal in 4.0..12.0)

        val durationFast = TransitionPlanner.calculateAdaptiveDurationSeconds(160.0, 160.0)
        assertTrue(durationFast in 6.0..12.0)

        val durationFallback = TransitionPlanner.calculateAdaptiveDurationSeconds(null, null)
        assertEquals(8.0, durationFallback, 1e-6)

        val durationClampedLow = TransitionPlanner.calculateAdaptiveDurationSeconds(null, null, 1.0)
        assertEquals(4.0, durationClampedLow, 1e-6)

        val durationClampedHigh = TransitionPlanner.calculateAdaptiveDurationSeconds(null, null, 25.0)
        assertEquals(12.0, durationClampedHigh, 1e-6)
    }

    @Test
    fun resolveTransitionStyle_selectsCorrectTier() {
        assertEquals(
            TransitionStyle.BEATMATCHED,
            TransitionPlanner.resolveTransitionStyle(120.0, 124.0),
        )
        assertEquals(
            TransitionStyle.DJ_ASSISTED,
            TransitionPlanner.resolveTransitionStyle(120.0, 134.0),
        )
        assertEquals(
            TransitionStyle.PLAIN_CROSSFADE,
            TransitionPlanner.resolveTransitionStyle(120.0, 150.0),
        )
        assertEquals(
            TransitionStyle.PLAIN_CROSSFADE,
            TransitionPlanner.resolveTransitionStyle(null, 120.0),
        )
    }

    @Test
    fun planTransition_producesValidAutomixPlan() {
        val plan = TransitionPlanner.planTransition(
            currentDurationMs = 180_000L,
            currentBpm = 120.0,
            nextBpm = 122.0,
        )

        assertTrue(plan.durationMs in 4_000L..12_000L)
        assertEquals(plan.durationMs, plan.triggerOffsetMs)
        assertEquals(0L, plan.incomingStartMs)
        assertTrue(plan.enableBassSwap)
    }

    @Test
    fun planTransition_limitsDurationToTrackLength() {
        val plan = TransitionPlanner.planTransition(
            currentDurationMs = 2_000L,
            currentBpm = 120.0,
            nextBpm = 122.0,
        )

        assertEquals(2_000L, plan.durationMs)
        assertEquals(2_000L, plan.triggerOffsetMs)
    }

    @Test
    fun planSmartTransition_interiorCliff_triggersAtMixOut() {
        val outgoing = TrackAnalysisResult(
            bpm = 120.0,
            mixInTime = 2.0,
            mixOutTime = 180.0,
            contentEndTime = 190.0,
        )
        val incoming = TrackAnalysisResult(
            bpm = 122.0,
            mixInTime = 3.5,
            mixOutTime = 150.0,
            contentEndTime = 160.0,
        )

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = incoming,
            currentDurationMs = 200_000L,
        )

        assertEquals(180_000L, plan.triggerAtMs)
        assertEquals(10_000L, plan.durationMs)
        assertEquals(3_500L, plan.incomingStartMs)
        assertTrue(plan.prepareAheadMs!! >= 4_000L)
    }

    @Test
    fun planSmartTransition_terminalContentEnd_adjustsStartBackward() {
        val outgoing = TrackAnalysisResult(
            bpm = 120.0,
            mixInTime = 1.0,
            mixOutTime = 190.0,
            contentEndTime = 190.0,
        )
        val incoming = TrackAnalysisResult(
            bpm = 120.0,
            mixInTime = 4.0,
            mixOutTime = 140.0,
            contentEndTime = 145.0,
        )

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = incoming,
            currentDurationMs = 200_000L,
        )

        assertTrue(plan.durationMs in 4_000L..12_000L)
        assertEquals(182_000L, plan.triggerAtMs)
        assertEquals(4_000L, plan.incomingStartMs)
    }

    @Test
    fun planSmartTransition_noIncoming_fallsBackToZeroStart() {
        val outgoing = TrackAnalysisResult(
            bpm = 120.0,
            mixOutTime = 170.0,
            contentEndTime = 178.0,
        )

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = null,
            currentDurationMs = 180_000L,
        )

        assertEquals(0L, plan.incomingStartMs)
        assertEquals(170_000L, plan.triggerAtMs)
        assertEquals(8_000L, plan.durationMs)
    }

    @Test
    fun resolveBassSwap_isUnboundFromStyle_andOnlySoftDisablesIt() {
        for (style in TransitionStyle.entries) {
            assertTrue("plain style must still filter: $style", TransitionPlanner.resolveBassSwap("standard"))
        }
        assertTrue(TransitionPlanner.resolveBassSwap("club"))
        assertFalse(TransitionPlanner.resolveBassSwap("soft"))
    }

    @Test
    fun planSmartTransition_unanalysedIncoming_stillDrivesTheFilter() {
        val outgoing = TrackAnalysisResult(bpm = 120.0, mixOutTime = 170.0, contentEndTime = 178.0)

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = null,
            currentDurationMs = 180_000L,
        )

        assertEquals(
            TransitionStyle.PLAIN_CROSSFADE,
            TransitionPlanner.resolveTransitionStyle(120.0, null),
        )
        assertTrue(plan.enableBassSwap)
    }

    @Test
    fun planSmartTransition_matchedTempo_enablesBassSwap() {
        val outgoing = TrackAnalysisResult(bpm = 120.0, mixOutTime = 170.0, contentEndTime = 178.0)
        val incoming = TrackAnalysisResult(bpm = 121.0, mixOutTime = 150.0, contentEndTime = 155.0)

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = incoming,
            currentDurationMs = 180_000L,
        )

        assertTrue(plan.enableBassSwap)
    }

    @Test
    fun sanitizeOutroMs_rejectsAnExitPointLatchedOntoAQuietPassage() {
        val contentEndMs = 95_481L

        assertEquals(null, TransitionPlanner.sanitizeOutroMs(18_442L, contentEndMs))
        assertEquals(null, TransitionPlanner.sanitizeOutroMs(20_000L, contentEndMs))
        assertEquals(null, TransitionPlanner.sanitizeOutroMs(0L, contentEndMs))
    }

    @Test
    fun sanitizeOutroMs_acceptsAnExitPointInTheFinalThird() {
        val contentEndMs = 95_481L
        val floor = maxOf((contentEndMs * 0.65).toLong(), contentEndMs - 30_000L)

        assertEquals(floor, TransitionPlanner.sanitizeOutroMs(floor, contentEndMs))
        assertEquals(null, TransitionPlanner.sanitizeOutroMs(floor - 1L, contentEndMs))
        assertEquals(90_000L, TransitionPlanner.sanitizeOutroMs(90_000L, contentEndMs))
        assertEquals(contentEndMs, TransitionPlanner.sanitizeOutroMs(contentEndMs, contentEndMs))
    }

    @Test
    fun planSmartTransition_earlyMixOutFallsBackToTheTrackEnd() {
        val outgoing = TrackAnalysisResult(bpm = 129.8, mixOutTime = 18.4, contentEndTime = 95.4)

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = null,
            currentDurationMs = 95_481L,
        )

        val triggerAt = plan.triggerAtMs ?: error("plan must carry a trigger")
        assertTrue(
            "fade must not start at the rejected 18s point, was $triggerAt",
            triggerAt >= 62_000L,
        )
        assertTrue("fade must fit inside the track", plan.durationMs <= 95_481L - triggerAt)
    }

    @Test
    fun planSmartTransition_validMixOutIsPreserved() {
        val outgoing = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0)

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = null,
            currentDurationMs = 95_000L,
        )

        val triggerAt = plan.triggerAtMs ?: error("plan must carry a trigger")
        assertTrue(
            "a legitimate outro must survive the guard, was $triggerAt",
            triggerAt in 80_000L..86_000L,
        )
    }

    @Test
    fun anchorPlan_leavesAHealthyPlanUntouched() {
        val anchored = TransitionPlanner.anchorPlanToPlaybackPosition(
            plannedStartAtMs = 84_000L,
            plannedFadeMs = 11_000L,
            currentPositionMs = 12_000L,
            contentEndMs = 95_481L,
        )

        assertEquals(84_000L, anchored.first)
        assertEquals(11_000L, anchored.second)
    }

    @Test
    fun anchorPlan_seekedPastTheTrigger_reanchorsAndCompresses() {
        val anchored = TransitionPlanner.anchorPlanToPlaybackPosition(
            plannedStartAtMs = 84_000L,
            plannedFadeMs = 11_000L,
            currentPositionMs = 90_000L,
            contentEndMs = 95_481L,
        )

        assertEquals("trigger must follow playback, never lag behind it", 90_000L, anchored.first)
        assertTrue("remaining time must never go negative", anchored.second > 0L)
        assertEquals(5_481L, anchored.second)
    }

    @Test
    fun planSmartTransition_seekedPastTheTrigger_neverYieldsNegativeRemaining() {
        val outgoing = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0)

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoing,
            incomingAnalysis = null,
            currentDurationMs = 95_481L,
            currentPositionMs = 90_000L,
        )

        val triggerAt = plan.triggerAtMs ?: error("plan must carry a trigger")
        assertTrue("remaining would be negative: $triggerAt - 90000", triggerAt >= 90_000L)
        assertTrue(plan.durationMs > 0L)
    }
}
