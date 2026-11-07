/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0.full

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.FullPlayerMotionState
import moe.rukamori.archivetune.ui.player.player_0.PlayerCanvasState
import moe.rukamori.archivetune.ui.player.player_0.PlayerCoverCard
import moe.rukamori.archivetune.ui.player.player_0.PlayerCoverGlow
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.playerArtworkFade
import moe.rukamori.archivetune.ui.state.PlayerUiState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun FullPlayerCoverSlot(
    state: PlayerUiState,
    canvasState: PlayerCanvasState,
    motionState: FullPlayerMotionState,
    rootCoordinates: LayoutCoordinates?,
    rootHeightPx: Float,
    safeWidthPx: Float,
    capsuleHeaderHeightPx: Float,
    toolbarTopInRoot: Float,
    slideOffset: () -> Float,
    queueFractionProvider: () -> Float,
    lyricsFractionProvider: () -> Float = { 0f },
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseScaleState = rememberCoverPlayPauseScale(state.isPlaying)
    val trackSwitchScale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    val isCoverGlowEnabled by remember(
        state.isAlbumCoverGlowEnabled,
        state.isPlaying,
        state.isImmersiveEnabled,
        motionState.isOverlayVisible,
        slideOffset,
        queueFractionProvider,
        lyricsFractionProvider,
    ) {
        derivedStateOf {
            val isCoverVisible = !state.isImmersiveEnabled && !motionState.isOverlayVisible
            isCoverVisible &&
                    state.isAlbumCoverGlowEnabled &&
                    state.isPlaying &&
                    slideOffset() > 0.95f &&
                    queueFractionProvider().coerceIn(0f, 1f) < 0.05f &&
                    lyricsFractionProvider().coerceIn(0f, 1f) < 0.05f
        }
    }

    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardTopInRoot by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .onGloballyPositioned { slotCoords ->
                if (rootCoordinates != null && rootCoordinates.isAttached && slotCoords.isAttached) {
                    cardTopInRoot = rootCoordinates.localPositionOf(slotCoords, Offset.Zero).y
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
                    val isCoverVisible = !state.isImmersiveEnabled && !motionState.isOverlayVisible
                    val effectiveScale = playPauseScaleState.value * trackSwitchScale.value

                    val kinematics = calculateCoverKinematics(
                        q = queueFractionProvider().coerceIn(0f, 1f),
                        playPauseScale = effectiveScale,
                        isCoverVisible = isCoverVisible,
                        safeWidthPx = safeWidthPx,
                        cardWidthPx = cardWidthPx,
                        cardTopInRoot = cardTopInRoot,
                        toolbarTopInRoot = toolbarTopInRoot,
                        rootHeightPx = rootHeightPx,
                        capsuleHeaderHeightPx = capsuleHeaderHeightPx,
                        extraLiftPx = 40.dp.toPx(),
                        gapPx = 16.dp.toPx(),
                    )
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = kinematics.scale
                    scaleY = kinematics.scale
                    translationY = kinematics.translationY
                    alpha = kinematics.alpha
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .playerArtworkFade(queueFractionProvider)
            ) {
                PlayerCoverGlow(
                    coverUrl = state.coverUrl,
                    enabled = isCoverGlowEnabled,
                    modifier = Modifier.fillMaxSize(),
                )

                PlayerCoverCard(
                    coverUrl = state.coverUrl,
                    placeholderResId = state.placeholderResId,
                    gestureEnabled = motionState.coverGestureEnabled,
                    mediaId = state.trackUrl,
                    songTitle = state.title,
                    artistName = state.artist,
                    isPlaying = state.isPlaying,
                    canvasHostEnabled = !state.isImmersiveEnabled,
                    canvasState = canvasState,
                    onCoverLoaded = {
                        coroutineScope.launch {
                            trackSwitchScale.snapTo(0.88f)
                            trackSwitchScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.58f,
                                    stiffness = 340f,
                                )
                            )
                        }
                    },
                    onNext = { onAction(PlayerAction.Next) },
                    onPrevious = { onAction(PlayerAction.Previous) }
                )
            }
        }
    }
}
