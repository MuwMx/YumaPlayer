package moe.rukamori.archivetune.ui.player.player_0

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.EqualizerDialog
import moe.rukamori.archivetune.ui.menu.TempoPitchDialog
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.PlayerSheetPredictiveBackHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetMotionController
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.scoped.playerSheetVerticalDragGesture
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberFullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.rememberSheetVisualState
import moe.rukamori.archivetune.ui.player.player_0.sett.FullPlayerOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.player.queue_0.QueueOptionsMenu
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.utils.rememberPreference

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
    onExpansionFractionChanged: (Float) -> Unit = {},
    onLyricsClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val view = LocalView.current
    val (hapticFeedbackEnabled) = rememberPreference(EnableHapticFeedbackKey, true)
    val playerConnection = LocalPlayerConnection.current

    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    var isLyricsMenuVisible by remember { mutableStateOf(false) }
    var isQueueMenuVisible by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showPitchTempoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueAddToPlaylistDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeightDp = maxHeight
        val screenHeightPx = with(density) { screenHeightDp.toPx() }
        val screenWidthDp = maxWidth
        val screenWidthPx = with(density) { screenWidthDp.toPx() }

        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navigationBarsPx = with(density) { navigationBarsPadding.toPx() }
        val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }

        val expandedY = 0f

        val totalOffsetPx = with(density) {
            val progress = if (FloatingToolbarHeight > 0.dp) {
                (bottomBarHeight / FloatingToolbarHeight).coerceIn(0f, 1f)
            } else 0f
            val bottomToolbarPadding = FloatingToolbarBottomPadding * progress
            (bottomBarHeight + bottomToolbarPadding + MiniPlayerBottomSpacing + MiniPlayerHeight).toPx()
        }

        val collapsedY = if (state.trackUrl.isEmpty()) {
            screenHeightPx
        } else {
            screenHeightPx - navigationBarsPx - totalOffsetPx
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

        val lyricsFractionProvider = { lyricsFraction.value }
        val queueFractionProvider = { queueFraction.value }

        LaunchedEffect(Unit) {
            snapshotFlow { expansionFraction.value }.collect { fraction ->
                if (fraction == 0f) {
                    if (state.isLyricsVisible) {
                        onCloseLyricsClick()
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

        // Сворачивание по внешнему запросу из состояния
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
            collapsedStateHorizontalPadding = 12.dp,
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

        val dynamicShape = remember(sheetVisualState) {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density
                ): Outline {
                    val expansionFractionVal = expansionFraction.value
                    // Фикс скругления при 99%+ раскрытии шторки
                    val radiusTop = with(density) {
                        if (expansionFractionVal > 0.99f) {
                            0f
                        } else {
                            sheetVisualState.overallSheetTopCornerRadiusProvider().toPx()
                        }
                    }
                    val radiusBottom = with(density) {
                        sheetVisualState.playerContentActualBottomRadiusProvider().toPx()
                    }
                    val dynamicHeight = sheetVisualState.playerContentAreaHeightPxProvider()

                    val targetSize = if (expansionFractionVal > 0.99f) {
                        size
                    } else {
                        Size(size.width, dynamicHeight)
                    }
                    return RoundedCornerShape(
                        topStart = radiusTop,
                        topEnd = radiusTop,
                        bottomStart = radiusBottom,
                        bottomEnd = radiusBottom
                    ).createOutline(targetSize, layoutDirection, density)
                }
            }
        }

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
                onExpandQueue = {},
                onCollapseQueue = {
                    scope.launch {
                        queueFraction.animateTo(
                            0f,
                            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            )
        }

        BackHandler(enabled = state.isLyricsVisible || lyricsFraction.value > 0.01f || queueFraction.value > 0.01f) {
            if (lyricsFraction.value > 0.01f || state.isLyricsVisible) {
                scope.launch {
                    lyricsFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
                onCloseLyricsClick()
            }
            if (queueFraction.value > 0.01f) {
                scope.launch {
                    queueFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }

        PlayerSheetPredictiveBackHandler(
            enabled = currentSheetState == PlayerSheetState.EXPANDED && !state.isLyricsVisible && lyricsFraction.value < 0.01f && queueFraction.value < 0.01f,
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

        val colorTop = Color(state.gradientColor)
        val colorBottom = Color(0xFF121212)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (expansionFraction.value * 0.6f).coerceIn(0f, 0.6f)
                }
                .background(Color.Black)
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = 0,
                        y = sheetVisualState.visualSheetTranslationYProvider().roundToInt()
                    )
                }
                .graphicsLayer {
                    translationX = if (currentSheetState == PlayerSheetState.COLLAPSED || expansionFraction.value < 0.01f) offsetAnimatable.value else 0f
                    scaleY = visualOvershootScaleY.value
                    val paddingX = sheetVisualState.currentHorizontalPaddingStartPxProvider()
                    val currentWidth = size.width - (paddingX * 2)
                    scaleX = currentWidth / size.width
                }
                .miniPlayerDismissHorizontalGesture(
                    enabled = currentSheetState == PlayerSheetState.COLLAPSED,
                    handler = miniDismissGestureHandler
                )
                .playerSheetVerticalDragGesture(
                    enabled = true,
                    handler = dragHandler
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shape = dynamicShape
                        clip = true
                    }
                    .background(Brush.verticalGradient(listOf(colorTop, colorBottom)))
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
                    onCloseQueueClick = {
                        scope.launch {
                            queueFraction.animateTo(
                                0f,
                                spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    onMoreQueueClick = { isQueueMenuVisible = true },
                    onOpenQueue = {
                        scope.launch {
                            queueFraction.animateTo(
                                1f,
                                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
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

        LyricsOptionsMenu(
            isVisible = isLyricsMenuVisible,
            onDismiss = { isLyricsMenuVisible = false },
            onAction = onAction,
            state = state
        )

        QueueOptionsMenu(
            isVisible = isQueueMenuVisible,
            onDismiss = { isQueueMenuVisible = false },
            onAction = onAction,
            onSaveAsPlaylist = {
                isQueueMenuVisible = false
                showQueueAddToPlaylistDialog = true
            },
            state = state
        )

        FullPlayerOptionsMenu(
            expanded = showSettingsMenu,
            initialScreen = menuInitialScreen,
            onDismissRequest = { showSettingsMenu = false },
            state = state,
            updateState = updateState,
            onBackgroundStyleChanged = onBackgroundStyleChanged,
            onImmersiveChanged = onImmersiveChanged,
            onOpenEqualizer = { showSettingsMenu = false; showEqualizerDialog = true },
            onOpenPlaybackSpeed = { showSettingsMenu = false; showPitchTempoDialog = true },
            onOpenAddToPlaylist = { showSettingsMenu = false; showAddToPlaylistDialog = true },
            onAction = onAction
        )

        // Диалог Эквалайзера
        if (showEqualizerDialog) {
            EqualizerDialog(
                onDismiss = { showEqualizerDialog = false },
                openSystemEqualizer = {
                    try {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            playerConnection?.localPlayer?.audioSessionId?.let {
                                extra -> putExtra(AudioEffect.EXTRA_AUDIO_SESSION, extra)
                            }
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            activityResultLauncher.launch(intent)
                        } else {
                            Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Диалог Скорости воспроизведения
        if (showPitchTempoDialog) {
            TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
        }

        if (showAddToPlaylistDialog && state.trackUrl.isNotBlank()) {
            AddToPlaylistDialog(
                isVisible = showAddToPlaylistDialog,
                onGetSong = { listOf(state.trackUrl) },
                onDismiss = { showAddToPlaylistDialog = false }
            )
        }

        if (showQueueAddToPlaylistDialog && queueState.queueWindows.isNotEmpty()) {
            val database = LocalDatabase.current
            AddToPlaylistDialog(
                isVisible = showQueueAddToPlaylistDialog,
                onGetSong = {
                    val songIds = database.withTransaction {
                        queueState.queueWindows.mapNotNull { window ->
                            window.mediaItem.metadata?.also { insert(it) }?.id
                        }
                    }
                    songIds
                },
                onDismiss = { showQueueAddToPlaylistDialog = false },
                onAddComplete = { songCount, playlistNames ->
                    val message = when {
                        playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                        else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}