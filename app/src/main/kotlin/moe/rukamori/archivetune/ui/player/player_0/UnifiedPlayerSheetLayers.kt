package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsColumn
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsHeader
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.ActiveDragSheet
import moe.rukamori.archivetune.ui.player.player_0.scoped.FullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.player.queue_0.QueueScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
internal fun UnifiedPlayerSheetLayers(
    state: PlayerUiState,
    queueState: QueueUiState,
    updateState: UpdateState,
    expansionFractionProvider: () -> Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    progressMsProvider: () -> Long,
    fullPlayerVisualState: FullPlayerVisualState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onCloseQueueClick: () -> Unit = {},
    onMoreQueueClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onMoreLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    dragHandler: SheetVerticalDragGestureHandler? = null
) {
    val haptics = rememberYumaHaptics()
    val density = LocalDensity.current.density

    val lyricsListState = rememberLazyListState()
    val canDragLyrics by remember(lyricsListState) {
        derivedStateOf {
            lyricsFractionProvider() > 0.05f &&
                    lyricsListState.firstVisibleItemIndex == 0 &&
                    lyricsListState.firstVisibleItemScrollOffset == 0
        }
    }
    val lyricsNestedScrollConnection = remember(dragHandler) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragLyrics },
            targetSheet = ActiveDragSheet.LYRICS
        )
    }

    var isQueueReordering by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val canDragQueue by remember(queueListState, isQueueReordering) {
        derivedStateOf {
            queueFractionProvider() > 0.05f &&
                    !isQueueReordering &&
                    queueListState.firstVisibleItemIndex == 0 &&
                    queueListState.firstVisibleItemScrollOffset == 0
        }
    }
    val queueNestedScrollConnection = remember(dragHandler) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragQueue },
            targetSheet = ActiveDragSheet.QUEUE
        )
    }


    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val expansion = expansionFractionProvider()
                    val isTranslucent = state.isBlurBackgroundEnabled || state.isImmersiveEnabled
                    val overlayFraction = if (isTranslucent) 0f else maxOf(lyricsFractionProvider(), queueFractionProvider())
                    alpha = (expansion * (1f - overlayFraction)).coerceIn(0f, 1f)
                }
        ) {
            moe.rukamori.archivetune.ui.player.player_0.PlayerBackgroundLayers(
                state = state,
                expansionFractionProvider = expansionFractionProvider,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                onColorsExtracted = { vibrant, darkMuted, gradient ->
                    onAction(PlayerAction.UpdateColors(vibrant, darkMuted, gradient))
                },
            )
        }

        val isMiniPlayerVisible by remember {
            derivedStateOf { expansionFractionProvider() < 0.05f }
        }

        val showMiniPlayer by remember {
            derivedStateOf { expansionFractionProvider() < 1f }
        }

        if (showMiniPlayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = expansionFractionProvider()
                        alpha = (1f - (fraction / 0.3f)).coerceIn(0f, 1f)
                    }
            ) {
                MiniPlayerContentInternal(
                    state = state,
                    expansionFractionProvider = expansionFractionProvider,
                    progressMsProvider = progressMsProvider,
                    onAction = onAction,
                    onMediaAreaClick = onExpandClick,
                    isVisible = isMiniPlayerVisible
                )
            }
        }

        val hasTrack by remember(state.title) {
            derivedStateOf { state.title.isNotEmpty() }
        }

        if (hasTrack) {
            val isFullPlayerVisible by remember {
                derivedStateOf {
                    expansionFractionProvider() > 0.005f && maxOf(lyricsFractionProvider(), queueFractionProvider()) < 1f
                }
            }
            val isLyricsVisible by remember {
                derivedStateOf { lyricsFractionProvider() > 0.05f }
            }
            val isQueueVisible by remember {
                derivedStateOf { queueFractionProvider() > 0.05f }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val maxFraction = maxOf(lyricsFractionProvider(), queueFractionProvider())
                        val expansionFraction = expansionFractionProvider()
                        alpha = expansionFraction.coerceIn(0f, 1f) * (1f - maxFraction)
                        translationY = (1f - expansionFraction) * (150f * density) - (200f * density * maxFraction)
                    }
            ) {
                FullPlayer(
                    state = state,
                    progressMsProvider = progressMsProvider,
                    updateState = updateState,
                    slideOffset = expansionFractionProvider,
                    density = density,
                    onCollapseClick = onCollapseClick,
                    onAction = onAction,
                    onSeek = onSeek,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onImmersiveChanged = onImmersiveChanged,
                    onOpenSettingsMenu = onOpenSettingsMenu,
                    onOpenQueue = onOpenQueue,
                    onSeekStarted = onSeekStarted,
                    lyricsFractionProvider = lyricsFractionProvider,
                    queueFractionProvider = queueFractionProvider,
                    isVisible = isFullPlayerVisible
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = lyricsFractionProvider()
                        alpha = fraction
                        translationY = if (fraction <= 0f) size.height else (1f - fraction) * (200f * density)
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LyricsHeader(
                        state = state,
                        animateProgressProvider = lyricsFractionProvider,
                        onCloseClick = onCloseLyricsClick,
                        onMoreClick = {
                            haptics.click()
                            onMoreLyricsClick()
                        },
                        isVisible = isLyricsVisible
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (lyricsNestedScrollConnection != null) {
                                    Modifier.nestedScroll(lyricsNestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .layout { measurable, constraints ->
                                    if (!constraints.hasBoundedWidth) {
                                        val placeable = measurable.measure(constraints)
                                        return@layout layout(placeable.width, placeable.height) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                    val borderPx = 1.dp.roundToPx()
                                    val expandedConstraints = constraints.copy(
                                        minWidth = constraints.maxWidth + (borderPx * 2),
                                        maxWidth = constraints.maxWidth + (borderPx * 2),
                                        minHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.minHeight,
                                        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.maxHeight
                                    )
                                    val placeable = measurable.measure(expandedConstraints)
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.placeRelative(-borderPx, 0)
                                    }
                                }
                                .sheetBackground(state)
                        )
                        LyricsColumn(
                            state = state,
                            animateProgressProvider = lyricsFractionProvider,
                            progressMsProvider = progressMsProvider,
                            onCloseClick = onCloseLyricsClick,
                            onMoreClick = onMoreLyricsClick,
                            onSearchClick = onSearchLyricsClick,
                            lazyListState = lyricsListState,
                            onAction = onAction,
                            onLineClick = { timeMs -> onSeek(timeMs.toFloat()) },
                            onSeek = onSeek,
                            onSeekStarted = onSeekStarted,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = queueFractionProvider()
                        alpha = fraction
                        translationY = if (fraction <= 0f) size.height else (1f - fraction) * (200f * density)
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    QueueSheetHeader(
                        queueState = queueState,
                        queueFractionProvider = queueFractionProvider,
                        onCloseClick = onCloseQueueClick,
                        onMoreQueueClick = onMoreQueueClick,
                        onToggleAutoMix = { onAction(PlayerAction.ToggleAutoMix) },
                        isAutoMixEnabled = state.isAutoMixEnabled,
                        state = state,
                        isVisible = isQueueVisible
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (queueNestedScrollConnection != null && !isQueueReordering) {
                                    Modifier.nestedScroll(queueNestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .layout { measurable, constraints ->
                                    if (!constraints.hasBoundedWidth) {
                                        val placeable = measurable.measure(constraints)
                                        return@layout layout(placeable.width, placeable.height) {
                                            placeable.placeRelative(0, 0)
                                        }
                                    }
                                    val borderPx = 1.dp.roundToPx()
                                    val expandedConstraints = constraints.copy(
                                        minWidth = constraints.maxWidth + (borderPx * 2),
                                        maxWidth = constraints.maxWidth + (borderPx * 2),
                                        minHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.minHeight,
                                        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.maxHeight
                                    )
                                    val placeable = measurable.measure(expandedConstraints)
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.placeRelative(-borderPx, 0)
                                    }
                                }
                                .sheetBackground(state)
                        )
                        QueueScreen(
                            state = queueState,
                            onAction = onAction,
                            queueFractionProvider = queueFractionProvider,
                            isSheetActive = isQueueVisible,
                            onCloseClick = onCloseQueueClick,
                            lazyListState = queueListState,
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                            ),
                            onReorderStateChange = { isQueueReordering = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
