package moe.rukamori.archivetune.playback.automix

import kotlin.math.ln
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_END
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_START
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.ENTRY_HIGH_PASS_HZ
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.incomingHighPassHz
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
    }

    @Test
    fun filterSweep_bothDecksCloseMonotonicallyDownward() {
        var previousLowPass = Double.MAX_VALUE
        var previousHighPass = Double.MAX_VALUE
        for (step in 0..100) {
            val progress = step / 100f
            val lowPass = outgoingLowPassHz(progress)
            val highPass = incomingHighPassHz(progress)
            assertTrue("outgoing low-pass rose at p=$progress", lowPass <= previousLowPass + 1e-6)
            assertTrue("incoming high-pass rose at p=$progress", highPass <= previousHighPass + 1e-6)
            previousLowPass = lowPass
            previousHighPass = highPass
        }
    }

    @Test
    fun filterSweep_spansFullRangeOnBothDecks() {
        assertEquals(DjFilterAudioProcessor.BYPASS_CUTOFF_HZ, outgoingLowPassHz(0f), 1.0)
        assertEquals(DjFilterAudioProcessor.SWEEP_TARGET_HZ, outgoingLowPassHz(1f), 1.0)
        assertEquals(ENTRY_HIGH_PASS_HZ.toDouble(), incomingHighPassHz(0f), 1.0)
        assertEquals(DjFilterAudioProcessor.BYPASS_HIGH_PASS_HZ, incomingHighPassHz(1f), 1.0)
    }

    @Test
    fun filterSweep_outgoingClosesAtTheSameRateIncomingOpens() {
        val bypassLowPass = DjFilterAudioProcessor.BYPASS_CUTOFF_HZ
        val bypassHighPass = DjFilterAudioProcessor.BYPASS_HIGH_PASS_HZ
        val lowPassSpan = ln(bypassLowPass / DjFilterAudioProcessor.SWEEP_TARGET_HZ)
        val highPassSpan = ln(ENTRY_HIGH_PASS_HZ / bypassHighPass)
        for (step in 1..99) {
            val progress = step / 100f
            val closedFraction = ln(bypassLowPass / outgoingLowPassHz(progress)) / lowPassSpan
            val openedFraction = ln(ENTRY_HIGH_PASS_HZ / incomingHighPassHz(progress)) / highPassSpan
            assertEquals("fractions diverged at p=$progress", closedFraction, openedFraction, 1e-9)
        }
    }
}
