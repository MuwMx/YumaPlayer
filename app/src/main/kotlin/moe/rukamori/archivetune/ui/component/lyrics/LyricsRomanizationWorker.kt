/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.Romanizer.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.Romanizer.providedRomanizedWordsForEntry
import moe.rukamori.archivetune.lyrics.Romanizer.romanizeLyricsLine
import moe.rukamori.archivetune.lyrics.Romanizer.romanizeLyricsWordWithLineContext
import moe.rukamori.archivetune.lyrics.Romanizer.shouldRomanizeLyricsLine
import moe.rukamori.archivetune.utils.reportException

@Composable
internal fun LyricsRomanizationEffect(
    lyricsEntries: List<LyricsEntry>,
    isTtmlFormat: Boolean,
    romanizationPreferences: LyricsRomanizationPreferences,
    isReadyToParse: Boolean,
    onEnrichedLyrics: (SyncedLyrics) -> Unit,
) {
    val latestOnEnrichedLyrics = rememberUpdatedState(onEnrichedLyrics)
    var appliedRomanizationKey by remember(lyricsEntries, isTtmlFormat) {
        mutableStateOf<Any?>(null)
    }

    LaunchedEffect(lyricsEntries, isTtmlFormat, romanizationPreferences, isReadyToParse) {
        if (!isReadyToParse) return@LaunchedEffect
        if (!romanizationPreferences.isEnabled) return@LaunchedEffect
        if (lyricsEntries.isEmpty()) return@LaunchedEffect
        val romanizationKey = Triple(lyricsEntries, isTtmlFormat, romanizationPreferences)
        if (romanizationKey == appliedRomanizationKey) return@LaunchedEffect

        val enriched =
            withContext(Dispatchers.Default) {
                val boundedEntries =
                    if (lyricsEntries.size > MAX_SYNCED_LYRICS_LINES) lyricsEntries.take(MAX_SYNCED_LYRICS_LINES) else lyricsEntries
                val toRomanize =
                    boundedEntries.mapIndexedNotNull { index, entry ->
                        val hasProviderRomanization =
                            providedRomanizedTextForEntry(entry, romanizationPreferences) != null
                        if (hasProviderRomanization || shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                            index to entry
                        } else {
                            null
                        }
                    }
                if (toRomanize.isEmpty()) return@withContext null

                val jobs =
                    toRomanize.map { (index, entry) ->
                        async {
                            val romanized: List<String?> =
                                try {
                                    if (isTtmlFormat && entry.words != null) {
                                        val mainWords = entry.words.filter { !it.isBackground }.let {
                                            if (it.size > MAX_SYLLABLES_PER_LINE) it.take(MAX_SYLLABLES_PER_LINE) else it
                                        }
                                        val mainWordCount = mainWords.size
                                        providedRomanizedWordsForEntry(entry, mainWordCount, romanizationPreferences)
                                            ?: mainWords.map { word ->
                                                romanizeLyricsWordWithLineContext(word.text, entry.text, romanizationPreferences)
                                            }
                                    } else {
                                        listOf(
                                            providedRomanizedTextForEntry(entry, romanizationPreferences)
                                                ?: romanizeLyricsLine(entry.text, romanizationPreferences),
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    reportException(e)
                                    if (isTtmlFormat && entry.words != null) {
                                        val count = entry.words.count { !it.isBackground }.coerceAtMost(MAX_SYLLABLES_PER_LINE)
                                        List(count) { null }
                                    } else {
                                        listOf(null)
                                    }
                                }
                            index to romanized
                        }
                    }
                val tempMap = mutableMapOf<Int, List<String?>>()
                jobs.awaitAll().forEach { (index, romanized) ->
                    tempMap[index] = romanized
                }
                buildSyncedLyrics(boundedEntries, isTtmlFormat, tempMap)
            } ?: return@LaunchedEffect

        appliedRomanizationKey = romanizationKey
        latestOnEnrichedLyrics.value(enriched)
    }
}
