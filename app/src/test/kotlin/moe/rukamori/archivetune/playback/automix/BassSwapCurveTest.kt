package moe.rukamori.archivetune.playback.automix

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_END
import moe.rukamori.archivetune.audiodsp.BASS_SWAP_WINDOW_START
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.incomingBassGainDb
import moe.rukamori.archivetune.audiodsp.outgoingBassGainDb

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
    fun outgoingBass_staysOpenBeforeWindow_andIsCutAfterIt() {
        val fullCut = DjFilterAudioProcessor.FULL_CUT_DB
        assertEquals(0.0, outgoingBassGainDb(0f), 1e-6)
        assertEquals(0.0, outgoingBassGainDb(BASS_SWAP_WINDOW_START), 1e-6)
        assertEquals(fullCut, outgoingBassGainDb(BASS_SWAP_WINDOW_END), 1e-6)
        assertEquals(fullCut, outgoingBassGainDb(1f), 1e-6)
    }

    @Test
    fun bassSwap_midpoint_givesIncomingDominance() {
        val progress = (BASS_SWAP_WINDOW_START + BASS_SWAP_WINDOW_END) / 2f
        val incoming = dbToLinear(incomingBassGainDb(progress))
        val outgoing = dbToLinear(outgoingBassGainDb(progress))

        assertEquals(0.55, incoming, 1e-6)
        assertEquals(0.45, outgoing, 1e-6)
    }

    @Test
    fun bassSwap_linearGains_stayComplementaryAcrossFade() {
        val floor = dbToLinear(DjFilterAudioProcessor.FULL_CUT_DB)
        var previous = Double.NEGATIVE_INFINITY
        for (step in 0..100) {
            val progress = step / 100f
            val incoming = incomingBassGainDb(progress)
            val sum = dbToLinear(incoming) + dbToLinear(outgoingBassGainDb(progress))

            assertTrue("sum at p=$progress was $sum", sum in (1.0 - 1e-3)..(1.0 + floor + 1e-3))
            assertTrue("incoming not monotonic at p=$progress", incoming >= previous - 1e-6)
            previous = incoming
        }
    }

    @Test
    fun bassSwap_insideWindow_isExactlyComplementary() {
        for (step in 46..54) {
            val progress = step / 100f
            val sum = dbToLinear(incomingBassGainDb(progress)) + dbToLinear(outgoingBassGainDb(progress))
            assertEquals("sum at p=$progress", 1.0, sum, 1e-3)
        }
    }
}
