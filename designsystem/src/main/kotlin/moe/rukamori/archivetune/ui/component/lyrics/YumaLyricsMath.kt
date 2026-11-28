/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

internal fun YumaCubicBezier.at(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f

    fun curve(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }

    fun slope(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b)
    }

    var t = x
    for (i in 0 until 8) {
        val err = curve(t, x1, x2) - x
        if (abs(err) < 1e-6f) {
            return curve(t, y1, y2)
        }
        val d = slope(t, x1, x2)
        if (abs(d) < 1e-6f) break
        t = (t - err / d).coerceIn(0f, 1f)
    }

    var lo = 0f
    var hi = 1f
    t = x
    for (i in 0 until 40) {
        val v = curve(t, x1, x2)
        if (abs(v - x) < 1e-6f) break
        if (v < x) lo = t else hi = t
        t = (lo + hi) * 0.5f
    }
    return curve(t, y1, y2)
}

internal fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun isJoiningScript(text: String): Boolean =
    text.any { ch ->
        when (ch.code) {
            in 0x0600..0x06FF,
            in 0x0700..0x074F,
            in 0x0750..0x077F,
            in 0x0780..0x07BF,
            in 0x0840..0x085F,
            in 0x08A0..0x08FF,
            in 0x1800..0x18AF,
            in 0xFB50..0xFDFF,
            in 0xFE70..0xFEFF,
            -> true
            else -> false
        }
    }

internal fun hasRtl(text: String): Boolean =
    text.any { ch ->
        when (ch.code) {
            in 0x0590..0x05FF,
            in 0x0600..0x06FF,
            in 0x0700..0x074F,
            in 0x0780..0x07BF,
            in 0x07C0..0x07FF,
            in 0x0800..0x083F,
            in 0x0840..0x085F,
            in 0x08A0..0x08FF,
            in 0xFB1D..0xFDFF,
            in 0xFE70..0xFEFF,
            -> true
            else -> false
        }
    }

internal data class YumaToken(
    val text: String,
    val startSec: Double,
    val durSec: Double,
    val isBackground: Boolean,
    val isSpace: Boolean,
)

internal fun splitPartIntoTokens(
    text: String,
    startMs: Long,
    endMs: Long,
    isBackground: Boolean = false,
): List<YumaToken> {
    if (text.isEmpty()) return emptyList()
    val chunks = ArrayList<Pair<StringBuilder, Boolean>>()
    for (c in text) {
        val ws = c.isWhitespace()
        val last = chunks.lastOrNull()
        if (last != null && last.second == ws) {
            last.first.append(c)
        } else {
            chunks.add(StringBuilder().append(c) to ws)
        }
    }

    val totalChars = chunks.filter { !it.second }.sumOf { it.first.length }
    val durMs = (endMs - startMs).coerceAtLeast(0L).toDouble()
    var cursor = 0

    return chunks.map { (sb, isSpace) ->
        val chunkText = sb.toString()
        if (isSpace) {
            YumaToken(
                text = chunkText,
                startSec = 0.0,
                durSec = 0.0,
                isBackground = isBackground,
                isSpace = true,
            )
        } else {
            val n = chunkText.length
            val tokenStartMs: Double
            val tokenEndMs: Double
            if (totalChars == 0) {
                tokenStartMs = startMs.toDouble()
                tokenEndMs = startMs.toDouble()
            } else {
                tokenStartMs = (startMs + (durMs * cursor / totalChars).roundToLong()).toDouble()
                tokenEndMs = (startMs + (durMs * (cursor + n) / totalChars).roundToLong()).toDouble()
            }
            cursor += n
            YumaToken(
                text = chunkText,
                startSec = tokenStartMs / 1000.0,
                durSec = (tokenEndMs - tokenStartMs).coerceAtLeast(0.0) / 1000.0,
                isBackground = isBackground,
                isSpace = false,
            )
        }
    }
}

internal fun buildLineSyncedParts(
    line: YumaLyricsLine,
    staggerSec: Double = 0.05,
): List<YumaLyricsWord> {
    if (line.text.isEmpty()) return emptyList()
    val chunks = ArrayList<Pair<StringBuilder, Boolean>>()
    for (c in line.text) {
        val ws = c.isWhitespace()
        val last = chunks.lastOrNull()
        if (last != null && last.second == ws) {
            last.first.append(c)
        } else {
            chunks.add(StringBuilder().append(c) to ws)
        }
    }

    val out = ArrayList<YumaLyricsWord>(chunks.size)
    var wordIndex = 0
    for ((sb, isWs) in chunks) {
        val startMs = line.timeMs + (wordIndex * staggerSec * 1000.0).toLong()
        out.add(
            YumaLyricsWord(
                text = sb.toString(),
                startMs = startMs,
                endMs = startMs,
                isBackground = false,
            ),
        )
        if (!isWs) {
            wordIndex++
        }
    }
    return out
}

