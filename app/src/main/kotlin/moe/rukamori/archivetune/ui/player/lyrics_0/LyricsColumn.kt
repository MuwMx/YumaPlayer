package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
fun LyricsColumn(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    progressMsProvider: () -> Long,
    onCloseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    onAction: (PlayerAction) -> Unit,
    onLineClick: (Long) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit
) {
    val isReadyToParse by remember(animateProgressProvider) {
        derivedStateOf { animateProgressProvider() > 0.99f }
    }

    // state.isLyricsVisible is user intent and outlives the sheet closing, so gating
    // idle work on it keeps the seek bar and the scroll loop running while parked.
    val isSheetActive by remember(animateProgressProvider) {
        derivedStateOf { animateProgressProvider() > 0.05f }
    }

    key(state.isLyricsVisible) {
        BackHandler(enabled = state.isLyricsVisible) {
            onCloseClick()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LyricsContentCard(
            state = state,
            animateProgressProvider = animateProgressProvider,
            progressMsProvider = progressMsProvider,
            onSearchClick = onSearchClick,
            lazyListState = lazyListState,
            onLineClick = onLineClick,
            onAction = onAction,
            onSeek = onSeek,
            onSeekStarted = onSeekStarted,
            isReadyToParse = isReadyToParse,
            isVisible = isSheetActive,
        )
    }
}
