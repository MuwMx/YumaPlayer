/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlin.math.roundToInt
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.Romanizer.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.ui.component.toLyricsWrappingUnits

internal const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1
internal const val MAX_SYNCED_LYRICS_LINES = 800
internal const val MAX_LINE_CHARACTERS = 600
internal const val MAX_SYLLABLES_PER_LINE = 120
internal const val MAX_TOTAL_SYLLABLES_PER_TRACK = 5000

internal fun List<WordTimestamp>.toKaraokeSyllables(phonetics: List<String?>): List<KaraokeSyllable> {
    val bounded = if (size > MAX_SYLLABLES_PER_LINE) take(MAX_SYLLABLES_PER_LINE) else this
    return bounded.mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val nextStart = bounded.getOrNull(index + 1)?.startTime?.toMilliseconds()
        val rawEnd = word.endTime.toMilliseconds()
        val end =
            nextStart
                ?.let { minOf(rawEnd, it) }
                ?: rawEnd

        KaraokeSyllable(
            content = if (word.text.length > 100) word.text.take(100) else word.text,
            start = start,
            end = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phonetics.getOrNull(index),
        )
    }
}

internal fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

internal fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    isTtml: Boolean,
    romanizationMap: Map<Int, List<String?>>,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val safeEntries = if (entries.size > MAX_SYNCED_LYRICS_LINES) entries.take(MAX_SYNCED_LYRICS_LINES) else entries
    val lines = mutableListOf<ISyncedLine>()
    var totalSyllableCount = 0

    safeEntries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        val boundedText = if (entry.text.length > MAX_LINE_CHARACTERS) entry.text.take(MAX_LINE_CHARACTERS) else entry.text

        if (isTtml && entry.words != null) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words.filter { !it.isBackground }.let {
                if (it.size > MAX_SYLLABLES_PER_LINE) it.take(MAX_SYLLABLES_PER_LINE) else it
            }
            val bgWords = entry.words.filter { it.isBackground }.let {
                if (it.size > MAX_SYLLABLES_PER_LINE) it.take(MAX_SYLLABLES_PER_LINE) else it
            }
            val alignment =
                when (entry.agent?.lowercase()) {
                    "v2" -> KaraokeAlignment.End
                    else -> KaraokeAlignment.Start
                }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words.let {
                if (it.size > MAX_SYLLABLES_PER_LINE) it.take(MAX_SYLLABLES_PER_LINE) else it
            }
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val mainSyllables = wordsForMain.toKaraokeSyllables(wordPhonetics)

            if (mainSyllables.isEmpty()) return@forEachIndexed
            val lineStart = mainSyllables.first().start
            val lineEnd = mainSyllables.last().end
            if (lineEnd <= lineStart) return@forEachIndexed

            if (totalSyllableCount + mainSyllables.size > MAX_TOTAL_SYLLABLES_PER_TRACK) {
                lines.add(
                    SyncedLine(
                        content = boundedText.ifBlank { mainSyllables.joinToString("") { it.content } },
                        translation = translation,
                        start = lineStart,
                        end = lineEnd,
                    ),
                )
                return@forEachIndexed
            }
            totalSyllableCount += mainSyllables.size

            val accompanimentLines =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    val bgSyllables = bgWords.toKaraokeSyllables(emptyList())
                    if (bgSyllables.isNotEmpty()) {
                        val bgStart = bgSyllables.first().start
                        val bgEnd = bgSyllables.last().end
                        if (bgEnd > bgStart) {
                            listOf(
                                KaraokeLine.AccompanimentKaraokeLine(
                                    syllables = bgSyllables,
                                    translation = null,
                                    alignment = alignment,
                                    start = bgStart,
                                    end = bgEnd,
                                    phonetic = null,
                                ),
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = translation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = null,
                    accompanimentLines = accompanimentLines,
                ),
            )
        } else {
            val nextEntry = safeEntries.getOrNull(index + 1)
            val lineEnd =
                if (nextEntry != null && nextEntry.time > entry.time) {
                    val gap = nextEntry.time - entry.time
                    if (gap > 3000L) {
                        minOf((nextEntry.time - 1L).toInt(), (entry.time + 4000L).toInt())
                            .coerceAtLeast(entry.time.toInt() + 1)
                    } else {
                        (nextEntry.time - 1L).coerceAtLeast(entry.time + 1L).toInt()
                    }
                } else {
                    (entry.time + 4000L).toInt()
                }
            val line =
                buildLineSyncedLrcLine(
                    entry = entry.copy(text = boundedText),
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                    allowKaraoke = totalSyllableCount < MAX_TOTAL_SYLLABLES_PER_TRACK,
                )
            if (line is KaraokeLine) {
                totalSyllableCount += line.syllables.size
            }
            lines.add(line)
        }
    }

    return SyncedLyrics(lines = lines)
}

internal fun buildLineSyncedLrcLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
    allowKaraoke: Boolean = true,
): ISyncedLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedRomanizedText == null || !allowKaraoke) {
        return SyncedLine(
            content = entry.text,
            translation = translation,
            start = start,
            end = end,
        )
    }

    val syllables =
        buildWrappingKaraokeSyllables(
            content = entry.text,
            romanizedText = normalizedRomanizedText,
            start = start,
            end = end,
        )

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = translation,
        alignment = KaraokeAlignment.Start,
        start = start,
        end = end,
    )
}

internal fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val boundedContent = if (content.length > MAX_LINE_CHARACTERS) content.take(MAX_LINE_CHARACTERS) else content
    val rawUnits = boundedContent.toLyricsWrappingUnits().ifEmpty { listOf(boundedContent) }
    val contentUnits = if (rawUnits.size > MAX_SYLLABLES_PER_LINE) rawUnits.take(MAX_SYLLABLES_PER_LINE) else rawUnits
    val rawPhoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticWords = if (rawPhoneticWords.size > MAX_SYLLABLES_PER_LINE) rawPhoneticWords.take(MAX_SYLLABLES_PER_LINE) else rawPhoneticWords
    val phoneticAnchorIndices =
        contentUnits.indices.filter { index ->
            contentUnits[index].any(Char::isLetterOrDigit)
        }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty() && phoneticWords.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex.coerceIn(phoneticAnchorIndices.indices)]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}
