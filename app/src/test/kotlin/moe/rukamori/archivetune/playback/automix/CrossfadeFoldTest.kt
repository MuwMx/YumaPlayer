package moe.rukamori.archivetune.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import moe.rukamori.archivetune.audiodsp.boundedFoldMs
import moe.rukamori.archivetune.audiodsp.fadeProgress
import moe.rukamori.archivetune.audiodsp.outgoingMidDuckGain

class CrossfadeFoldTest {

    private val durationMs = 10_093L

    @Test
    fun fold_neverEatsMoreThanHalfTheWindow() {
        val fold = boundedFoldMs(latenessMs = 9_806L, durationMs = durationMs)
        val audibleMs = durationMs - fold
        assertTrue("audible fade collapsed to $audibleMs ms", audibleMs >= CrossfadeConstants.MIN_FADE_MS)
        assertTrue("fold must leave at least half the window audible", audibleMs >= durationMs / 2)
    }

    @Test
    fun fold_keepsShortFadesAboveTheMinimum() {
        val shortDuration = 700L
        val fold = boundedFoldMs(latenessMs = 5_000L, durationMs = shortDuration)
        assertTrue("audible $shortDuration minus fold $fold below minimum", shortDuration - fold >= CrossfadeConstants.MIN_FADE_MS)
    }

    @Test
    fun fold_passesThroughLatenessBelowTheLimit() {
        assertEquals(881L, boundedFoldMs(881L, durationMs))
        assertEquals(0L, boundedFoldMs(0L, durationMs))
        assertEquals(0L, boundedFoldMs(-500L, durationMs))
        assertEquals(0L, boundedFoldMs(500L, 0L))
    }

    @Test
    fun progress_startsAtZeroSoNoDeckStepsOnTheFirstTick() {
        val fold = boundedFoldMs(9_806L, durationMs)
        val progress = fadeProgress(elapsedMs = fold, foldMs = fold, durationMs = durationMs)
        assertEquals("progress must not start mid-fade", 0f, progress, 1e-6f)
        assertEquals("outgoing must still be at unity", 1f, FALL(0f), 1e-6f)
        assertEquals("incoming must still be at silence", 0f, RISE(0f), 1e-6f)
    }

    @Test
    fun progress_advancesMonotonicallyAcrossTheAudibleWindow() {
        val fold = boundedFoldMs(3_197L, durationMs)
        var previous = -1f
        var step = 0L
        while (step <= durationMs) {
            val progress = fadeProgress(step, fold, durationMs)
            assertTrue("progress went backwards at $step", progress >= previous)
            previous = progress
            step += 50L
        }
        assertEquals("must finish the window", 1f, fadeProgress(durationMs, fold, durationMs), 1e-6f)
    }

    @Test
    fun progress_clampedIntoTheUnitRangeUnderLateness() {
        val fold = boundedFoldMs(9_806L, durationMs)
        assertEquals(0f, fadeProgress(0L, fold, durationMs), 1e-6f)
        assertEquals(1f, fadeProgress(durationMs + 5_000L, fold, durationMs), 1e-6f)
    }

    @Test
    fun progress_exhaustedWindowIsCompleteRatherThanStuck() {
        assertEquals(1f, fadeProgress(elapsedMs = 0L, foldMs = 100L, durationMs = 100L), 1e-6f)
        assertEquals(1f, fadeProgress(elapsedMs = 5L, foldMs = 200L, durationMs = 100L), 1e-6f)
    }

    @Test
    fun outgoingGain_hasNoStepAtTheStartOfAFoldedFade() {
        val fold = boundedFoldMs(9_806L, durationMs)
        val startProgress = fadeProgress(fold, fold, durationMs)
        val startVolume = FALL(startProgress) * outgoingMidDuckGain(startProgress).toFloat()
        assertEquals("outgoing must not jump on the first tick", 1f, startVolume, 1e-4f)
    }
}
