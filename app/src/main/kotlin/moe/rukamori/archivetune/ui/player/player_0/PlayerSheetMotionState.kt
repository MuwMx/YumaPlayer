package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.player.player_0.scoped.FullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetMotionController
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberFullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberSheetVisualState
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Stable
internal class PlayerSheetMotionScope(
    val density: Density,
    val screenHeightDp: Dp,
    val screenHeightPx: Float,
    val screenWidthDp: Dp,
    val screenWidthPx: Float,
    val miniHeightPx: Float,
    val expandedY: Float,
    val collapsedY: Float,
    val scope: CoroutineScope,
    val mutatorMutex: MutatorMutex,
    val velocityTracker: VelocityTracker,
    val translationY: Animatable<Float, AnimationVector1D>,
    val expansionFraction: Animatable<Float, AnimationVector1D>,
    val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    val lyricsFraction: Animatable<Float, AnimationVector1D>,
    val queueFraction: Animatable<Float, AnimationVector1D>,
    val lyricsFractionProvider: () -> Float,
    val queueFractionProvider: () -> Float,
    val motionController: SheetMotionController,
    val sheetVisualState: SheetVisualState,
    val fullPlayerVisualState: FullPlayerVisualState,
    private val currentSheetStateState: MutableState<PlayerSheetState>,
    private val predictiveBackProgressState: MutableState<Float>,
    private val isSheetSettlingState: MutableState<Boolean>,
    val handleOpenQueue: () -> Unit,
    val handleCloseQueue: () -> Unit,
) {
    var currentSheetState: PlayerSheetState
        get() = currentSheetStateState.value
        set(value) {
            currentSheetStateState.value = value
        }

    var predictiveBackProgress: Float
        get() = predictiveBackProgressState.value
        set(value) {
            predictiveBackProgressState.value = value
        }

    var isSheetSettling: Boolean
        get() = isSheetSettlingState.value
        private set(value) {
            isSheetSettlingState.value = value
        }

    fun collapse() {
        scope.launch {
            if (isSheetSettlingState.value) return@launch
            isSheetSettlingState.value = true
            try {
                motionController.animateTo(false, true, collapsedY)
                currentSheetState = PlayerSheetState.COLLAPSED
            } finally {
                isSheetSettlingState.value = false
            }
        }
    }

    fun expand() {
        scope.launch {
            if (isSheetSettlingState.value) return@launch
            isSheetSettlingState.value = true
            try {
                motionController.animateTo(true, true, collapsedY)
                currentSheetState = PlayerSheetState.EXPANDED
            } finally {
                isSheetSettlingState.value = false
            }
        }
    }
}

