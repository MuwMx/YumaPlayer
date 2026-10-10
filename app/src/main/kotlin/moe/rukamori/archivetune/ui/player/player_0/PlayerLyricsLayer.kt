package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsColumn
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsHeader
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.ActiveDragSheet
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
internal fun PlayerLyricsLayer(
    state: PlayerUiState,
    lyricsFractionProvider: () -> Float,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onMoreLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandler: SheetVerticalDragGestureHandler? = null,
) {
    val haptics = rememberYumaHaptics()

    val lyricsListState = rememberLazyListState()
    val canDragLyrics by remember(lyricsListState) {
        derivedStateOf {
            lyricsFractionProvider() > 0.05f &&
                    lyricsListState.firstVisibleItemIndex == 0 &&
                    lyricsListState.firstVisibleItemScrollOffset == 0
        }
    }
    val lyricsNestedScrollConnection = remember(dragHandler) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragLyrics },
            targetSheet = ActiveDragSheet.LYRICS
        )
    }

    val lyricsBackgroundColor = remember(state.darkMutedColor, state.isBlurBackgroundEnabled) {
        if (state.isBlurBackgroundEnabled) {
            Color(0xFF0F0F0F)
        } else {
            Color(state.darkMutedColor)
        }
    }

    PlayerOverlaySheet(
        fractionProvider = lyricsFractionProvider,
        backgroundColor = lyricsBackgroundColor,
        modifier = modifier,
        showDragHandle = false,
        dragHandler = dragHandler,
        headerContent = {
            val isHeaderVisible by remember {
                derivedStateOf { lyricsFractionProvider() > 0.05f }
            }

            if (isHeaderVisible) {
                LyricsHeader(
                    state = state,
                    animateProgressProvider = lyricsFractionProvider,
                    onCloseClick = onCloseLyricsClick,
                    onMoreClick = {
                        haptics.click()
                        onMoreLyricsClick()
                    },
                    isVisible = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (lyricsNestedScrollConnection != null) {
                        Modifier.nestedScroll(lyricsNestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
        ) {
            LyricsColumn(
                state = state,
                animateProgressProvider = lyricsFractionProvider,
                playbackProgress = playbackProgress,
                onCloseClick = onCloseLyricsClick,
                onMoreClick = onMoreLyricsClick,
                onSearchClick = onSearchLyricsClick,
                lazyListState = lyricsListState,
                onAction = onAction,
                onLineClick = { timeMs -> onSeek(timeMs.toFloat()) },
                onSeek = onSeek,
                onSeekStarted = onSeekStarted,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
