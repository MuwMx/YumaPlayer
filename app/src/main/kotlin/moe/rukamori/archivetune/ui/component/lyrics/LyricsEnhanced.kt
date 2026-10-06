/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component.lyrics

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.LrcParser.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LrcParser.isTtml
import moe.rukamori.archivetune.lyrics.LrcParser.parseLyrics
import moe.rukamori.archivetune.lyrics.LrcParser.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.ui.component.shareLyricsAsText

@Composable
fun LyricsEnhanced(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    isReadyToParse: Boolean = true,
    isLyricsVisible: Boolean = true,
    lazyListState: LazyListState? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val animationsDisabled = LocalAnimationsDisabled.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackParameters by playerConnection.playbackParameters.collectAsState()

    val styleState = rememberLyricsStyleState(
        textColorOverride = textColorOverride,
        lyricsLineBlurOverride = lyricsLineBlurOverride,
    )

    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics =
        remember(currentLyrics, mediaMetadata?.id) {
            currentLyrics
                ?.takeIf { lyricsEntity -> lyricsEntity.id == mediaMetadata?.id }
                ?.lyrics
        }
    val showTranslations =
        remember(currentLyrics?.source) {
            currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value
        }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, lyrics) {
            mediaMetadata?.id.orEmpty() to lyrics
        }

    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics) || isTtml(lyrics)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics) }

    val lyricsEntries: List<LyricsEntry> by produceState(initialValue = emptyList(), key1 = lyrics) {
        val raw = lyrics
        if (raw == null || raw == LYRICS_NOT_FOUND) {
            value = emptyList()
        } else {
            value =
                withContext(Dispatchers.Default) {
                    when {
                        isTtml(raw) -> parseTtml(raw)
                        isLineSyncedLrc(raw) -> parseLyrics(raw)
                        else -> raw.lines().filter { it.isNotBlank() }.map { LyricsEntry(time = -1L, text = it.trim()) }
                    }
                }
        }
    }

    var syncedLyrics by remember(lyricsSessionKey) { mutableStateOf(SyncedLyrics(emptyList())) }
    var syncedLyricsRenderVersion by remember(lyricsSessionKey) { mutableIntStateOf(0) }

    LaunchedEffect(lyricsEntries, isTtmlFormat) {
        syncedLyrics = if (lyricsEntries.isEmpty()) {
            SyncedLyrics(emptyList())
        } else {
            withContext(Dispatchers.Default) { buildSyncedLyrics(lyricsEntries, isTtmlFormat, emptyMap()) }
        }
    }

    LyricsRomanizationEffect(
        lyricsEntries = lyricsEntries,
        isTtmlFormat = isTtmlFormat,
        romanizationPreferences = styleState.romanizationPreferences,
        isReadyToParse = isReadyToParse,
        onEnrichedLyrics = { enriched ->
            syncedLyrics = enriched
            syncedLyricsRenderVersion += 1
        },
    )

    val defaultListState = key(lyricsSessionKey) { rememberLazyListState() }
    val listState = lazyListState ?: defaultListState

    val timingState =
        rememberLyricsTimingState(
            player = player,
            lyricsSessionKey = lyricsSessionKey,
            syncedLyrics = syncedLyrics,
            isTtmlFormat = isTtmlFormat,
            animationsDisabled = animationsDisabled,
            playbackSpeed = playbackParameters.speed,
            isReadyToParse = isReadyToParse,
            isLyricsVisible = isLyricsVisible,
            sliderPositionProvider = sliderPositionProvider,
            lyricsSyncOffset = lyricsSyncOffset,
            listState = listState,
            isSelectionModeActive = false,
        )

    val selectionState =
        rememberLyricsSelectionState(
            context = context,
            mediaMetadata = mediaMetadata,
            lyricsEntries = lyricsEntries,
            isSynced = isSynced,
            syncedLyrics = syncedLyrics,
            showTranslations = showTranslations,
            lyricsClick = styleState.lyricsClick,
            onSeekTo = timingState.seekTo,
            onShareLyrics = { data ->
                shareDialogData = data
                showShareDialog = true
            },
        )

    LyricsFocusAutoScrollEffect(
        listState = listState,
        syncedLyrics = syncedLyrics,
        lyricsSessionKey = lyricsSessionKey,
        isSynced = isSynced,
        isTtmlFormat = isTtmlFormat,
        isReadyToParse = isReadyToParse,
        isLyricsVisible = isLyricsVisible,
        isManualScrolling = timingState.isManualScrolling,
        isSelectionModeActive = selectionState.isSelectionModeActive,
        lineFocusPosition = timingState.lineFocusPosition,
    )

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LyricsDisplayView(
        lyrics = lyrics,
        isSynced = isSynced,
        syncedLyrics = syncedLyrics,
        plainLyrics = selectionState.plainLyrics,
        listState = listState,
        selectedLineKeySet = selectionState.selectedLineKeySet,
        textColor = styleState.textColor,
        normalTextStyle = styleState.normalTextStyle,
        accompanimentTextStyle = styleState.accompanimentTextStyle,
        phoneticTextStyle = styleState.phoneticTextStyle,
        lyricsLineBlur = styleState.lyricsLineBlur,
        isReadyToParse = isReadyToParse,
        showTranslations = showTranslations,
        romanizationPreferences = styleState.romanizationPreferences,
        lyricsSessionKey = lyricsSessionKey,
        syncedLyricsRenderVersion = syncedLyricsRenderVersion,
        lyricsTextSize = styleState.lyricsTextSize,
        lyricsLineSpacing = styleState.lyricsLineSpacing,
        timingState = timingState,
        onPlainLineClicked = selectionState.onPlainLineClicked,
        onPlainLinePressed = selectionState.onPlainLinePressed,
        onKaraokeLineClicked = selectionState.onKaraokeLineClicked,
        onKaraokeLinePressed = selectionState.onKaraokeLinePressed,
        modifier = modifier,
    )

    if (selectionState.isSelectionModeActive && selectionState.selectionLines.isNotEmpty()) {
        LyricsSelectionBottomSheet(
            lines = selectionState.selectionLines,
            selectedLineKeys = selectionState.selectedLineKeySet,
            onToggleLine = selectionState.toggleSelectedLine,
            onDismissRequest = selectionState.dismissSelection,
            onShareSelected = selectionState.shareSelectedLyrics,
        )
    }

    LyricsShareChoiceDialog(
        showShareDialog = showShareDialog,
        onDismissShareDialog = { showShareDialog = false },
        showShareImageDialog = showShareImageDialog,
        onDismissShareImageDialog = { showShareImageDialog = false },
        shareDialogData = shareDialogData,
        mediaMetadata = mediaMetadata,
        onShareText = { payload ->
            shareLyricsAsText(
                context = context,
                payload = payload,
                songId = mediaMetadata?.id,
            )
        },
        onOpenShareImage = { data ->
            shareDialogData = data
            showShareImageDialog = true
        },
    )
}
