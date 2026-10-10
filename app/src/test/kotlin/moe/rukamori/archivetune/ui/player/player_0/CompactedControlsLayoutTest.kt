package moe.rukamori.archivetune.ui.player.player_0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactedControlsLayoutTest {

    @Test
    fun computesCompactionAndLiftCorrectlyWhenFitting() {
        val rootHeight = 2400f
        val capsuleHeaderHeight = 250f
        val toolbarBottom = 300f
        val m0 = 1200f
        val b0 = 1900f
        val transportLift = 42f
        val gapG = 48f

        val layout = computeCompactedControlsLayout(
            rootHeight = rootHeight,
            capsuleHeaderHeight = capsuleHeaderHeight,
            toolbarBottom = toolbarBottom,
            m0 = m0,
            b0 = b0,
            transportLift = transportLift,
            gapG = gapG,
        )

        val expectedD = 42f
        val expectedBc = 1858f
        val expectedGc = 658f
        val expectedQPeek = 1432.5f
        val expectedDeltaPeek = -473.5f

        assertEquals(expectedD, layout.d, 1e-4f)
        assertEquals(expectedBc, layout.bc, 1e-4f)
        assertEquals(expectedGc, layout.gc, 1e-4f)
        assertEquals(expectedQPeek, layout.qPeek, 1e-4f)
        assertEquals(expectedDeltaPeek, layout.deltaPeek, 1e-4f)
        assertEquals(0f, layout.deficit, 1e-4f)
        assertTrue(layout.isFeasible)
        assertEquals(expectedDeltaPeek, layout.effectiveDeltaPeek, 1e-4f)
    }

    @Test
    fun detectsDeficitWhenHeightInsufficient() {
        val rootHeight = 1000f
        val capsuleHeaderHeight = 250f
        val toolbarBottom = 400f
        val m0 = 600f
        val b0 = 950f
        val transportLift = 42f
        val gapG = 48f

        val layout = computeCompactedControlsLayout(
            rootHeight = rootHeight,
            capsuleHeaderHeight = capsuleHeaderHeight,
            toolbarBottom = toolbarBottom,
            m0 = m0,
            b0 = b0,
            transportLift = transportLift,
            gapG = gapG,
        )

        assertEquals(93.5f, layout.deficit, 1e-4f)
        assertFalse(layout.isFeasible)
        assertEquals(0f, layout.effectiveDeltaPeek, 1e-4f)
    }

    @Test
    fun handlesZeroHeightGracefully() {
        val layout = computeCompactedControlsLayout(
            rootHeight = 0f,
            capsuleHeaderHeight = 100f,
            toolbarBottom = 100f,
            m0 = 100f,
            b0 = 200f,
            transportLift = 14f,
            gapG = 16f,
        )

        assertFalse(layout.isReady)
        assertFalse(layout.isFeasible)
        assertEquals(0f, layout.deltaPeek, 1e-4f)
        assertEquals(0f, layout.deficit, 1e-4f)
        assertEquals(0f, layout.effectiveDeltaPeek, 1e-4f)
    }
}
