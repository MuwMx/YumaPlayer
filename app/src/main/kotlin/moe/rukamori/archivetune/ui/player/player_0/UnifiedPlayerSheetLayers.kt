package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.FullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
internal fun UnifiedPlayerSheetLayers(
    state: PlayerUiState,
    queueState: QueueUiState,
    updateState: UpdateState,
    expansionFractionProvider: () -> Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    progressMsProvider: () -> Long,
    fullPlayerVisualState: FullPlayerVisualState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onCloseQueueClick: () -> Unit = {},
    onMoreQueueClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onMoreLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    dragHandler: SheetVerticalDragGestureHandler? = null,
) {
    val density = LocalDensity.current.density
    val canvasState = rememberPlayerCanvasState(state.trackUrl, state.title, state.artist)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val expansion = expansionFractionProvider()
                    val isTranslucent = state.isBlurBackgroundEnabled || state.isImmersiveEnabled
                    val overlayFraction = if (isTranslucent) 0f else maxOf(lyricsFractionProvider(), queueFractionProvider())
                    alpha = (expansion * (1f - overlayFraction)).coerceIn(0f, 1f)
                }
        ) {
            PlayerBackgroundLayers(
                state = state,
                canvasState = canvasState,
                expansionFractionProvider = expansionFractionProvider,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                onColorsExtracted = { vibrant, darkMuted, gradient ->
                    onAction(PlayerAction.UpdateColors(vibrant, darkMuted, gradient))
                },
            )
        }

        val isMiniPlayerVisible by remember {
            derivedStateOf { expansionFractionProvider() < 0.05f }
        }

        val showMiniPlayer by remember {
            derivedStateOf { expansionFractionProvider() < 1f }
        }

        if (showMiniPlayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = expansionFractionProvider()
                        alpha = (1f - (fraction / 0.3f)).coerceIn(0f, 1f)
                    }
            ) {
                MiniPlayerContentInternal(
                    state = state,
                    expansionFractionProvider = expansionFractionProvider,
                    progressMsProvider = progressMsProvider,
                    onAction = onAction,
                    onMediaAreaClick = onExpandClick,
                    isVisible = isMiniPlayerVisible
                )
            }
        }

        val hasTrack by remember(state.title) {
            derivedStateOf { state.title.isNotEmpty() }
        }

        if (hasTrack) {
            val isFullPlayerMounted by remember {
                derivedStateOf { expansionFractionProvider() > 0.005f }
            }

            val isFullPlayerVisible by remember {
                derivedStateOf {
                    expansionFractionProvider() > 0.005f && maxOf(lyricsFractionProvider(), queueFractionProvider()) < 1f
                }
            }

            if (isFullPlayerMounted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val maxFraction = maxOf(lyricsFractionProvider(), queueFractionProvider())
                            val expansionFraction = expansionFractionProvider()
                            alpha = expansionFraction.coerceIn(0f, 1f) * (1f - maxFraction)
                            translationY = (1f - expansionFraction) * (150f * density) - (200f * density * maxFraction)
                        }
                ) {
                    FullPlayer(
                        state = state,
                        canvasState = canvasState,
                        progressMsProvider = progressMsProvider,
                        updateState = updateState,
                        slideOffset = expansionFractionProvider,
                        density = density,
                        onCollapseClick = onCollapseClick,
                        onAction = onAction,
                        onSeek = onSeek,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onImmersiveChanged = onImmersiveChanged,
                        onOpenSettingsMenu = onOpenSettingsMenu,
                        onOpenQueue = onOpenQueue,
                        onSeekStarted = onSeekStarted,
                        lyricsFractionProvider = lyricsFractionProvider,
                        queueFractionProvider = queueFractionProvider,
                        isVisible = isFullPlayerVisible
                    )
                }
            }

            val isLyricsMounted by remember {
                derivedStateOf { lyricsFractionProvider() > 0.001f }
            }

            if (isLyricsMounted) {
                PlayerLyricsLayer(
                    state = state,
                    lyricsFractionProvider = lyricsFractionProvider,
                    progressMsProvider = progressMsProvider,
                    onAction = onAction,
                    onCloseLyricsClick = onCloseLyricsClick,
                    onMoreLyricsClick = onMoreLyricsClick,
                    onSearchLyricsClick = onSearchLyricsClick,
                    onSeek = onSeek,
                    onSeekStarted = onSeekStarted,
                    dragHandler = dragHandler,
                )
            }

            val isQueueMounted by remember {
                derivedStateOf { queueFractionProvider() > 0.001f }
            }

            if (isQueueMounted) {
                PlayerQueueLayer(
                    state = state,
                    queueState = queueState,
                    queueFractionProvider = queueFractionProvider,
                    onAction = onAction,
                    onCloseQueueClick = onCloseQueueClick,
                    onMoreQueueClick = onMoreQueueClick,
                    dragHandler = dragHandler,
                )
            }
        }
    }
}
