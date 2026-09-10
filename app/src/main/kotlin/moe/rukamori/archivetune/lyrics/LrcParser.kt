/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import moe.rukamori.archivetune.db.entities.LyricsEntity

@Suppress("RegExpRedundantEscape")
object LrcParser {
    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}\](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    private const val NBSP = '\u00A0'
    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    fun isTtml(lyrics: String): Boolean {
        Log.d("SPLIT_STRESS", "LRC_DETECT isTtml len=${lyrics.length} thread=${Thread.currentThread().name}")
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun isLineSyncedLrc(lyrics: String): Boolean {
        Log.d("SPLIT_STRESS", "isLineSyncedLrc lines=${lyrics.lines().size} thread=${Thread.currentThread().name}")
        return lyrics.lineSequence().any { line ->
            val trimmedLine = line.trim()
            LINE_REGEX.matches(trimmedLine) || YRC_LINE_REGEX.matches(trimmedLine)
        }
    }

    fun parseTtml(
        lyrics: String,
        durationSeconds: Int? = null,
    ): List<LyricsEntry> {
        val t0 = SystemClock.elapsedRealtime()
        Log.d("SPLIT_STRESS", "parseTtml entry len=${lyrics.length} dur=$durationSeconds thread=${Thread.currentThread().name}")
        val parsedLines = TTMLParser.parseTTML(normalizeLyricsText(lyrics))
        if (parsedLines.isEmpty()) {
            Log.d("SPLIT_STRESS", "parseTtml exit lines=0 dt=${SystemClock.elapsedRealtime() - t0}ms")
            return emptyList()
        }
        val scale = 1.0

        val result = parsedLines
            .map { line ->
                val words =
                    line.words
                        .filter { it.text.isNotEmpty() }
                        .map { word ->
                            WordTimestamp(
                                text = word.text,
                                startTime = word.startTime * scale,
                                endTime = word.endTime * scale,
                                isBackground = word.isBackground,
                            )
                        }.takeIf { it.isNotEmpty() }

                LyricsEntry(
                    time = (line.startTime * scale * 1000.0).toLong(),
                    text = line.text,
                    words = words,
                    agent = line.agent,
                    providerRomanizedText = line.providerRomanizedText,
                    providerRomanizedWords = line.providerRomanizedWords,
                    providerRomanizedLanguage = line.providerRomanizedLanguage,
                    providerTranslationText = line.providerTranslationText,
                )
            }.sorted()
        Log.d("SPLIT_STRESS", "parseTtml exit lines=${result.size} dt=${SystemClock.elapsedRealtime() - t0}ms")
        return result
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val t0 = SystemClock.elapsedRealtime()
        Log.d("SPLIT_STRESS", "parseLyrics entry len=${lyrics.length} thread=${Thread.currentThread().name}")
        val lines = lyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        val merged = mergeLineSyncedTranslations(result).sorted()
        Log.d("SPLIT_STRESS", "parseLyrics exit lines=${merged.size} dt=${SystemClock.elapsedRealtime() - t0}ms")
        return merged
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == NBSP }

        val unwrapped = stripCodeFence(raw)
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }

        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    fun displayLyricsText(lyrics: String): String {
        val raw = normalizeLyricsText(lyrics)
        if (raw.isEmpty() || raw == LyricsEntity.LYRICS_NOT_FOUND) return ""

        val visibleLines =
            when {
                isTtml(raw) -> runCatching { parseTtml(raw).map { it.text } }.getOrElse { emptyList() }
                isLineSyncedLrc(raw) -> runCatching { parseLyrics(raw).map { it.text } }.getOrElse { emptyList() }
                raw.startsWith("<") -> emptyList()
                else -> raw.lines().map(::cleanInlineWordTimingText)
            }

        return visibleLines
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
            }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun hasMeaningfulLyricsContent(lyrics: String): Boolean = displayLyricsText(lyrics).isNotEmpty()

    fun lyricsOrNotFound(lyrics: String): String {
        val normalized = normalizeLyricsText(lyrics)
        return normalized.takeIf(::hasMeaningfulLyricsContent) ?: LyricsEntity.LYRICS_NOT_FOUND
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics

        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics

        val bodyLines =
            lines.drop(1).let { remainingLines ->
                if (remainingLines.lastOrNull()?.trim() == "```") {
                    remainingLines.dropLast(1)
                } else {
                    remainingLines
                }
            }

        return bodyLines.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    fun insertInstrumentalBreaks(
        entries: List<LyricsEntry>,
        songDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        Log.d("SPLIT_STRESS", "insertInstrumentalBreaks entry entries=${entries.size} songDurationMs=$songDurationMs thread=${Thread.currentThread().name}")
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()
        insertIntroInstrumentalIfNeeded(entries, result)
        result.addAll(entries)
        insertOutroInstrumentalIfNeeded(entries, songDurationMs, result)
        return result
    }

    private fun insertIntroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        result: MutableList<LyricsEntry>,
    ) {
        val firstTimedVocalEntry = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() } ?: return
        val introGapMs = firstTimedVocalEntry.time - INSTRUMENTAL_INTRO_START_MS
        if (introGapMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        Log.d("SPLIT_STRESS", "insertInstrumentalBreaks intro start=$INSTRUMENTAL_INTRO_START_MS dur=$introGapMs")
        result.add(
            LyricsEntry(
                time = INSTRUMENTAL_INTRO_START_MS,
                text = "",
                isInstrumental = true,
                durationMs = introGapMs,
            ),
        )
    }

    private fun insertOutroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        songDurationMs: Long,
        result: MutableList<LyricsEntry>,
    ) {
        if (songDurationMs <= 0L) return
        val lastVocalEntry = entries.lastOrNull { it.text.isNotBlank() } ?: return
        val outroStartMs = lastVocalEntry.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
        val outroDurationMs = songDurationMs - outroStartMs
        if (outroDurationMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        Log.d("SPLIT_STRESS", "insertInstrumentalBreaks outro start=$outroStartMs dur=$outroDurationMs")
        result.add(
            LyricsEntry(
                time = outroStartMs,
                text = "",
                isInstrumental = true,
                durationMs = outroDurationMs,
            ),
        )
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = cleanInlineWordTimingText(matchResult.groupValues[3])
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLongOrNull() ?: 0L
                when (milString.length) {
                    1 -> mil *= 100
                    2 -> mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = matchResult.groupValues[1].toLongOrNull() ?: return null
        val text = cleanInlineWordTimingText(matchResult.groupValues[2])
        return listOf(LyricsEntry(time, text))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val mergedByTime = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = mergedByTime[entry.time]
            if (existing == null) {
                mergedByTime[entry.time] = entry
                return@forEach
            }

            val translatedText =
                entry.text
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }

            if (translatedText != null && existing.providerTranslationText == null) {
                mergedByTime[entry.time] = existing.copy(providerTranslationText = translatedText)
            }
        }
        return mergedByTime.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String =
        text
            .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
            .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
            .replace(YRC_WORD_TIME_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim { it.isWhitespace() || it == NBSP }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return high.coerceIn(0, lines.lastIndex)
    }
}
