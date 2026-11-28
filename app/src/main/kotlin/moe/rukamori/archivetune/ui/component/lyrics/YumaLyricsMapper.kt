/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import kotlin.math.roundToLong
import moe.rukamori.archivetune.lyrics.language.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.language.Romanizer.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.language.Romanizer.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.model.LyricsEntry

internal fun List<LyricsEntry>.toYumaLyricsDocument(
    romanizationPreferences: LyricsRomanizationPreferences,
    romanizedLines: Map<Int, List<String?>> = emptyMap(),
    songwriters: List<String> = emptyList(),
): YumaLyricsDocument {
    if (isEmpty()) return YumaLyricsDocument(lines = emptyList(), songwriters = songwriters)

    val bounded = if (size > MAX_SYNCED_LYRICS_LINES) take(MAX_SYNCED_LYRICS_LINES) else this
    val mappedLines = ArrayList<YumaLyricsLine>(bounded.size)
    var hasWordSync = false

    bounded.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (!entry.isInstrumental && entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        val words =
            entry.words
                ?.take(MAX_SYLLABLES_PER_LINE)
                ?.map { word ->
                    YumaLyricsWord(
                        text = if (word.text.length > 100) word.text.take(100) else word.text,
                        startMs = (word.startTime * 1000.0).roundToLong().coerceAtLeast(0L),
                        endMs = (word.endTime * 1000.0).roundToLong().coerceAtLeast(0L),
                        isBackground = word.isBackground,
                    )
                }.orEmpty()

        if (words.isNotEmpty()) {
            hasWordSync = true
        }

        val nextTimeMs = bounded.getOrNull(index + 1)?.time
        val endTimeMs =
            when {
                words.isNotEmpty() -> words.last().endMs.coerceAtLeast(entry.time + 1L)
                entry.durationMs > 0L -> entry.time + entry.durationMs
                nextTimeMs != null && nextTimeMs > entry.time -> (nextTimeMs - 1L).coerceAtLeast(entry.time + 1L)
                else -> entry.time + 4000L
            }

        val romanizedFromWorker =
            romanizedLines[index]
                ?.filterNotNull()
                ?.joinToString(" ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        val romanizedText =
            romanizedFromWorker
                ?: entry.romanizedTextFlow.value?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (romanizationPreferences.isEnabled) {
                    providedRomanizedTextForEntry(entry, romanizationPreferences)
                } else {
                    null
                }

        val boundedText =
            if (entry.text.length > MAX_LINE_CHARACTERS) entry.text.take(MAX_LINE_CHARACTERS) else entry.text

        mappedLines.add(
            YumaLyricsLine(
                timeMs = entry.time,
                endTimeMs = endTimeMs,
                text = boundedText,
                words = words,
                agent = entry.agent,
                isInstrumental = entry.isInstrumental,
                romanizedText = romanizedText,
                translationText = providedTranslationTextForEntry(entry),
            ),
        )
    }

    return YumaLyricsDocument(
        lines = insertInstrumentalBreaks(mappedLines),
        songwriters = songwriters,
        isWordSynced = hasWordSync,
    )
}
