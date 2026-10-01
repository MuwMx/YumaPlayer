package moe.rukamori.archivetune.ui.player.player_0

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.PlayerSheetPredictiveBackHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetMotionController
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberFullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberSheetVisualState
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun UnifiedPlayerSheetV2(
    state: PlayerUiState,
    queueState: QueueUiState = QueueUiState(),
    updateState: UpdateState = UpdateState.NoUpdate,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    progressMsProvider: () -> Long,
    bottomBarHeight: Dp = 0.dp,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onExpansionFractionChanged: (Float) -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onCloseQueueClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val (hapticFeedbackEnabled) = rememberPreference(EnableHapticFeedbackKey, true)

    var isLyricsMenuVisible by remember { mutableStateOf(false) }
    var isQueueMenuVisible by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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

        var currentSheetState by remember { mutableStateOf(PlayerSheetState.COLLAPSED) }
        val translationY = remember { Animatable(collapsedY) }
        val expansionFraction = remember { Animatable(0f) }
        val visualOvershootScaleY = remember { Animatable(1f) }
        var predictiveBackProgress by remember { mutableStateOf(0f) }

        val offsetAnimatable = remember { Animatable(0f) }
        val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticView = view,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissPlaylistAndShowUndo = { onAction(PlayerAction.Dismiss) }
        )

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

        val dragHandler = remember(motionController, sheetVisualState, screenHeightPx, screenWidthPx) {
            SheetVerticalDragGestureHandler(
                scope = scope,
                velocityTracker = velocityTracker,
                densityProvider = { density },
                sheetMotionController = motionController,
                playerContentExpansionFraction = expansionFraction,
                currentSheetTranslationY = translationY,
                lyricsFraction = lyricsFraction,
                queueFraction = queueFraction,
                expandedYProvider = { expandedY },
                collapsedYProvider = { collapsedY },
                miniHeightPxProvider = { miniHeightPx },
                screenHeightPxProvider = { screenHeightPx },
                screenWidthPxProvider = { screenWidthPx },
                currentSheetStateProvider = { currentSheetState },
                visualOvershootScaleY = visualOvershootScaleY,
                onDraggingChange = {},
                onDraggingPlayerAreaChange = {},
                onAnimateSheet = { targetExpanded, spec, velocity ->
                    motionController.animateTo(
                        targetExpanded = targetExpanded,
                        canExpand = true,
                        collapsedY = collapsedY,
                        animationSpec = spec ?: spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                        initialVelocity = velocity
                    )
                },
                onExpandSheetState = { currentSheetState = PlayerSheetState.EXPANDED },
                onCollapseSheetState = { currentSheetState = PlayerSheetState.COLLAPSED },
                onExpandLyrics = {
                    onAction(PlayerAction.Lyrics)
                },
                onCollapseLyrics = {
                    onCloseLyricsClick()
                },
                onExpandQueue = handleOpenQueue,
                onCollapseQueue = handleCloseQueue
            )
        }

        BackHandler(
            enabled = state.isLyricsVisible || state.isQueueVisible
        ) {
            if (lyricsFraction.value > 0.01f || state.isLyricsVisible) {
                scope.launch {
                    lyricsFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
                onCloseLyricsClick()
            }
            if (queueFraction.value > 0.01f || state.isQueueVisible) {
                handleCloseQueue()
            }
        }

        PlayerSheetPredictiveBackHandler(
            enabled = currentSheetState == PlayerSheetState.EXPANDED && !state.isLyricsVisible && !state.isQueueVisible,
            currentSheetState = currentSheetState,
            predictiveBackFractionValue = predictiveBackProgress,
            onPredictiveBackFractionChanged = { predictiveBackProgress = it },
            sheetCollapsedTargetY = collapsedY,
            sheetExpandedTargetY = expandedY,
            sheetMotionController = motionController,
            animationDurationMs = 300,
            onSwipeEdgeChanged = {},
            onCollapse = {
                scope.launch {
                    motionController.animateTo(false, true, collapsedY)
                    currentSheetState = PlayerSheetState.COLLAPSED
                }
            },
            onExpand = {
                scope.launch {
                    motionController.animateTo(true, true, collapsedY)
                    currentSheetState = PlayerSheetState.EXPANDED
                }
            },
            registrationKey = currentSheetState
        )

        val pillShape = rememberPlayerSheetPillShape(
            density = density,
            expansionFraction = expansionFraction
        )

        PlayerSheetScaffold(
            sheetVisualState = sheetVisualState,
            currentSheetState = currentSheetState,
            expansionFraction = expansionFraction,
            offsetAnimatable = offsetAnimatable,
            visualOvershootScaleY = visualOvershootScaleY,
            miniDismissGestureHandler = miniDismissGestureHandler,
            dragHandler = dragHandler,
            screenHeightPx = screenHeightPx,
            screenWidthPx = screenWidthPx,
            density = density,
            pillShape = pillShape,
            content = {
                Box(
                    modifier = Modifier.playerSheetGlassBorder(
                        expansionFractionProvider = { expansionFraction.value },
                        density = density
                    )
                ) {
                    UnifiedPlayerSheetLayers(
                        state = state,
                        queueState = queueState,
                        updateState = updateState,
                        expansionFractionProvider = { expansionFraction.value },
                        lyricsFractionProvider = lyricsFractionProvider,
                        queueFractionProvider = queueFractionProvider,
                        progressMsProvider = progressMsProvider,
                        fullPlayerVisualState = fullPlayerVisualState,
                        onAction = onAction,
                        onCloseLyricsClick = onCloseLyricsClick,
                        onCloseQueueClick = handleCloseQueue,
                        onMoreQueueClick = { isQueueMenuVisible = true },
                        onOpenQueue = handleOpenQueue,
                        onMoreLyricsClick = { isLyricsMenuVisible = true },
                        onSearchLyricsClick = onSearchLyricsClick,
                        onCollapseClick = {
                            scope.launch {
                                motionController.animateTo(false, true, collapsedY)
                                currentSheetState = PlayerSheetState.COLLAPSED
                            }
                        },
                        onExpandClick = {
                            scope.launch {
                                motionController.animateTo(true, true, collapsedY)
                                currentSheetState = PlayerSheetState.EXPANDED
                            }
                        },
                        onSeek = onSeek,
                        onSeekStarted = onSeekStarted,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onImmersiveChanged = onImmersiveChanged,
                        onOpenSettingsMenu = { screen ->
                            menuInitialScreen = screen
                            showSettingsMenu = true
                        },
                        dragHandler = dragHandler
                    )
                }
            }
        )

        PlayerSheetDialogs(
            state = state,
            queueState = queueState,
            updateState = updateState,
            onAction = onAction,
            onBackgroundStyleChanged = onBackgroundStyleChanged,
            onImmersiveChanged = onImmersiveChanged,
            isLyricsMenuVisible = isLyricsMenuVisible,
            onDismissLyricsMenu = { isLyricsMenuVisible = false },
            isQueueMenuVisible = isQueueMenuVisible,
            onDismissQueueMenu = { isQueueMenuVisible = false },
            showSettingsMenu = showSettingsMenu,
            menuInitialScreen = menuInitialScreen,
            onDismissSettingsMenu = { showSettingsMenu = false }
        )
    }
}
