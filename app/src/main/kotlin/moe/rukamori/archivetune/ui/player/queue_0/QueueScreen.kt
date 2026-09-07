/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.queue_0

import android.util.Log
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.media3.common.Timeline
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val Timeline.Window.queueItemKey: Long
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
    var dragFromIndex by remember { mutableStateOf<Int?>(null) }
    var dragToIndex by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }

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
                    Log.d("TEMP_PAUSE_LOG", "QueueScreen: pagination resumed / active (loading more)")
                    playerConnection?.service?.onInfiniteQueueEnabled()
                } else {
                    Log.d("TEMP_PAUSE_LOG", "QueueScreen: pagination sleeping (hidden or not near end, fraction=${queueFractionProvider()})")
                }
            }
    }

    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            onMove = { from, to ->
                if (dragFromIndex == null) {
                    dragFromIndex = from.index
                }
                dragToIndex = to.index
                mutableQueueWindows.add(to.index, mutableQueueWindows.removeAt(from.index))
            },
        )

    LaunchedEffect(state.queueWindows) {
        if (!reorderableState.isAnyItemDragging) {
            mutableQueueWindows.clear()
            mutableQueueWindows.addAll(state.queueWindows)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        onReorderStateChange(reorderableState.isAnyItemDragging)
        if (!reorderableState.isAnyItemDragging) {
            val from = dragFromIndex
            val to = dragToIndex
            if (from != null && to != null && from != to) {
                onAction(PlayerAction.MoveQueueItem(from, to))
            }
            dragFromIndex = null
            dragToIndex = null
        }
    }

    val fadeHeight = 24.dp
    val darkScheme = darkColorScheme()
    MaterialTheme(colorScheme = darkScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalYumaColors provides darkYumaColorScheme(darkScheme),
        ) {
            LazyColumn(
                state = lazyListState,
                userScrollEnabled = !(reorderableState.isAnyItemDragging || reorderHandleInUse),
                modifier =
                    modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Во время движения пальца GPU не тратит ресурсы на FBO-буфер
                            compositingStrategy =
                                if (queueFractionProvider() >= 0.99f) {
                                    CompositingStrategy.Offscreen
                                } else {
                                    CompositingStrategy.Auto
                                }
                        }
                        .drawWithContent {
                            drawContent()
                            if (queueFractionProvider() <= 0f) return@drawWithContent
                            val fadeHeightPx = fadeHeight.toPx()
                            if (size.height > 0f && fadeHeightPx > 0f) {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black),
                                            startY = 0f,
                                            endY = fadeHeightPx,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height - fadeHeightPx,
                                            endY = size.height,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
            items = mutableQueueWindows,
            key = { _, window -> window.queueItemKey },
            contentType = { _, _ -> "queue_item" },
        ) { index, window ->
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
                    index = index,
                    isActive = index == state.currentWindowIndex,
                    isDragging = isDragging,
                    cropToSquare = cropToSquare,
                    itemWidthPx = itemWidthPx,
                    enableHapticFeedback = enableHapticFeedback,
                    hapticView = hapticView,
                    onPlay = {
                        haptics.click()
                        onAction(PlayerAction.PlayQueueItem(index))
                    },
                    onRemove = { onAction(PlayerAction.RemoveQueueItem(index)) },
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
    }
}
}

@Composable
private fun QueueItem(
    window: Timeline.Window,
    index: Int,
    isActive: Boolean,
    isDragging: Boolean,
    cropToSquare: Boolean,
    itemWidthPx: Float,
    enableHapticFeedback: Boolean,
    hapticView: View,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = window.mediaItem.metadata ?: return
    val dismissScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissOffsetAnimatable = remember(window.queueItemKey) { Animatable(0f) }

    val dismissEnabled = !isDragging
    val dismissHandler =
        remember(window.queueItemKey, dismissEnabled, itemWidthPx, enableHapticFeedback) {
            if (dismissEnabled && itemWidthPx > 0f) {
                QueueItemDismissGestureHandler(
                    scope = dismissScope,
                    density = density,
                    hapticView = hapticView,
                    hapticFeedbackEnabled = enableHapticFeedback,
                    offsetAnimatable = dismissOffsetAnimatable,
                    itemWidthPx = itemWidthPx,
                    onDismiss = onRemove,
                )
            } else {
                null
            }
        }

    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val currentOffsetPx = dismissOffsetAnimatable.value
    val revealWidthPx = (-currentOffsetPx).coerceAtLeast(0f)
    val revealProgress =
        if (density.density > 0f) {
            (revealWidthPx / (56.dp.value * density.density)).coerceIn(0f, 1f)
        } else {
            0f
        }

    val dismissBackgroundColor by animateColorAsState(
        targetValue =
            if (isSwipeTargeted) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
            },
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor",
    )
    val dismissIconAlpha by animateFloatAsState(
        targetValue = revealProgress * if (isSwipeTargeted) 1f else 0.88f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconAlpha",
    )
    val dismissIconScale by animateFloatAsState(
        targetValue = if (isSwipeTargeted) 1.08f else 0.95f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconScale",
    )

    val dismissGestureModifier =
        if (dismissEnabled && dismissHandler != null) {
            Modifier.pointerInput(window.queueItemKey, dismissHandler) {
                detectHorizontalDragGestures(
                    onDragStart = { dismissHandler.onDragStart() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dismissHandler.onHorizontalDrag(dragAmount)
                    },
                    onDragEnd = { dismissHandler.onDragEnd() },
                    onDragCancel = { dismissHandler.onDragCancel() },
                )
            }
        } else {
            Modifier
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.weight(1f),
        ) {
            if (revealWidthPx > 0f) {
                val revealWidthDp = with(density) { revealWidthPx.toDp() }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .height(ListItemHeight)
                            .width(revealWidthDp)
                            .clip(CircleShape)
                            .background(dismissBackgroundColor),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.remove_from_queue),
                        modifier =
                            Modifier
                                .padding(end = 16.dp)
                                .graphicsLayer {
                                    alpha = dismissIconAlpha
                                    scaleX = dismissIconScale
                                    scaleY = dismissIconScale
                                },
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = currentOffsetPx }
                        .then(dismissGestureModifier),
            ) {
                MediaMetadataListItem(
                    mediaMetadata = metadata,
                    isActive = isActive,
                    isPlaying = isActive,
                    cropToSquare = cropToSquare,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = currentOffsetPx == 0f) {
                                onPlay()
                            },
                )
            }
        }

        dragHandle()
    }
}
