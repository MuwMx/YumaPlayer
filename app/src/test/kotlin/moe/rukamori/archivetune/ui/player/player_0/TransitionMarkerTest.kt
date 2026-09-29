/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.ui.player.player_0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.audiodsp.TransitionPlanner

class TransitionMarkerTest {

    private val aggressiveness = CrossfadeConstants.Aggressiveness.STANDARD.name.lowercase()

    @Test
    fun theMarkerLandsExactlyOnTheTriggerTheServiceWillUse() {
        val analysis = TrackAnalysisResult(bpm = 129.8, mixOutTime = 84.0, contentEndTime = 95.0)
        val durationMs = 95_481L
        val positionMs = 12_000L

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = analysis,
            incomingAnalysis = null,
            currentDurationMs = durationMs,
            aggressiveness = aggressiveness,
            currentPositionMs = positionMs,
        )
        val marker = resolveTransitionMarkerMs(analysis, durationMs, positionMs)

        assertEquals(
            "marker drifted away from the audible start of the effect",
            plan.triggerAtMs,
            marker,
        )
    }

    @Test
    fun theMarkerNeverLandsOnAnExitPointTheServiceRejected() {
        val analysis = TrackAnalysisResult(bpm = 129.8, mixOutTime = 18.4, contentEndTime = 95.4)
        val durationMs = 95_481L
        val positionMs = 5_000L

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = analysis,
            incomingAnalysis = null,
            currentDurationMs = durationMs,
            aggressiveness = aggressiveness,
            currentPositionMs = positionMs,
        )
        val marker = resolveTransitionMarkerMs(analysis, durationMs, positionMs) ?: error("marker missing")

        assertEquals(plan.triggerAtMs, marker)
        assertEquals("marker must not sit on the discarded 18s point", marker!! >= 62_000L, true)
    }

    @Test
    fun theMarkerFollowsAPlaybackPositionThatOvertookTheTrigger() {
        val analysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0)
        val durationMs = 95_481L
        val positionMs = 90_000L

        val plan = TransitionPlanner.planSmartTransition(
            outgoingAnalysis = analysis,
            incomingAnalysis = null,
            currentDurationMs = durationMs,
            aggressiveness = aggressiveness,
            currentPositionMs = positionMs,
        )
        val marker = resolveTransitionMarkerMs(analysis, durationMs, positionMs) ?: error("marker missing")

        assertEquals(plan.triggerAtMs, marker)
        assertEquals(positionMs.toLong(), marker)
    }

    @Test
    fun theMarkerIsAbsentWithoutAnalysisOrDuration() {
        assertNull(resolveTransitionMarkerMs(null, 95_481L, 1_000L))
        assertNull(
            resolveTransitionMarkerMs(
                TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0),
                0L,
                1_000L,
            ),
        )
    }

    @Test
    fun theMarkerStaysInsideTheTrack() {
        val analysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 170.0, contentEndTime = 178.0)
        val durationMs = 180_000L

        val marker = resolveTransitionMarkerMs(analysis, durationMs, 1_000L) ?: error("marker missing")

        assertEquals(true, marker > 0L)
        assertEquals(true, marker < durationMs)
    }
}
