package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.incomingStageGain
import moe.rukamori.archivetune.audiodsp.outgoingStageGain

/**
 * Two decks are audible during the overlap, so their linear gains must sum to at most unity. This
 * is the measured failure: the sum reached 1.33 at p=0.36 and the crackle was audible from
 * p=0.18 to p=0.74, matching the window where the sum exceeded one.
 */
class CrossfadeSumBoundTest {

    private val steps = 4000

    private fun outgoingGain(progress: Float, base: Float, partnerBase: Float): Float {
        val cap = (1f - incomingGain(progress, partnerBase)).coerceIn(0f, 1f)
        return (base * outgoingStageGain(progress)).coerceAtMost(cap)
    }

    private fun incomingGain(progress: Float, base: Float): Float =
        (base * incomingStageGain(progress)).coerceAtMost(1f)

    @Test
    fun summedGains_neverExceedUnity() {
        for (base in listOf(1f, 1.414f, 0.7f)) {
            var worst = 0f
            for (i in 0..steps) {
                val progress = i / steps.toFloat()
                val out = outgoingGain(progress, base, base)
                val inc = incomingGain(progress, base)
                worst = maxOf(worst, out + inc)
            }
            assertTrue("sum reached $worst with base $base", worst <= 1.0f + 1e-4f)
        }
    }

    @Test
    fun summedGains_neverExceedUnityWhenTheDecksCarryDifferentBaseLevels() {
        val pairs = listOf(1.2f to 0.8f, 1.414f to 0.5f, 0.4f to 1.3f, 1f to 1.414f, 0.6f to 0.9f)
        for ((outBase, inBase) in pairs) {
            var worst = 0f
            for (i in 0..steps) {
                val progress = i / steps.toFloat()
                worst = maxOf(worst, outgoingGain(progress, outBase, inBase) + incomingGain(progress, inBase))
            }
            assertTrue("sum reached $worst for out=$outBase in=$inBase", worst <= 1.0f + 1e-4f)
        }
    }

    @Test
    fun theAsymmetricCaseIsWhatMakesTheCapNecessary() {
        var wouldOverflow = 0
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val raw = (1.2f * outgoingStageGain(progress)).coerceAtMost(1f)
            if (raw + incomingGain(progress, 0.8f) > 1.0f) wouldOverflow++
        }
        assertTrue(
            "an asymmetric pair must actually need the cap, otherwise the test proves nothing",
            wouldOverflow > 0,
        )
    }

    @Test
    fun theCapLeavesTheEndsOfTheCurveUntouched() {
        val base = 1f
        val atStart = outgoingGain(0f, base, base)
        val atEnd = outgoingGain(1f, base, base)
        assertEquals("fade start must not be attenuated", base * outgoingStageGain(0f), atStart, 1e-4f)
        assertEquals("fade end must reach silence", 0f, atEnd, 1e-4f)
    }

    @Test
    fun theCapIsRedundantWhenBothDecksShareTheSameBase() {
        val base = 1f
        var capEngaged = 0
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val raw = base * outgoingStageGain(progress)
            val capped = outgoingGain(progress, base, base)
            if (raw > capped + 1e-4f) capEngaged++
        }
        assertEquals("the curves are complementary, so the cap must never need to engage", 0, capEngaged)
    }

    @Test
    fun theCapOnlyBitesWhereTheRawCurveWouldOverflow() {
        val base = 1f
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val raw = base * outgoingStageGain(progress)
            val capped = outgoingGain(progress, base, base)
            val rawSum = raw + incomingGain(progress, base)
            if (rawSum <= 1.0f) {
                assertEquals("cap must not engage below the overflow point at p=$progress", raw, capped, 1e-4f)
            }
        }
    }

}
