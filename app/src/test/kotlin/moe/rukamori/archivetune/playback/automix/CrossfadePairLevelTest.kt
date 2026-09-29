package moe.rukamori.archivetune.playback.automix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.crossfadePairCompensation
import moe.rukamori.archivetune.audiodsp.crossfadeSummedGain

class CrossfadePairLevelTest {

    private val maxBase = 1.414f
    private val steps = 2000

    @Test
    fun summedGain_staysAtOrBelowUnityWithBothDecksAtMaxNormalisation() {
        var worst = 0f
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val sum = crossfadeSummedGain(progress, maxBase, maxBase)
            assertTrue("summed gain rose to $sum at p=$progress", sum <= 1.0f + 1e-4f)
            worst = maxOf(worst, sum)
        }
        println("worst summed gain at max normalisation: $worst")
    }

    @Test
    fun compensation_isExactlyTheReciprocalOfThePeak() {
        val compensation = crossfadePairCompensation(maxBase, maxBase)
        val peak = sqrt(2.0) * maxBase
        assertEquals((1.0 / peak).toFloat(), compensation, 1e-6f)
    }

    @Test
    fun compensation_isUnityWhenThePairCannotClip() {
        assertEquals(1f, crossfadePairCompensation(0.5f, 0.5f), 1e-6f)
        assertEquals(1f, crossfadePairCompensation(0.7f, 0.2f), 1e-6f)
        assertEquals(1f, crossfadePairCompensation(0f, 0f), 1e-6f)
    }

    @Test
    fun uncompensatedPairWouldClipOnLoudNormalisedTracks() {
        var worst = 0f
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            worst = maxOf(
                worst,
                maxBase * cos(progress * PI / 2.0).toFloat() +
                    maxBase * sin(progress * PI / 2.0).toFloat(),
            )
        }
        println("uncompensated worst case: $worst (${20 * kotlin.math.log10(worst)} dBFS)")
        assertTrue("the uncompensated pair must exceed unity, otherwise there is nothing to fix", worst > 1.0f)
    }

    @Test
    fun compensation_coversEveryAsymmetricBasePair() {
        val bases = listOf(0.2f to 0.3f, 0.5f to 1.0f, 1.0f to 0.4f, 1.414f to 0.05f, 0.9f to 1.2f)
        for ((outgoing, incoming) in bases) {
            for (i in 0..steps) {
                val progress = i / steps.toFloat()
                val sum = crossfadeSummedGain(progress, outgoing, incoming)
                assertTrue(
                    "summed gain $sum above unity for out=$outgoing in=$incoming at p=$progress",
                    sum <= 1.0f + 1e-4f,
                )
            }
        }
    }

    @Test
    fun compensation_keepsTheFadeMonotonicSoItStillReadsAsAFade() {
        var previousOutgoing = Float.MAX_VALUE
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val compensation = crossfadePairCompensation(maxBase, maxBase)
            val outgoing = maxBase * compensation * cos(progress * PI / 2.0).toFloat()
            assertTrue("outgoing gain rose at p=$progress", outgoing <= previousOutgoing + 1e-4f)
            previousOutgoing = outgoing
        }
    }
}
