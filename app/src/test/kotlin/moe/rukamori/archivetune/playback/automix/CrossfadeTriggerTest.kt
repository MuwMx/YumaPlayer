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
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.playback.crossfade.resolveCrossfadeTriggerAt
class CrossfadeTriggerTest {

    private val durationMs = 95_481L
    private val fadeMs = 5_000L

    @Test
    fun aPlanTriggerIsUsedVerbatim() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0),
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = 71_234L,
        )

        assertEquals(71_234L, trigger)
    }

    @Test
    fun anEarlyMixOutCanNeverBecomeTheTrigger() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = TrackAnalysisResult(bpm = 129.8, mixOutTime = 18.4, contentEndTime = 95.4),
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        assertEquals("rejected 18s point must fall back to the content end", 95_400L - fadeMs, trigger)
        assertTrue("trigger must never precede the outro floor", trigger >= 62_000L)
    }

    @Test
    fun aRejectedMixOutIsNotShiftedInsteadOfDiscarded() {
        val analysis = TrackAnalysisResult(bpm = 129.8, mixOutTime = 18.4, contentEndTime = 95.4)
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = analysis,
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        val halfFadeShift = 18_400L - (fadeMs / 2L)
        assertTrue(
            "the old half-fade shift must be gone, got $trigger",
            trigger != halfFadeShift,
        )
    }

    @Test
    fun aValidMixOutIsUsedAsIs() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0),
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        assertEquals(84_000L, trigger)
    }

    @Test
    fun withoutAnalysisTheTriggerFallsBackToTheEndOfTheTrack() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = null,
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        assertEquals(durationMs - fadeMs, trigger)
    }

    @Test
    fun withAutomixOffTheMixOutIsIgnoredEntirely() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = false,
            outgoingAnalysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 84.0, contentEndTime = 95.0),
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        assertEquals(95_000L - fadeMs, trigger)
    }

    @Test
    fun contentEndLongerThanTheReportedDurationCannotPushTheTriggerOut() {
        val trigger = resolveCrossfadeTriggerAt(
            automixEnabled = true,
            outgoingAnalysis = TrackAnalysisResult(bpm = 120.0, mixOutTime = 200.0, contentEndTime = 200.0),
            durationMs = durationMs,
            triggerOffsetMs = fadeMs,
            planTriggerAtMs = null,
        )

        assertTrue("trigger must stay inside the track, got $trigger", trigger <= durationMs)
    }
}
