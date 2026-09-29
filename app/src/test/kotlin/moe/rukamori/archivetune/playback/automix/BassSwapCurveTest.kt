package moe.rukamori.archivetune.playback.automix

import kotlin.math.ln
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_END
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_START
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingLowPassHz

class BassSwapCurveTest {

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    @Test
    fun incomingBass_staysCutBeforeWindow_andOpensAfterIt() {
        val fullCut = DjFilterAudioProcessor.FULL_CUT_DB
        assertEquals(fullCut, incomingBassGainDb(0f), 1e-6)
        assertEquals(fullCut, incomingBassGainDb(BASS_SWAP_WINDOW_START), 1e-6)
        assertEquals(0.0, incomingBassGainDb(BASS_SWAP_WINDOW_END), 1e-6)
        assertEquals(0.0, incomingBassGainDb(1f), 1e-6)
    }

    @Test
    fun bassSwap_midpoint_givesIncomingDominance() {
        val progress = (BASS_SWAP_WINDOW_START + BASS_SWAP_WINDOW_END) / 2f
        assertEquals(0.55, dbToLinear(incomingBassGainDb(progress)), 1e-6)
        assertEquals(0.45, dbToLinear(outgoingBassGainDb(progress)), 1e-6)
    }

    @Test
    fun bassSwap_linearGains_stayComplementaryAcrossFade() {
        for (step in 0..100) {
            val progress = step / 100f
            val sum = dbToLinear(incomingBassGainDb(progress)) + dbToLinear(outgoingBassGainDb(progress))
            assertTrue("sum at p=$progress was $sum", sum in 1.0 - 1e-3..1.0 + 0.07)
        }
    }

    @Test
    fun bassSwap_bothDecksNeverOwnTheLowEndAtOnceForLong() {
        var dualActive = 0
        for (step in 0..20) {
            val progress = step / 20f
            val outgoing = dbToLinear(outgoingBassGainDb(progress))
            val incoming = dbToLinear(incomingBassGainDb(progress))
            if (outgoing > 0.8 && incoming > 0.8) dualActive++
        }
        assertTrue("both decks owned the low end for ${dualActive}/21 of the fade", dualActive <= 5)
    }

    @Test
    fun filterSweep_outgoingCornerClosesMonotonicallyDownward() {
        var previous = Double.MAX_VALUE
        for (step in 0..100) {
            val progress = step / 100f
            val lowPass = outgoingLowPassHz(progress)
            assertTrue("outgoing low-pass rose at p=$progress", lowPass <= previous + 1e-6)
            previous = lowPass
        }
    }

    @Test
    fun filterSweep_holdsCornerOpenBeforeTheFadeGetsUnderway() {
        for (step in 0..35) {
            val progress = step / 100f
            assertEquals(
                "corner moved too early at p=$progress",
                DjFilterAudioProcessor.BYPASS_CUTOFF_HZ,
                outgoingLowPassHz(progress),
                1.0,
            )
        }
    }

    @Test
    fun filterSweep_spansFullRangeOnOutgoingDeck() {
        assertEquals(DjFilterAudioProcessor.BYPASS_CUTOFF_HZ, outgoingLowPassHz(0f), 1.0)
        assertEquals(DjFilterAudioProcessor.SWEEP_TARGET_HZ, outgoingLowPassHz(1f), 1.0)
    }

    @Test
    fun filterSweep_neverEntersTheKickBand() {
        assertTrue("corner must stay above the kick band", DjFilterAudioProcessor.SWEEP_TARGET_HZ >= 1000.0)
    }

}
