package moe.rukamori.archivetune.playback.crossmix

import moe.rukamori.archivetune.constants.CrossmixTempoMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class CrossmixPlannerTest {
    private fun makeAnalysis(bpm: Float, durationMs: Long = 180_000L): CrossmixTrackAnalysis {
        val beatMs = 60_000.0 / bpm
        val beatCount = (durationMs / beatMs).toInt()
        val beatsMs = LongArray(beatCount) { (it * beatMs).roundToLong() }
        val barBeats = IntArray((beatCount + 3) / 4) { it * 4 }
        val secCount = (durationMs / 1_000L).toInt().coerceAtLeast(1)
        return CrossmixTrackAnalysis(
            analyzedMs = durationMs,
            complete = true,
            bpm = bpm,
            bpmConfidence = 0.9f,
            beatsMs = beatsMs,
            barBeats = barBeats,
            sectionBars = intArrayOf(0),
            key = "C major",
            keyConfidence = 0.8f,
            leadingSilenceEndMs = 0L,
            trailingSilenceStartMs = durationMs,
            energy = FloatArray(secCount) { 0.7f },
            bass = FloatArray(secCount) { 0.7f },
            brightness = FloatArray(secCount) { 0.5f },
            vibe = Vibe(
                energy = 0.7f,
                danceability = 0.7f,
                brightness = 0.5f,
                bassWeight = 0.7f,
                minor = false,
            ),
        )
    }

    @Test
    fun explicitBassSwap_preservedWhenBothAnalysesNullAndAlwaysMixTrue() {
        val outgoing = CrossmixTrackInput("out", 180_000L, null, null)
        val incoming = CrossmixTrackInput("inc", 180_000L, null, null)
        val settings = CrossmixSettings(style = CrossmixStyle.BassSwap, alwaysMix = true)

        val plan = CrossmixPlanner.plan(outgoing, incoming, settings)

        assertEquals(CrossmixStyle.BassSwap, plan.style)
        assertEquals(1f, plan.inSpeed, 0.0001f)
        assertEquals(1f, plan.outSpeed, 0.0001f)
        assertFalse(plan.beatMatched)
    }

    @Test
    fun explicitBassSwap_preservedWhenIncomingAnalysisNull() {
        val outgoing = CrossmixTrackInput("out", 180_000L, makeAnalysis(120f), null)
        val incoming = CrossmixTrackInput("inc", 180_000L, null, null)
        val settings = CrossmixSettings(style = CrossmixStyle.BassSwap)

        val plan = CrossmixPlanner.plan(outgoing, incoming, settings)

        assertEquals(CrossmixStyle.BassSwap, plan.style)
        assertEquals(1f, plan.inSpeed, 0.0001f)
        assertEquals(1f, plan.outSpeed, 0.0001f)
        assertFalse(plan.beatMatched)
    }

    @Test
    fun explicitBassSwap_withCompatibleBpms_preservesStyleAndAppliesTempoMatch() {
        val outgoing = CrossmixTrackInput("out", 180_000L, makeAnalysis(100f), null)
        val incoming = CrossmixTrackInput("inc", 180_000L, makeAnalysis(104f), null)
        val settings = CrossmixSettings(
            style = CrossmixStyle.BassSwap,
            tempoMatch = CrossmixTempoMatch.FULL,
        )

        val plan = CrossmixPlanner.plan(outgoing, incoming, settings)

        assertEquals(CrossmixStyle.BassSwap, plan.style)
        assertTrue(plan.beatMatched)
        assertTrue(plan.inSpeed != 1f || plan.outSpeed != 1f)
    }

    @Test
    fun autoStyle_selectsPlanAutomatically() {
        val outgoing = CrossmixTrackInput("out", 180_000L, makeAnalysis(120f), null)
        val incoming = CrossmixTrackInput("inc", 180_000L, makeAnalysis(122f), null)

        val plan = CrossmixPlanner.plan(outgoing, incoming, CrossmixSettings(style = null))

        assertNotNull(plan)
        assertNotNull(plan.style)
    }
}
