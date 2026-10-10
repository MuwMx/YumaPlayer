package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerToolbar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
fun FullPlayer(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    canvasState: PlayerCanvasState = rememberPlayerCanvasState(state.trackUrl, state.title, state.artist),
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

    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat().takeIf { it > 0f }
        ?: (LocalConfiguration.current.screenWidthDp * density)
    val insetsTopPx = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value * density
    val toolbarHPx = 56f * density
    val peekUpPx = 28f * density

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        PlayerLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val lyricsFraction = lyricsFractionProvider()
                    alpha = (1f - lyricsFraction).coerceIn(0f, 1f)
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
                        .zIndex(1f)
                        .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                        .graphicsLayer {
                            val queueFraction = queueFractionProvider().coerceIn(0f, 1f)
                            alpha = 1f - normalizeFraction(queueFraction, 0.75f, 0.95f)
                        }
                )
            },
            cover = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp)
                        .graphicsLayer {
                            val queueFraction = queueFractionProvider().coerceIn(0f, 1f)
                            val peekProgress = normalizeFraction(queueFraction, 0f, 0.45f)
                            val expandProgress = normalizeFraction(queueFraction, 0.45f, 1f)

                            val targetScale = if (size.width > 0f) (screenWidthPx / size.width).coerceIn(1f, 1.6f) else 1f
                            val coverScale = 1f + (targetScale - 1f) * peekProgress
                            scaleX = coverScale
                            scaleY = coverScale

                            val expandDistancePx = size.height + toolbarHPx + insetsTopPx
                            translationY = -(peekUpPx * peekProgress) - (expandDistancePx * expandProgress)

                            val queueCoverFade = 1f - normalizeFraction(queueFraction, 0.45f, 0.80f)
                            val isCoverVisible = !state.isImmersiveEnabled && !motionState.isOverlayVisible
                            alpha = if (isCoverVisible) queueCoverFade else 0f
                        }
                        .drawWithContent {
                            drawContent()
                            val isCanvasPlaying = canvasState.isCanvasEnabled && canvasState.artwork != null
                            if (!state.isImmersiveEnabled && !isCanvasPlaying) {
                                val q = queueFractionProvider().coerceIn(0f, 1f)
                                val maskAlpha = normalizeFraction(q, 0f, 0.45f)
                                if (maskAlpha > 0f) {
                                    val topH = 72.dp.toPx()
                                    val bottomH = 96.dp.toPx()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.65f * maskAlpha), Color.Transparent),
                                            startY = 0f,
                                            endY = topH
                                        ),
                                        size = Size(size.width, topH)
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f * maskAlpha)),
                                            startY = size.height - bottomH,
                                            endY = size.height
                                        ),
                                        topLeft = Offset(0f, size.height - bottomH),
                                        size = Size(size.width, bottomH)
                                    )
                                }
                            }
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
                        canvasState = canvasState,
                        onNext = { onAction(PlayerAction.Next) },
                        onPrevious = { onAction(PlayerAction.Previous) }
                    )
                }
            },
            controls = {
                FullPlayerControlsGroup(
                    state = state,
                    playbackProgress = playbackProgress,
                    slideOffset = slideOffset,
                    controlsOffsetY = { motionState.controlsOffsetY },
                    queueFractionProvider = queueFractionProvider,
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

internal fun normalizeFraction(value: Float, start: Float, end: Float): Float {
    if (end <= start) return 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}
