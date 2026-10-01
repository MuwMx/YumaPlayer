package moe.rukamori.archivetune.ui.player.player_0

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.PlayerSheetPredictiveBackHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import moe.rukamori.archivetune.ui.state.PlayerUiState

internal class PlayerSheetGestures(
    val dragHandler: SheetVerticalDragGestureHandler,
    val miniDismissGestureHandler: MiniPlayerDismissGestureHandler,
)

@Composable
internal fun rememberPlayerSheetGestures(
    motionScope: PlayerSheetMotionScope,
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    view: View,
    hapticFeedbackEnabled: Boolean,
): PlayerSheetGestures {
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = motionScope.scope,
        density = motionScope.density,
        hapticView = view,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        offsetAnimatable = motionScope.offsetAnimatable,
        screenWidthPx = motionScope.screenWidthPx,
        onDismissPlaylistAndShowUndo = { onAction(PlayerAction.Dismiss) }
    )

    val dragHandler = remember(
        motionScope.motionController,
        motionScope.sheetVisualState,
        motionScope.screenHeightPx,
        motionScope.screenWidthPx
    ) {
        SheetVerticalDragGestureHandler(
            scope = motionScope.scope,
            velocityTracker = motionScope.velocityTracker,
            densityProvider = { motionScope.density },
            sheetMotionController = motionScope.motionController,
            playerContentExpansionFraction = motionScope.expansionFraction,
            currentSheetTranslationY = motionScope.translationY,
            lyricsFraction = motionScope.lyricsFraction,
            queueFraction = motionScope.queueFraction,
            expandedYProvider = { motionScope.expandedY },
            collapsedYProvider = { motionScope.collapsedY },
            miniHeightPxProvider = { motionScope.miniHeightPx },
            screenHeightPxProvider = { motionScope.screenHeightPx },
            screenWidthPxProvider = { motionScope.screenWidthPx },
            currentSheetStateProvider = { motionScope.currentSheetState },
            visualOvershootScaleY = motionScope.visualOvershootScaleY,
            onDraggingChange = {},
            onDraggingPlayerAreaChange = {},
            onAnimateSheet = { targetExpanded, spec, velocity ->
                motionScope.motionController.animateTo(
                    targetExpanded = targetExpanded,
                    canExpand = true,
                    collapsedY = motionScope.collapsedY,
                    animationSpec = spec ?: spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                    initialVelocity = velocity
                )
            },
            onExpandSheetState = { motionScope.currentSheetState = PlayerSheetState.EXPANDED },
            onCollapseSheetState = { motionScope.currentSheetState = PlayerSheetState.COLLAPSED },
            onExpandLyrics = {
                onAction(PlayerAction.Lyrics)
            },
            onCollapseLyrics = {
                onCloseLyricsClick()
            },
            onExpandQueue = motionScope.handleOpenQueue,
            onCollapseQueue = motionScope.handleCloseQueue
        )
    }

    BackHandler(
        enabled = state.isLyricsVisible || state.isQueueVisible
    ) {
        if (motionScope.lyricsFraction.value > 0.01f || state.isLyricsVisible) {
            motionScope.scope.launch {
                motionScope.lyricsFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            }
            onCloseLyricsClick()
        }
        if (motionScope.queueFraction.value > 0.01f || state.isQueueVisible) {
            motionScope.handleCloseQueue()
        }
    }

    PlayerSheetPredictiveBackHandler(
        enabled = motionScope.currentSheetState == PlayerSheetState.EXPANDED && !state.isLyricsVisible && !state.isQueueVisible,
        currentSheetState = motionScope.currentSheetState,
        predictiveBackFractionValue = motionScope.predictiveBackProgress,
        onPredictiveBackFractionChanged = { motionScope.predictiveBackProgress = it },
        sheetCollapsedTargetY = motionScope.collapsedY,
        sheetExpandedTargetY = motionScope.expandedY,
        sheetMotionController = motionScope.motionController,
        animationDurationMs = 300,
        onSwipeEdgeChanged = {},
        onCollapse = {
            motionScope.collapse()
        },
        onExpand = {
            motionScope.expand()
        },
        registrationKey = motionScope.currentSheetState
    )

    return remember(dragHandler, miniDismissGestureHandler) {
        PlayerSheetGestures(
            dragHandler = dragHandler,
            miniDismissGestureHandler = miniDismissGestureHandler
        )
    }
}

@Composable
internal fun PlayerSheetGestures(
    motionScope: PlayerSheetMotionScope,
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    view: View,
    hapticFeedbackEnabled: Boolean,
): PlayerSheetGestures = rememberPlayerSheetGestures(
    motionScope = motionScope,
    state = state,
    onAction = onAction,
    onCloseLyricsClick = onCloseLyricsClick,
    view = view,
    hapticFeedbackEnabled = hapticFeedbackEnabled,
)
