package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

    val localDensity = LocalDensity.current
    val capsuleHeaderHeightPx = with(localDensity) { CapsuleDefaults.totalHeaderHeight().roundToPx().toFloat() }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var safeWidthPx by remember { mutableFloatStateOf(0f) }
    var toolbarTopInRoot by remember { mutableFloatStateOf(0f) }
    var toolbarBottomInRoot by remember { mutableFloatStateOf(0f) }
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardTopInRoot by remember { mutableFloatStateOf(0f) }

    val isToolbarInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.94f }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { rootHeightPx = it.height.toFloat() }
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PlayerLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { safeWidthPx = it.width.toFloat() }
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
                        enabled = isToolbarInteractive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                            .onGloballyPositioned { coords ->
                                val root = rootCoordinates
                                if (root != null && root.isAttached && coords.isAttached) {
                                    toolbarTopInRoot = root.localPositionOf(coords, Offset.Zero).y
                                    toolbarBottomInRoot = root.localPositionOf(coords, Offset(0f, coords.size.height.toFloat())).y
                                }
                            }
                            .graphicsLayer {
                                val queueFraction = queueFractionProvider().coerceIn(0f, 1f)
                                alpha = (1f - (queueFraction - 0.75f) / (0.95f - 0.75f)).coerceIn(0f, 1f)
                            }
                    )
                },
                cover = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp)
                            .onGloballyPositioned { slotCoords ->
                                val root = rootCoordinates
                                if (root != null && root.isAttached && slotCoords.isAttached) {
                                    cardTopInRoot = root.localPositionOf(slotCoords, Offset.Zero).y
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .onSizeChanged { size ->
                                    cardWidthPx = if (size.width > 0) size.width.toFloat() else 0f
                                }
                                .graphicsLayer {
                                    val q = queueFractionProvider().coerceIn(0f, 1f)
                                    val isCoverVisible = !state.isImmersiveEnabled && !motionState.isOverlayVisible
                                    if (!isCoverVisible) {
                                        alpha = 0f
                                        scaleX = 1f
                                        scaleY = 1f
                                        translationY = 0f
                                    } else {
                                        val w = safeWidthPx
                                        val a = cardWidthPx

                                        val targetScale = if (a > 0f && w > 0f) {
                                            (w / a) * 1.04f
                                        } else {
                                            1f
                                        }

                                        val peekProgress = (q / 0.45f).coerceIn(0f, 1f)
                                        val coverHeight = a * targetScale

                                        val rootH = rootHeightPx
                                        val headerH = capsuleHeaderHeightPx
                                        val queueTravel = (rootH - headerH).coerceAtLeast(0f)

                                        val queuePeekTop = rootH - 0.45f * queueTravel
                                        val queueCurrentTop = rootH - q * queueTravel

                                        val extraLiftPx = 40.dp.toPx()
                                        val gapPx = 16.dp.toPx()

                                        val peekTop = minOf(
                                            toolbarTopInRoot - extraLiftPx,
                                            queuePeekTop - coverHeight - gapPx
                                        )

                                        transformOrigin = TransformOrigin(0.5f, 0f)

                                        if (q <= 0.45f) {
                                            scaleX = 1f + peekProgress * (targetScale - 1f)
                                            scaleY = scaleX

                                            translationY =
                                                (peekTop - cardTopInRoot) * peekProgress

                                            alpha = 1f

                                        } else {
                                            scaleX = targetScale
                                            scaleY = targetScale

                                            val queueTop = rootH - q * queueTravel

                                            val coverTop = minOf(
                                                peekTop,
                                                queueTop - coverHeight - gapPx
                                            )

                                            translationY = coverTop - cardTopInRoot

                                            val coverBottom = coverTop + coverHeight

                                            val exitBottom = headerH - gapPx

                                            val fadeDistance = headerH.coerceAtLeast(1f)

                                            alpha = (
                                                    (coverBottom - exitBottom) / fadeDistance
                                                    ).coerceIn(0f, 1f)
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
                        isVisible = isVisible,
                        rootHeightPx = rootHeightPx,
                        capsuleHeaderHeightPx = capsuleHeaderHeightPx,
                        toolbarBottomInRootPx = toolbarBottomInRoot,
                        rootCoordinates = rootCoordinates,
                    )
                }
            )
        }
    }
}
