package moe.rukamori.archivetune.ui.player.player_0

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
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
    isDocked: Boolean = true,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val (hapticFeedbackEnabled) = rememberPreference(EnableHapticFeedbackKey, true)

    var isLyricsMenuVisible by remember { mutableStateOf(false) }
    var isQueueMenuVisible by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val motionScope = rememberPlayerSheetMotionScope(
            state = state,
            maxHeight = maxHeight,
            maxWidth = maxWidth,
            bottomBarHeight = bottomBarHeight,
            density = density,
            onExpansionFractionChanged = onExpansionFractionChanged,
            onCloseLyricsClick = onCloseLyricsClick,
            onOpenQueue = onOpenQueue,
            onCloseQueueClick = onCloseQueueClick,
        )

        val gestures = PlayerSheetGestures(
            motionScope = motionScope,
            state = state,
            onAction = onAction,
            onCloseLyricsClick = onCloseLyricsClick,
            view = view,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
        )

        val pillShape = rememberPlayerSheetPillShape(
            density = density,
            expansionFraction = motionScope.expansionFraction,
            isDocked = isDocked,
        )
        val miniHazeStyle = rememberMiniHazeStyle(
            pureBlack = pureBlack,
            blurRadius = blurRadius
        )
        val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

        val backgroundGradient = rememberBackgroundGradient(state.gradientColor)

        PlayerSheetScaffold(
            sheetVisualState = motionScope.sheetVisualState,
            currentSheetState = motionScope.currentSheetState,
            expansionFraction = motionScope.expansionFraction,
            offsetAnimatable = motionScope.offsetAnimatable,
            visualOvershootScaleY = motionScope.visualOvershootScaleY,
            miniDismissGestureHandler = gestures.miniDismissGestureHandler,
            dragHandler = gestures.dragHandler,
            screenHeightPx = motionScope.screenHeightPx,
            screenWidthPx = motionScope.screenWidthPx,
            density = density,
            pillShape = pillShape,
            isDocked = isDocked,
            content = {
                Box(
                    modifier = Modifier
                        .playerSheetBackdrop(
                            hazeState = hazeState,
                            hazeStyle = miniHazeStyle,
                            backgroundBrush = backgroundGradient,
                            pureBlack = pureBlack
                        )
                        .playerSheetGlassBorder(
                            expansionFractionProvider = { motionScope.expansionFraction.value },
                            density = density
                        )
                ) {
                    UnifiedPlayerSheetLayers(
                        state = state,
                        queueState = queueState,
                        updateState = updateState,
                        expansionFractionProvider = { motionScope.expansionFraction.value },
                        lyricsFractionProvider = motionScope.lyricsFractionProvider,
                        queueFractionProvider = motionScope.queueFractionProvider,
                        progressMsProvider = progressMsProvider,
                        fullPlayerVisualState = motionScope.fullPlayerVisualState,
                        onAction = onAction,
                        onCloseLyricsClick = onCloseLyricsClick,
                        onCloseQueueClick = motionScope.handleCloseQueue,
                        onMoreQueueClick = { isQueueMenuVisible = true },
                        onOpenQueue = motionScope.handleOpenQueue,
                        onMoreLyricsClick = { isLyricsMenuVisible = true },
                        onSearchLyricsClick = onSearchLyricsClick,
                        onCollapseClick = { motionScope.collapse() },
                        onExpandClick = { motionScope.expand() },
                        onSeek = onSeek,
                        onSeekStarted = onSeekStarted,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onImmersiveChanged = onImmersiveChanged,
                        onOpenSettingsMenu = { screen ->
                            menuInitialScreen = screen
                            showSettingsMenu = true
                        },
                        dragHandler = gestures.dragHandler
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