internal data class WordLook(
    val opacity: Float,
    val start: Float,
    val end: Float,
    val glowRadiusPx: Float,
    val glowAlpha: Float,
)

internal fun glowRadius(
    elapsedSec: Double,
    durSec: Double,
    fromPx: Float,
    ease: YumaCubicBezier,
): Float {
    if (elapsedSec < 0.0 || elapsedSec >= durSec || durSec <= 0.0) return 0f
    return fromPx * (1f - ease.at((elapsedSec / durSec).toFloat()))
}

internal fun computeWordLook(
    wordStartSec: Double,
    wordDurSec: Double,
    isLineSynced: Boolean,
    isLong: Boolean,
    tSec: Double,
    isLive: Boolean,
    exitSinceWallSec: Double?,
    exitSongSec: Double,
    glowFromPx: Float,
    tokens: YumaLyricsTokens,
): WordLook {
    val timing = tokens.timing
    val motion = tokens.motion
    val visual = tokens.visual
    val glowAlpha = if (isLong) visual.longGlowAlpha else visual.glowAlpha
    val swipeStart = timing.swipeStart
    val swipeEnd = timing.swipeEnd

    if (exitSinceWallSec != null) {
        if (exitSongSec < wordStartSec) {
            return WordLook(0f, swipeStart.first, swipeEnd.first, 0f, glowAlpha)
        }
        val p = (exitSinceWallSec / timing.exitFadeSec.coerceAtLeast(0.01f)).toFloat().coerceIn(0f, 1f)
        val opacity = lerpFloat(1f, 0f, motion.ease.at(p)).coerceIn(0f, 1f)
        return WordLook(opacity, swipeStart.second, swipeEnd.second, 0f, glowAlpha)
    }

    if (!isLive) {
        return WordLook(0f, swipeStart.first, swipeEnd.first, 0f, glowAlpha)
    }

    val elapsed = tSec - wordStartSec
    if (elapsed < 0.0) {
        return WordLook(0f, swipeStart.first, swipeEnd.first, 0f, glowAlpha)
    }

    val effectiveGlowFrom = if (glowAlpha < visual.glowInvisibleAlpha) 0f else glowFromPx

    if (isLineSynced) {
        val p = motion.ease.at((elapsed / timing.lineFadeSec.coerceAtLeast(0.01f)).toFloat())
        val glow = glowRadius(elapsed, timing.glowMinSec.toDouble(), effectiveGlowFrom, motion.ease)
        return WordLook(p, swipeStart.second, swipeEnd.second, glow, glowAlpha)
    }

    val d = max(wordDurSec, 0.0)
    val sd = d * timing.swipeDurationRatio
    val p = if (sd <= 0.0) {
        1f
    } else {
        ((elapsed + d * timing.swipeLeadRatio) / sd).toFloat().coerceIn(0f, 1f)
    }
    val glowDur = max(d * timing.glowRatio, timing.glowMinSec.toDouble())
    return WordLook(
        opacity = 1f,
        start = lerpFloat(swipeStart.first, swipeStart.second, p),
        end = lerpFloat(swipeEnd.first, swipeEnd.second, p),
        glowRadiusPx = glowRadius(elapsed, glowDur, effectiveGlowFrom, motion.ease),
        glowAlpha = glowAlpha,
    )
}

internal data class LetterWaveTiming(
    val delaySec: Double,
    val durationSec: Double,
)

internal fun letterWaves(
    letterCount: Int,
    wordDurSec: Double,
    floatSec: Double,
    timing: TimingTokens = TimingTokens(),
    crestFraction: Float = 0.4f,
): List<LetterWaveTiming> {
    if (letterCount <= 0) return emptyList()
    val sd = wordDurSec * timing.swipeDurationRatio
    val lead = wordDurSec * timing.swipeLeadRatio
    val rise = floatSec * crestFraction
    val (sf, st) = timing.swipeStart.first.toDouble() to timing.swipeStart.second.toDouble()
    val (ef, et) = timing.swipeEnd.first.toDouble() to timing.swipeEnd.second.toDouble()

    return List(letterCount) { i ->
        if (sd <= 0.0) {
            LetterWaveTiming(0.0, floatSec)
        } else {
            val begin = (sd * (i.toDouble() / letterCount - ef) / (et - ef)).coerceIn(0.0, sd)
            val finish = (sd * ((i + 1).toDouble() / letterCount - sf) / (st - sf)).coerceIn(0.0, sd)
            val dur = max(finish - begin, 0.001)
            val touched = begin - lead
            val lit = touched + dur
            if (lit - rise > touched) {
                LetterWaveTiming(touched, (lit - touched) / crestFraction)
            } else {
                LetterWaveTiming(lit - rise, floatSec)
            }
        }
    }
}

