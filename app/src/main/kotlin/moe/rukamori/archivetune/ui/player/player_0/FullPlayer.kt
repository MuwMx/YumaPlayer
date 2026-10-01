package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerToolbar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
fun FullPlayer(
    state: PlayerUiState,
    progressMsProvider: () -> Long = { 0L },
    slideOffset: () -> Float,
    density: Float,
    onCollapseClick: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    updateState: UpdateState,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onOpenQueue: () -> Unit = {},
    lyricsFractionProvider: () -> Float = { if (state.isLyricsVisible) 1f else 0f },
    queueFractionProvider: () -> Float = { 0f },
    isVisible: Boolean = true,
) {
    val motionState = rememberFullPlayerMotionState(
        state = state,
        slideOffset = slideOffset,
        lyricsFractionProvider = lyricsFractionProvider,
        queueFractionProvider = queueFractionProvider,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        PlayerLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val overlayFraction = maxOf(lyricsFractionProvider(), queueFractionProvider())
                    alpha = (1f - overlayFraction).coerceIn(0f, 1f)
                },
            toolbar = {
                PlayerToolbar(
                    state = state,
                    onCollapseClick = onCollapseClick,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
                    onTimerBadgeClick = { onOpenSettingsMenu(PlayerMenuScreen.SLEEP_TIMER) },
                    hasUpdate = updateState is UpdateState.SoftUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                )
            },
            cover = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp)
                        .graphicsLayer {
                            val isCoverVisible = !state.isImmersiveEnabled && !motionState.isOverlayVisible
                            alpha = if (isCoverVisible) 1f else 0f
                        }
                ) {
                    PlayerCoverCard(
                        coverUrl = state.coverUrl,
                        placeholderResId = state.placeholderResId,
                        isAlbumCoverGlowEnabled = state.isAlbumCoverGlowEnabled,
                        vibrantColor = Color(state.vibrantColor),
                        gestureEnabled = motionState.coverGestureEnabled,
                        mediaId = state.trackUrl,
                        songTitle = state.title,
                        artistName = state.artist,
                        isPlaying = motionState.canPlayCanvas,
                        onNext = { onAction(PlayerAction.Next) },
                        onPrevious = { onAction(PlayerAction.Previous) }
                    )
                }
            },
            controls = {
                FullPlayerControlsGroup(
                    state = state,
                    progressMsProvider = progressMsProvider,
                    slideOffset = slideOffset,
                    controlsOffsetY = { motionState.controlsOffsetY },
                    onAction = onAction,
                    onSeek = onSeek,
                    onSeekStarted = onSeekStarted,
                    onOpenSettingsMenu = onOpenSettingsMenu,
                    onOpenQueue = onOpenQueue,
                    isVisible = isVisible
                )
            }
        )
    }
}
