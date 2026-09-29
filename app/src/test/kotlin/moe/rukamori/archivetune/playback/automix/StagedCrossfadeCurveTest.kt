package moe.rukamori.archivetune.playback.automix

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.SOLO_OUTGOING_PROGRESS
import moe.rukamori.archivetune.audiodsp.incomingStageGain
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import moe.rukamori.archivetune.audiodsp.outgoingStageGain

class StagedCrossfadeCurveTest {

    @Test
    fun outgoing_playsAloneAtFullLevelThroughTheSoloWindow() {
        for (step in 0..350) {
            val p = step / 1000f
            assertEquals("outgoing must stay at unity during the solo window at p=$p", 1.0f, outgoingStageGain(p), 1e-4f)
            assertEquals("incoming must be silent during the solo window at p=$p", 0.0f, incomingStageGain(p), 1e-4f)
        }
    }

    @Test
    fun both_curves_reachTheirEndsAtFullFade() {
        assertEquals(0.0f, outgoingStageGain(1f), 1e-4f)
        assertEquals(1.0f, incomingStageGain(1f), 1e-4f)
    }

    @Test
    fun thePairIsSelfBoundingBecauseTheGainsAreComplementary() {
        var worst = 0f
        for (step in 0..1000) {
            val p = step / 1000f
            worst = maxOf(worst, outgoingStageGain(p) + incomingStageGain(p))
        }
        println("worst raw sum = $worst")
        assertEquals("the curves must be complementary", 1.0f, worst, 1e-4f)
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
        val before = outgoingStageGain(SOLO_OUTGOING_PROGRESS - step)
        val at = outgoingStageGain(SOLO_OUTGOING_PROGRESS)
        val after = outgoingStageGain(SOLO_OUTGOING_PROGRESS + step)
        assertTrue("slope before the hold is $before->$at", abs(at - before) < 1e-4f)
        assertTrue("slope after the hold is $at->$after", abs(after - at) < 1e-4f)
    }

    @Test
    fun curves_clampOutsideTheUnitRange() {
        assertEquals(outgoingStageGain(0f), outgoingStageGain(-0.5f), 1e-6f)
        assertEquals(incomingStageGain(1f), incomingStageGain(1.5f), 1e-6f)
    }
}