internal fun letterFloat(
    elapsedSec: Double,
    wave: LetterWaveTiming,
    isLong: Boolean,
    tokens: LetterWaveTokens,
    easeInOut: YumaCubicBezier,
    easeOut: YumaCubicBezier,
): Pair<Float, Float> {
    val rawP = (elapsedSec - wave.delaySec) / wave.durationSec.coerceAtLeast(0.001)
    if (rawP < 0.0) return 0f to tokens.restScale
    val p = min(rawP, 1.0).toFloat()
    val peak = if (isLong) tokens.emphasis else 1f
    val crest = tokens.crestFraction
    return if (p < crest) {
        val k = easeInOut.at(p / crest)
        (tokens.crestEm * k) to lerpFloat(tokens.restScale, peak, k)
    } else {
        val k = easeOut.at((p - crest) / (1f - crest))
        lerpFloat(tokens.crestEm, tokens.settleEm, k) to lerpFloat(peak, 1f, k)
    }
}

internal fun wobble(
    elapsedSec: Double,
    tokens: WobbleTokens,
    ease: YumaCubicBezier,
    easeInOut: YumaCubicBezier,
): Pair<Float, Float> {
    val secs = tokens.secs.toDouble()
    if (!tokens.enabled || elapsedSec < 0.0 || elapsedSec >= secs || secs <= 0.0) {
        return 0f to 1f
    }
    val p = ease.at((elapsedSec / secs).toFloat())
    return when {
        p < 0.125f -> {
            val k = p / 0.125f
            (tokens.peakTranslateEm * k) to lerpFloat(1f, tokens.peakScaleX, k)
        }
        p < 0.75f -> {
            val k = easeInOut.at((p - 0.125f) / 0.625f)
            lerpFloat(tokens.peakTranslateEm, 0f, k) to lerpFloat(tokens.peakScaleX, 1f, k)
        }
        else -> 0f to 1f
    }
}

internal fun wordBounce(
    elapsedSec: Double,
    wordDurSec: Double,
    tokens: WordBounceTokens,
): Pair<Float, Float> {
    if (elapsedSec < 0.0) return 0f to 1f
    val secs = (tokens.secs + max(wordDurSec, 0.0).toFloat() * tokens.durRatio).coerceAtLeast(0.01f)
    val p = (elapsedSec / secs).toFloat().coerceAtMost(1f)
    if (p >= 1f) return tokens.settleEm to 1f
    val shape = if (tokens.spring) {
        sin(p * PI.toFloat() * 1.5f) * (1f - p) * 1.4f
    } else {
        sin(p * PI.toFloat())
    }
    return (tokens.settleEm * p + tokens.liftEm * shape) to lerpFloat(1f, tokens.scale.coerceAtLeast(0.1f), max(shape, 0f))
}

internal fun scrollDurationSec(
    relIndex: Int,
    deltaPx: Float,
    baseSecs: Double = 0.75,
): Double {
    val up = deltaPx < 0f
    val above = (relIndex < 0 && !up) || (relIndex > 0 && up)
    val i = abs(relIndex).toDouble()
    val baseMs = baseSecs * 1000.0
    return if (above) {
        (baseMs + min(i, 6.0) * 20.0) / 1000.0
    } else {
        (baseMs + ln(i + 2.0) * 80.0 + (i + 1.0) * 20.0) / 1000.0
    }
}

internal fun ladderStyle(table: List<LineDistanceStyle>, distance: Int): LineDistanceStyle {
    if (table.isEmpty()) return LineDistanceStyle(alpha = 1f, blurFraction = 0f)
    val idx = (distance - 1).coerceAtLeast(0)
    return table.getOrElse(idx) { table.last() }
}

internal fun nearestAgent(lines: List<YumaLyricsLine>, index: Int): String? {
    for (i in (index + 1) until lines.size) {
        val line = lines[i]
        if (!line.isInstrumental && line.agent != null) return line.agent
    }
    for (i in (index - 1) downTo 0) {
        val line = lines[i]
        if (!line.isInstrumental && line.agent != null) return line.agent
    }
    return null
}

fun insertInstrumentalBreaks(
    lines: List<YumaLyricsLine>,
    minGapMs: Long = TimingTokens().minInstrumentalGapMs,
): List<YumaLyricsLine> {
    if (lines.isEmpty() || minGapMs <= 0L) return lines
    val out = ArrayList<YumaLyricsLine>(lines.size + 4)

    val first = lines.first()
    if (!first.isInstrumental && first.timeMs >= minGapMs) {
        out.add(
            YumaLyricsLine(
                timeMs = 0L,
                endTimeMs = first.timeMs,
                text = "",
                isInstrumental = true,
            ),
        )
    }

    for (i in lines.indices) {
        val current = lines[i]
        out.add(current)
        val next = lines.getOrNull(i + 1) ?: continue
        if (!current.isInstrumental && !next.isInstrumental) {
            val gapMs = next.timeMs - current.endTimeMs
            if (gapMs >= minGapMs) {
                out.add(
                    YumaLyricsLine(
                        timeMs = current.endTimeMs,
                        endTimeMs = next.timeMs,
                        text = "",
                        isInstrumental = true,
                    ),
                )
            }
        }
    }
    return out
}