@Composable
internal fun rememberPlayerSheetMotionScope(
    state: PlayerUiState,
    maxHeight: Dp,
    maxWidth: Dp,
    bottomBarHeight: Dp,
    density: Density,
    onExpansionFractionChanged: (Float) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onOpenQueue: () -> Unit,
    onCloseQueueClick: () -> Unit,
): PlayerSheetMotionScope {
    val screenHeightDp = maxHeight
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    val screenWidthDp = maxWidth
    val screenWidthPx = with(density) { screenWidthDp.toPx() }

    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }

    val expandedY = 0f

    val totalOffsetPx = with(density) {
        val progress = if (FloatingToolbarHeight > 0.dp) {
            (bottomBarHeight / FloatingToolbarHeight).coerceIn(0f, 1f)
        } else 0f
        val navContribution = (FloatingToolbarHeight + FloatingToolbarBottomPadding) * progress
        (navigationBarsPadding + navContribution + MiniPlayerBottomSpacing + MiniPlayerHeight).toPx()
    }

    val collapsedY = if (state.trackUrl.isEmpty()) {
        screenHeightPx
    } else {
        screenHeightPx - totalOffsetPx
    }

    val scope = rememberCoroutineScope()
    val mutatorMutex = remember { MutatorMutex() }
    val velocityTracker = remember { VelocityTracker() }

    val currentSheetStateState = remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
    var currentSheetState by currentSheetStateState
    val translationY = remember { Animatable(collapsedY) }
    val expansionFraction = remember { Animatable(0f) }
    val visualOvershootScaleY = remember { Animatable(1f) }
    val predictiveBackProgressState = remember { mutableStateOf(0f) }
    val isSheetSettlingState = remember { mutableStateOf(false) }
    val predictiveBackProgress by predictiveBackProgressState

    val offsetAnimatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        snapshotFlow { expansionFraction.value }.collect { fraction ->
            onExpansionFractionChanged(fraction)
        }
    }

    val lyricsFraction = remember { Animatable(0f) }
    val queueFraction = remember { Animatable(0f) }

    LaunchedEffect(state.isLyricsVisible) {
        if (state.isLyricsVisible) {
            withFrameNanos { }
            lyricsFraction.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        } else {
            lyricsFraction.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    LaunchedEffect(state.isQueueVisible) {
        val target = if (state.isQueueVisible) 1f else 0f
        if (queueFraction.targetValue != target) {
            queueFraction.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    val lyricsFractionProvider = { lyricsFraction.value }
    val queueFractionProvider = { queueFraction.value }

    val handleOpenQueue: () -> Unit = {
        scope.launch {
            queueFraction.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
        onOpenQueue()
    }

    val handleCloseQueue: () -> Unit = {
        scope.launch {
            queueFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
        onCloseQueueClick()
    }

    LaunchedEffect(Unit) {
        snapshotFlow { expansionFraction.value }.collect { fraction ->
            if (fraction == 0f) {
                if (state.isLyricsVisible) {
                    onCloseLyricsClick()
                }
                if (state.isQueueVisible) {
                    onCloseQueueClick()
                }
                if (lyricsFraction.value > 0f) {
                    lyricsFraction.snapTo(0f)
                }
                if (queueFraction.value > 0f) {
                    queueFraction.snapTo(0f)
                }
            }
        }
    }

    val motionController = remember(translationY, expansionFraction, mutatorMutex) {
        SheetMotionController(
            translationY = translationY,
            expansionFraction = expansionFraction,
            mutex = mutatorMutex,
            defaultAnimationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            expandedY = expandedY
        )
    }

    LaunchedEffect(collapsedY, motionController) {
        if (currentSheetState == PlayerSheetState.COLLAPSED) {
            if (translationY.value >= screenHeightPx - 1f && collapsedY < screenHeightPx) {
                translationY.animateTo(collapsedY, spring(stiffness = Spring.StiffnessMediumLow))
            } else {
                motionController.syncToExpansion(collapsedY)
            }
        } else {
            motionController.syncToExpansion(collapsedY)
        }
    }

    LaunchedEffect(state.isSheetCollapseRequested) {
        if (state.isSheetCollapseRequested) {
            if (currentSheetState == PlayerSheetState.EXPANDED || expansionFraction.value > 0.01f) {
                scope.launch {
                    motionController.animateTo(false, true, collapsedY)
                    currentSheetState = PlayerSheetState.COLLAPSED
                }
            }
        }
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = true,
        collapsedStateHorizontalPadding = FloatingToolbarHorizontalPadding,
        predictiveBackCollapseProgress = predictiveBackProgress,
        currentSheetContentState = currentSheetState,
        playerContentExpansionFraction = expansionFraction,
        containerHeight = screenHeightDp,
        currentSheetTranslationY = translationY,
        sheetCollapsedTargetY = collapsedY,
        isPlaying = state.isPlaying,
        hasCurrentSong = true
    )

    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = expansionFraction,
        initialOffsetY = 150f
    )

    return remember(
        density,
        screenHeightDp,
        screenHeightPx,
        screenWidthDp,
        screenWidthPx,
        miniHeightPx,
        expandedY,
        collapsedY,
        scope,
        mutatorMutex,
        velocityTracker,
        translationY,
        expansionFraction,
        visualOvershootScaleY,
        offsetAnimatable,
        lyricsFraction,
        queueFraction,
        motionController,
        sheetVisualState,
        fullPlayerVisualState,
    ) {
        PlayerSheetMotionScope(
            density = density,
            screenHeightDp = screenHeightDp,
            screenHeightPx = screenHeightPx,
            screenWidthDp = screenWidthDp,
            screenWidthPx = screenWidthPx,
            miniHeightPx = miniHeightPx,
            expandedY = expandedY,
            collapsedY = collapsedY,
            scope = scope,
            mutatorMutex = mutatorMutex,
            velocityTracker = velocityTracker,
            translationY = translationY,
            expansionFraction = expansionFraction,
            visualOvershootScaleY = visualOvershootScaleY,
            offsetAnimatable = offsetAnimatable,
            lyricsFraction = lyricsFraction,
            queueFraction = queueFraction,
            lyricsFractionProvider = lyricsFractionProvider,
            queueFractionProvider = queueFractionProvider,
            motionController = motionController,
            sheetVisualState = sheetVisualState,
            fullPlayerVisualState = fullPlayerVisualState,
            currentSheetStateState = currentSheetStateState,
            predictiveBackProgressState = predictiveBackProgressState,
            isSheetSettlingState = isSheetSettlingState,
            handleOpenQueue = handleOpenQueue,
            handleCloseQueue = handleCloseQueue,
        )
    }
}
