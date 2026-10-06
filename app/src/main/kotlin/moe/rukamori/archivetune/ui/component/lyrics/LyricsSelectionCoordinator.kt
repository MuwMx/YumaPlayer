/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.Romanizer.providedTranslationTextForEntry
import moe.rukamori.archivetune.models.MediaMetadata

@Stable
internal class LyricsSelectionState(
    val isSelectionModeActive: Boolean,
    val selectedLineKeySet: Set<String>,
    val selectionLines: List<LyricSelectionLine>,
    val plainLyrics: PlainLyrics,
    val toggleSelectedLine: (String) -> Unit,
    val dismissSelection: () -> Unit,
    val shareSelectedLyrics: () -> Unit,
    val onPlainLineClicked: (String) -> Unit,
    val onPlainLinePressed: (String) -> Unit,
    val onKaraokeLineClicked: (ISyncedLine) -> Unit,
    val onKaraokeLinePressed: (ISyncedLine) -> Unit,
)

@Composable
internal fun rememberLyricsSelectionState(
    context: Context,
    mediaMetadata: MediaMetadata?,
    lyricsEntries: List<LyricsEntry>,
    isSynced: Boolean,
    syncedLyrics: SyncedLyrics,
    showTranslations: Boolean,
    lyricsClick: Boolean,
    maxSelectionLimit: Int = 5,
    onSeekTo: (Long) -> Unit,
    onShareLyrics: (Triple<String, String, String>) -> Unit,
): LyricsSelectionState {
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedLineKeys = remember { mutableStateListOf<String>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }

    val plainLyrics =
        remember(lyricsEntries, isSynced, showTranslations) {
            PlainLyrics(
                items =
                    if (isSynced) {
                        emptyList()
                    } else {
                        val boundedEntries =
                            if (lyricsEntries.size > MAX_SYNCED_LYRICS_LINES) {
                                lyricsEntries.take(MAX_SYNCED_LYRICS_LINES)
                            } else {
                                lyricsEntries
                            }
                        boundedEntries.mapIndexedNotNull { index, entry ->
                            val text = entry.text.trim()
                            if (text.isBlank()) {
                                null
                            } else {
                                val boundedText = if (text.length > MAX_LINE_CHARACTERS) text.take(MAX_LINE_CHARACTERS) else text
                                val selectionId = "plain:$index:${boundedText.hashCode()}"
                                val translation = if (showTranslations) providedTranslationTextForEntry(entry) else null
                                PlainLyricLine(
                                    itemId = "$selectionId#$index",
                                    selectionId = selectionId,
                                    text = boundedText,
                                    translation = translation,
                                )
                            }
                        }
                    },
            )
        }

    val selectionLines =
        remember(isSynced, syncedLyrics, plainLyrics) {
            if (isSynced) {
                syncedLyrics.lines.mapIndexedNotNull { index, line ->
                    val text = line.lineText()
                    if (text.isBlank()) {
                        null
                    } else {
                        val selectionId = line.selectionKey(text)
                        LyricSelectionLine(itemId = "$selectionId#$index", selectionId = selectionId, text = text)
                    }
                }
            } else {
                plainLyrics.items.map { line ->
                    LyricSelectionLine(itemId = line.itemId, selectionId = line.selectionId, text = line.text)
                }
            }
        }

    val selectedLineKeySet by remember { derivedStateOf { selectedLineKeys.toSet() } }

    val dismissSelection =
        remember {
            {
                isSelectionModeActive = false
                selectedLineKeys.clear()
            }
        }

    val toggleSelectedLine: (String) -> Unit =
        remember {
            { lineKey ->
                if (selectedLineKeys.contains(lineKey)) {
                    selectedLineKeys.remove(lineKey)
                    if (selectedLineKeys.isEmpty()) isSelectionModeActive = false
                } else if (selectedLineKeys.size < maxSelectionLimit) {
                    selectedLineKeys.add(lineKey)
                } else {
                    showMaxSelectionToast = true
                }
            }
        }

    val shareSelectedLyrics: () -> Unit =
        remember(mediaMetadata, selectionLines, selectedLineKeySet) {
            {
                val metadata = mediaMetadata
                if (metadata != null) {
                    val selectedLyricsText =
                        selectionLines
                            .filter { line -> line.selectionId in selectedLineKeySet }
                            .joinToString("\n") { line -> line.text }
                    if (selectedLyricsText.isNotBlank()) {
                        onShareLyrics(
                            Triple(
                                selectedLyricsText,
                                metadata.title,
                                metadata.artists.joinToString { it.name },
                            ),
                        )
                    }
                }
                dismissSelection()
            }
        }

    val onPlainLineClicked: (String) -> Unit =
        remember {
            { lineKey -> if (isSelectionModeActive) toggleSelectedLine(lineKey) }
        }

    val onPlainLinePressed: (String) -> Unit =
        remember {
            { lineKey ->
                if (!isSelectionModeActive) {
                    isSelectionModeActive = true
                    if (!selectedLineKeys.contains(lineKey)) selectedLineKeys.add(lineKey)
                } else if (!selectedLineKeys.contains(lineKey)) {
                    toggleSelectedLine(lineKey)
                }
            }
        }

    val onKaraokeLineClicked: (ISyncedLine) -> Unit =
        remember(lyricsClick, isSynced) {
            { line ->
                if (isSelectionModeActive) {
                    toggleSelectedLine(line.selectionKey())
                } else if (lyricsClick && isSynced && line.start > 0) {
                    onSeekTo(line.start.toLong())
                }
            }
        }

    val onKaraokeLinePressed: (ISyncedLine) -> Unit =
        remember {
            { line ->
                val lineKey = line.selectionKey()
                if (!isSelectionModeActive) {
                    isSelectionModeActive = true
                    if (!selectedLineKeys.contains(lineKey)) selectedLineKeys.add(lineKey)
                } else if (!selectedLineKeys.contains(lineKey)) {
                    toggleSelectedLine(lineKey)
                }
            }
        }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(
                context,
                context.getString(R.string.max_selection_limit, maxSelectionLimit),
                Toast.LENGTH_SHORT,
            ).show()
            showMaxSelectionToast = false
        }
    }

    return remember(
        isSelectionModeActive,
        selectedLineKeySet,
        selectionLines,
        plainLyrics,
        toggleSelectedLine,
        dismissSelection,
        shareSelectedLyrics,
        onPlainLineClicked,
        onPlainLinePressed,
        onKaraokeLineClicked,
        onKaraokeLinePressed,
    ) {
        LyricsSelectionState(
            isSelectionModeActive = isSelectionModeActive,
            selectedLineKeySet = selectedLineKeySet,
            selectionLines = selectionLines,
            plainLyrics = plainLyrics,
            toggleSelectedLine = toggleSelectedLine,
            dismissSelection = dismissSelection,
            shareSelectedLyrics = shareSelectedLyrics,
            onPlainLineClicked = onPlainLineClicked,
            onPlainLinePressed = onPlainLinePressed,
            onKaraokeLineClicked = onKaraokeLineClicked,
            onKaraokeLinePressed = onKaraokeLinePressed,
        )
    }
}
