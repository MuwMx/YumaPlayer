package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
