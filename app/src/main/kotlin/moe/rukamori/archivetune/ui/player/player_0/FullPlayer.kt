/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.full.FullPlayerCoverSlot
import moe.rukamori.archivetune.ui.player.player_0.full.FullPlayerToolbarSlot
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
fun FullPlayer(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    canvasState: PlayerCanvasState = rememberPlayerCanvasState(state.trackUrl, state.title, state.artist),
    slideOffset: () -> Float,
    onCollapseClick: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
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
                    FullPlayerToolbarSlot(
                        state = state,
                        updateState = updateState,
                        rootCoordinates = rootCoordinates,
                        queueFractionProvider = queueFractionProvider,
                        onCollapseClick = onCollapseClick,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onOpenSettingsMenu = onOpenSettingsMenu,
                        onPositionedInRoot = { top, bottom ->
                            toolbarTopInRoot = top
                            toolbarBottomInRoot = bottom
                        },
                    )
                },
                cover = {
                    FullPlayerCoverSlot(
                        state = state,
                        canvasState = canvasState,
                        motionState = motionState,
                        rootCoordinates = rootCoordinates,
                        rootHeightPx = rootHeightPx,
                        safeWidthPx = safeWidthPx,
                        capsuleHeaderHeightPx = capsuleHeaderHeightPx,
                        toolbarTopInRoot = toolbarTopInRoot,
                        slideOffset = slideOffset,
                        queueFractionProvider = queueFractionProvider,
                        lyricsFractionProvider = lyricsFractionProvider,
                        onAction = onAction,
                    )
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
