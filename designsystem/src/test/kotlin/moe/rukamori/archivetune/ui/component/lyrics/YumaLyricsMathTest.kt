/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YumaLyricsMathTest {

    @Test
    fun bezierMatchesCssEase() {
        val motion = MotionTokens()
        assertEquals(0.8024f, motion.ease.at(0.5f), 0.002f)
        assertEquals(0.4094f, motion.ease.at(0.25f), 0.002f)
        assertEquals(0.3153f, motion.easeIn.at(0.5f), 0.002f)
        assertEquals(0f, motion.ease.at(0f), 1e-6f)
        assertEquals(1f, motion.ease.at(1f), 1e-6f)
    }

    @Test
    fun partsSplitOnSpacesWithProratedTime() {
        val tokens = splitPartIntoTokens(
            text = "hello world",
            startMs = 1000L,
            endMs = 2000L,
            isBackground = false,
        )
        assertEquals(3, tokens.size)
        assertEquals("hello", tokens[0].text)
        assertEquals(false, tokens[0].isSpace)
        assertEquals(1.0, tokens[0].startSec, 1e-4)
        assertEquals(0.5, tokens[0].durSec, 1e-4)

        assertEquals(" ", tokens[1].text)
        assertEquals(true, tokens[1].isSpace)

        assertEquals("world", tokens[2].text)
        assertEquals(false, tokens[2].isSpace)
        assertEquals(1.5, tokens[2].startSec, 1e-4)
        assertEquals(0.5, tokens[2].durSec, 1e-4)
    }

    @Test
    fun lineSyncedWordsAreStaggered() {
        val line = YumaLyricsLine(
            timeMs = 5000L,
            endTimeMs = 8000L,
            text = "one two three",
        )
        val parts = buildLineSyncedParts(line, staggerSec = 0.05)
        val words = parts.filter { it.text.isNotBlank() }
        assertEquals(3, words.size)
        assertEquals(5000L, words[0].startMs)
        assertEquals(5050L, words[1].startMs)
        assertEquals(5100L, words[2].startMs)
        assertEquals(words[0].startMs, words[0].endMs)
    }

    @Test
    fun letterWavesCrestWhenLit() {
        val motion = MotionTokens()
        val timing = TimingTokens()
        val waves = letterWaves(
            letterCount = 5,
            wordDurSec = 1.0,
            floatSec = motion.letterWave.secs.toDouble(),
            timing = timing,
            crestFraction = motion.letterWave.crestFraction,
        )
        assertEquals(5, waves.size)

        val before = letterFloat(-0.5, waves[0], isLong = false, motion.letterWave, motion.easeInOut, motion.easeOut)
        assertEquals(0f, before.first, 1e-5f)

        val atCrestTime = waves[0].delaySec + waves[0].durationSec * motion.letterWave.crestFraction
        val atCrest = letterFloat(atCrestTime, waves[0], isLong = false, motion.letterWave, motion.easeInOut, motion.easeOut)
        assertEquals(motion.letterWave.crestEm, atCrest.first, 0.005f)

        val atLongCrest = letterFloat(atCrestTime, waves[0], isLong = true, motion.letterWave, motion.easeInOut, motion.easeOut)
        assertEquals(motion.letterWave.emphasis, atLongCrest.second, 0.005f)
    }

    @Test
    fun scrollDurationsFollowBaselineAndStagger() {
        val dActive = scrollDurationSec(0, 100f, baseSecs = 0.75)
        val dBelow1 = scrollDurationSec(1, 100f, baseSecs = 0.75)
        val dBelow4 = scrollDurationSec(4, 100f, baseSecs = 0.75)
        val dAbove2 = scrollDurationSec(-2, 100f, baseSecs = 0.75)

        assertTrue(dBelow1 > 0.75)
        assertTrue(dBelow4 > dBelow1)
        assertEquals(0.79, dAbove2, 1e-4)
        assertTrue(dActive > 0.75)
    }

    @Test
    fun endAndCenterAlignmentCoordinatesMatchBetweenActiveAndInactive() {
        val fullWidth = 800f
        val contentWidth = fullWidth / 1.0f
        val tokens = splitPartIntoTokens("Hello world", 1000L, 2000L)
        val inputs = tokens.map { FlowBoxInput(it, if (it.isSpace) 0f else 60f, 30f) }

        val alignV2 = resolveEffectiveAlignment(YumaLyricsAlignment.AutoByAgent, "v2")
        val alignV3 = resolveEffectiveAlignment(YumaLyricsAlignment.AutoByAgent, "v3")
        val alignV1000 = resolveEffectiveAlignment(YumaLyricsAlignment.AutoByAgent, "v1000")

        assertEquals(YumaLyricsAlignment.End, alignV2)
        assertEquals(YumaLyricsAlignment.End, alignV3)
        assertEquals(YumaLyricsAlignment.Center, alignV1000)

        val (endBoxes, _) = layoutTokenBoxes(inputs, 10f, contentWidth, 36f, alignV2, false, 20)
        val endInset = computeContentInset(alignV2, fullWidth, contentWidth)
        assertTrue("End-aligned first word must not sit at x=0", endBoxes.first().x + endInset > 400f)

        val (centerBoxes, _) = layoutTokenBoxes(inputs, 10f, contentWidth, 36f, alignV1000, false, 20)
        val centerInset = computeContentInset(alignV1000, fullWidth, contentWidth)
        val centerFirstX = centerBoxes.first().x + centerInset
        assertTrue("Center-aligned first word must not sit at x=0", centerFirstX > 200f && centerFirstX < 500f)

        // Same layout coordinates regardless of active/inactive/exiting state
        val (endBoxesSecondPass, _) = layoutTokenBoxes(inputs, 10f, contentWidth, 36f, alignV2, false, 20)
        assertEquals(endBoxes.map { it.x }, endBoxesSecondPass.map { it.x })
    }

    @Test
    fun repeatedWordsInSameLineUseOwnTokenTiming() {
        val tokens = listOf(
            YumaLyricsWord("Call ", 1000L, 1300L),
            YumaLyricsWord("me ", 1300L, 1600L),
            YumaLyricsWord("crazy, ", 1600L, 2200L),
            YumaLyricsWord("call ", 2500L, 2900L),
            YumaLyricsWord("it ", 2900L, 3200L),
            YumaLyricsWord("Karma", 3200L, 3800L),
        ).flatMap { w -> splitPartIntoTokens(w.text, w.startMs, w.endMs, w.isBackground) }
            .filter { !it.isSpace }

        assertEquals(6, tokens.size)
        assertEquals("Call", tokens[0].text)
        assertEquals(1.0, tokens[0].startSec, 1e-4)
        assertEquals("call", tokens[3].text)
        assertEquals(2.5, tokens[3].startSec, 1e-4)
        assertNotEquals(tokens[0].startSec, tokens[3].startSec)
    }

    @Test
    fun sweepGradientBoundsUseLocalCoordsForDrawTextAndOriginForDstIn() {
        val wordWidth = 120f
        val wordX = 200f
        val localBounds = computeSweepGradientBounds(
            width = wordWidth,
            isRtl = false,
            startFrac = 0.4f,
            endFrac = 0.6f,
            originX = 0f,
        )!!
        assertTrue(localBounds.startX in 0f..wordWidth)
        assertTrue(localBounds.endX in 0f..wordWidth)
        assertEquals(48f, localBounds.startX, 1e-3f)
        assertEquals(73f, localBounds.endX, 1e-3f)

        val canvasBounds = computeSweepGradientBounds(
            width = wordWidth,
            isRtl = false,
            startFrac = 0.4f,
            endFrac = 0.6f,
            originX = wordX,
        )!!
        assertEquals(wordX + 48f, canvasBounds.startX, 1e-3f)
        assertEquals(wordX + 73f, canvasBounds.endX, 1e-3f)
    }

    @Test
    fun instrumentalBreaksInsertedForGapsAtLeast5sAndIntro() {
        val linesWithIntroAndGap = listOf(
            YumaLyricsLine(timeMs = 6000L, endTimeMs = 9000L, text = "First"),
            YumaLyricsLine(timeMs = 14500L, endTimeMs = 17000L, text = "Second"),
            YumaLyricsLine(timeMs = 20000L, endTimeMs = 22000L, text = "Third"),
        )
        val result = insertInstrumentalBreaks(linesWithIntroAndGap, minGapMs = 5000L)
        assertEquals(5, result.size)

        assertTrue(result[0].isInstrumental)
        assertEquals(0L, result[0].timeMs)
        assertEquals(6000L, result[0].endTimeMs)

        assertEquals("First", result[1].text)

        assertTrue(result[2].isInstrumental)
        assertEquals(9000L, result[2].timeMs)
        assertEquals(14500L, result[2].endTimeMs)

        assertEquals("Second", result[3].text)
        assertEquals("Third", result[4].text)

        val shortIntroAndGap = listOf(
            YumaLyricsLine(timeMs = 3000L, endTimeMs = 6000L, text = "A"),
            YumaLyricsLine(timeMs = 10999L, endTimeMs = 13000L, text = "B"),
        )
        val noBreaks = insertInstrumentalBreaks(shortIntroAndGap, minGapMs = 5000L)
        assertEquals(2, noBreaks.size)
        assertTrue(noBreaks.none { it.isInstrumental })
    }
}
