package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Stable
internal class FullPlayerMotionState(
    val isOverlayVisibleState: State<Boolean>,
    val isSheetExpandedState: State<Boolean>,
    val coverGestureEnabledState: State<Boolean>,
    val canPlayCanvasState: State<Boolean>,
    val immersiveCoverAlphaState: State<Float>,
    val immersiveCoverScaleState: State<Float>,
    val controlsOffsetYState: State<Dp>,
) {
    val isOverlayVisible: Boolean get() = isOverlayVisibleState.value
    val isSheetExpanded: Boolean get() = isSheetExpandedState.value
    val coverGestureEnabled: Boolean get() = coverGestureEnabledState.value
    val canPlayCanvas: Boolean get() = canPlayCanvasState.value
    val immersiveCoverAlpha: Float get() = immersiveCoverAlphaState.value
    val immersiveCoverScale: Float get() = immersiveCoverScaleState.value
    val controlsOffsetY: Dp get() = controlsOffsetYState.value
}

@Composable
internal fun rememberFullPlayerMotionState(
    state: PlayerUiState,
    slideOffset: () -> Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
): FullPlayerMotionState {
    var prevLyricsVisible by remember { mutableStateOf(state.isLyricsVisible) }
    val isExitingLyrics = prevLyricsVisible && !state.isLyricsVisible
    LaunchedEffect(state.isLyricsVisible) { prevLyricsVisible = state.isLyricsVisible }

    val isOverlayVisibleState = remember(state.isLyricsVisible) {
        derivedStateOf {
            state.isLyricsVisible || lyricsFractionProvider() > 0.5f
        }
    }

    val isSheetExpandedState = remember(slideOffset) { derivedStateOf { slideOffset() > 0.95f } }

    val coverGestureEnabledState = remember(state.isImmersiveEnabled, state.isLyricsVisible) {
        derivedStateOf {
            !state.isImmersiveEnabled && !state.isLyricsVisible &&
                lyricsFractionProvider() < 0.05f && queueFractionProvider() < 0.05f
        }
    }

    val canPlayCanvasState = remember(
        state.isPlaying,
        state.isImmersiveEnabled,
        state.isLyricsVisible,
        state.isQueueVisible,
    ) {
        derivedStateOf {
            state.isPlaying &&
                !state.isImmersiveEnabled &&
                isSheetExpandedState.value &&
                !state.isLyricsVisible &&
                !state.isQueueVisible &&
                lyricsFractionProvider() < 0.05f &&
                queueFractionProvider() < 0.05f
        }
    }

    val immersiveCoverAlphaState = animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled) {
            if (isOverlayVisibleState.value) 1f else 0f
        } else {
            if (isOverlayVisibleState.value) 0f else 1f
        },
        animationSpec = if (isExitingLyrics && state.isImmersiveEnabled) {
            snap()
        } else {
            tween(durationMillis = 450, easing = FastOutSlowInEasing)
        },
        label = "ImmersiveCoverAlpha"
    )
    val immersiveCoverScaleState = animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled) {
            if (isOverlayVisibleState.value) 1f else 1.35f
        } else {
            if (isOverlayVisibleState.value) 0.8f else 1f
        },
        animationSpec = if (isExitingLyrics && state.isImmersiveEnabled) {
            snap()
        } else {
            tween(durationMillis = 450, easing = FastOutSlowInEasing)
        },
        label = "ImmersiveCoverScale"
    )

    val controlsOffsetYState = animateDpAsState(
        targetValue = if (state.isImmersiveEnabled) 16.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "ImmersiveControlsOffset"
    )

    return remember(
        isOverlayVisibleState,
        isSheetExpandedState,
        coverGestureEnabledState,
        canPlayCanvasState,
        immersiveCoverAlphaState,
        immersiveCoverScaleState,
        controlsOffsetYState
    ) {
        FullPlayerMotionState(
            isOverlayVisibleState = isOverlayVisibleState,
            isSheetExpandedState = isSheetExpandedState,
            coverGestureEnabledState = coverGestureEnabledState,
            canPlayCanvasState = canPlayCanvasState,
            immersiveCoverAlphaState = immersiveCoverAlphaState,
            immersiveCoverScaleState = immersiveCoverScaleState,
            controlsOffsetYState = controlsOffsetYState,
        )
    }
}
