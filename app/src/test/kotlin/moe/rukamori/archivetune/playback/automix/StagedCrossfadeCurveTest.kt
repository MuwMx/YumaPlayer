package moe.rukamori.archivetune.playback.automix

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.STAGE_HOLD_PROGRESS
import moe.rukamori.archivetune.audiodsp.incomingStageGain
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz
import moe.rukamori.archivetune.audiodsp.outgoingStageGain

class StagedCrossfadeCurveTest {

    @Test
    fun outgoing_passesThroughTheReferenceAnchorPoints() {
        assertEquals(1.0f, outgoingStageGain(0f), 1e-4f)
        assertEquals(0.95f, outgoingStageGain(STAGE_HOLD_PROGRESS), 1e-4f)
        assertEquals(0.0f, outgoingStageGain(1f), 1e-4f)
    }

    @Test
    fun incoming_passesThroughTheReferenceAnchorPoints() {
        assertEquals(0.0f, incomingStageGain(0f), 1e-4f)
        assertEquals(0.38f, incomingStageGain(STAGE_HOLD_PROGRESS), 1e-4f)
        assertEquals(1.0f, incomingStageGain(1f), 1e-4f)
    }

    @Test
    fun outgoing_keepsItsFullLevelWhileTheCornerCloses() {
        val p = STAGE_HOLD_PROGRESS / 2f
        assertTrue("outgoing must not be ducked under the filter", outgoingStageGain(p) > 0.9f)
        assertEquals(
            "the corner must still be open at $p",
            20_000.0,
            outgoingLowPassHz(p),
            1.0,
        )
    }

    @Test
    fun incoming_staysQuietUntilTheOutgoingIsDark() {
        assertTrue("incoming audible too early at 0.10", incomingStageGain(0.10f) < 0.12f)
        assertTrue("incoming audible too early at 0.25", incomingStageGain(0.25f) < 0.38f)
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
        // Smoothstep has zero slope at both ends, so the two segments must meet flat or the
        // outgoing would kink and click at exactly the progress where the corner starts closing.
        val step = 0.001f
        val before = outgoingStageGain(STAGE_HOLD_PROGRESS - step)
        val at = outgoingStageGain(STAGE_HOLD_PROGRESS)
        val after = outgoingStageGain(STAGE_HOLD_PROGRESS + step)
        assertTrue("slope before the hold is $before->$at", abs(at - before) < 1e-4f)
        assertTrue("slope after the hold is $at->$after", abs(after - at) < 1e-4f)
    }

    @Test
    fun curves_clampOutsideTheUnitRange() {
        assertEquals(outgoingStageGain(0f), outgoingStageGain(-0.5f), 1e-6f)
        assertEquals(incomingStageGain(1f), incomingStageGain(1.5f), 1e-6f)
    }
}
