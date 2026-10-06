/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.queue_0

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.Timeline
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal val Timeline.Window.queueItemKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())

@Composable
fun QueueScreen(
    state: QueueUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    queueFractionProvider: () -> Float = { 1f },
    isSheetActive: Boolean = true,
    onReorderStateChange: (Boolean) -> Unit = {},
    onCloseClick: () -> Unit = {},
) {

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val (cropToSquare) = rememberPreference(CropThumbnailToSquareKey, false)
    val haptics = rememberYumaHaptics()
    val hapticView = LocalView.current
    val playerConnection = LocalPlayerConnection.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val itemWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { (configuration.screenWidthDp.dp - 40.dp).toPx() }
    }

    val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
    var dragFromKey by remember { mutableStateOf<Any?>(null) }
    var dragToKey by remember { mutableStateOf<Any?>(null) }
    var initialWindowsSnapshot by remember { mutableStateOf<List<Timeline.Window>?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }

    val currentPlayingUid =
        remember(state.currentWindowIndex, state.queueWindows) {
            state.queueWindows.getOrNull(state.currentWindowIndex)?.uid
        }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            if (queueFractionProvider() <= 0.05f) {
                false
            } else {
                val layoutInfo = lazyListState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
            }
        }.distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    playerConnection?.service?.onInfiniteQueueEnabled()
                }
            }
    }

    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            onMove = { from, to ->
                if (dragFromKey == null) {
                    initialWindowsSnapshot = mutableQueueWindows.toList()
                    dragFromKey = from.key
                }
                dragToKey = to.key
                mutableQueueWindows.add(to.index, mutableQueueWindows.removeAt(from.index))
            },
        )

    LaunchedEffect(state.queueWindows) {
        if (!reorderableState.isAnyItemDragging) {
            val isContentIdentical =
                mutableQueueWindows.size == state.queueWindows.size &&
                    mutableQueueWindows.indices.all { i ->
                        mutableQueueWindows[i].queueItemKey == state.queueWindows[i].queueItemKey
                    }
            if (!isContentIdentical) {
                mutableQueueWindows.clear()
                mutableQueueWindows.addAll(state.queueWindows)
            }
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        onReorderStateChange(reorderableState.isAnyItemDragging)
        if (!reorderableState.isAnyItemDragging) {
            val fromKey = dragFromKey
            val toKey = dragToKey
            val snapshot = initialWindowsSnapshot
            if (fromKey != null && toKey != null && fromKey != toKey && snapshot != null) {
                val fromWindow = snapshot.firstOrNull { it.queueItemKey == fromKey }
                val toWindow = snapshot.firstOrNull { it.queueItemKey == toKey }
                if (fromWindow != null && toWindow != null) {
                    onAction(PlayerAction.MoveQueueItem(fromWindow.uid, toWindow.uid))
                }
            }
            dragFromKey = null
            dragToKey = null
            initialWindowsSnapshot = null
        }
    }

    val fadeHeight = 24.dp
    val darkScheme = remember { darkColorScheme() }
    val yumaColors = remember(darkScheme) { darkYumaColorScheme(darkScheme) }
    MaterialTheme(colorScheme = darkScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalYumaColors provides yumaColors,
        ) {
            val surfaceColor = MaterialTheme.colorScheme.surface


            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = !(reorderableState.isAnyItemDragging || reorderHandleInUse),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
            items = mutableQueueWindows,
            key = { _, window -> window.queueItemKey },
            contentType = { _, _ -> "queue_item" },
        ) { _, window ->
            ReorderableItem(
                state = reorderableState,
                key = window.queueItemKey,
                modifier = if (reorderableState.isAnyItemDragging) Modifier else Modifier.animateItem(),
            ) { isDragging ->
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.02f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    label = "queueItemScale",
                )

                QueueItem(
                    window = window,
                    isActive = currentPlayingUid != null && window.uid == currentPlayingUid,
                    isDragging = isDragging,
                    cropToSquare = cropToSquare,
                    shouldLoadImage = isSheetActive,
                    itemWidthPx = itemWidthPx,
                    isSheetActive = isSheetActive,
                    enableHapticFeedback = enableHapticFeedback,
                    hapticView = hapticView,
                    onPlay = remember(window.uid, onAction) {
                        {
                            haptics.click()
                            onAction(PlayerAction.PlayQueueItem(window.uid))
                        }
                    },
                    onRemove = remember(window.uid, onAction) {
                        {
                            mutableQueueWindows.removeAll { it.uid == window.uid }
                            onAction(PlayerAction.RemoveQueueItem(window.uid))
                        }
                    },
                    dragHandle = {
                        IconButton(
                            onClick = {},
                            modifier =
                                Modifier
                                    .draggableHandle(
                                        onDragStarted = {
                                            reorderHandleInUse = true
                                            onReorderStateChange(true)
                                            if (enableHapticFeedback) {
                                                ViewCompat.performHapticFeedback(
                                                    hapticView,
                                                    HapticFeedbackConstantsCompat.GESTURE_START,
                                                )
                                            }
                                        },
                                        onDragStopped = {
                                            reorderHandleInUse = false
                                            onReorderStateChange(false)
                                            if (enableHapticFeedback) {
                                                ViewCompat.performHapticFeedback(
                                                    hapticView,
                                                    HapticFeedbackConstantsCompat.GESTURE_END,
                                                )
                                            }
                                        },
                                    )
                                    .graphicsLayer { alpha = 0.99f }
                                    .size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                compositingStrategy =
                                    if (isDragging) CompositingStrategy.Offscreen
                                    else CompositingStrategy.Auto
                            },
                )
            }
            }
        }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(fadeHeight)
                            .align(Alignment.TopCenter)
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(fadeHeight)
                            .align(Alignment.BottomCenter)
                )
            }
        }
    }
}
