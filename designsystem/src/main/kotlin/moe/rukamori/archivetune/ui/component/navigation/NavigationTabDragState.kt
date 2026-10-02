package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationTabDragState(
    private val items: List<Screens>,
    private val isSelected: (Screens) -> Boolean,
    private val onItemClick: (Screens, Boolean) -> Unit,
    private val coroutineScope: CoroutineScope,
    private val haptics: YumaHaptics,
) {
    var isDragging by mutableStateOf(false)
        internal set
    var rowWidthPx by mutableFloatStateOf(0f)
    val selectorXAnimatable = Animatable(0f)
    val selectorWidthAnimatable = Animatable(0f)

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    fun onDragStart(offsetX: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        isDragging = true
        haptics.longPress()
        val initialIndex = (offsetX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        val center = (initialIndex + 0.5f) * tabWidthPx
        coroutineScope.launch {
            selectorXAnimatable.snapTo(center)
            selectorWidthAnimatable.snapTo(tabWidthPx)
        }
        if (!isSelected(items[initialIndex])) {
            haptics.click()
            onItemClick(items[initialIndex], false)
        }
    }

    fun onDrag(dragAmountX: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        val currentX = selectorXAnimatable.value + dragAmountX
        val firstCenter = 0.5f * tabWidthPx
        val lastCenter = (items.size - 0.5f) * tabWidthPx
        val clampedX = currentX.coerceIn(firstCenter, lastCenter)

        val fraction = ((clampedX - firstCenter) / tabWidthPx).coerceIn(0f, (items.size - 1).toFloat())
        val baseIndex = fraction.toInt().coerceIn(0, items.size - 1)
        val t = (fraction - baseIndex).coerceIn(0f, 1f)
        val stretchFactor = 4f * t * (1f - t)
        val targetWidth = tabWidthPx * (1f + 0.25f * stretchFactor)

        coroutineScope.launch {
            selectorXAnimatable.snapTo(clampedX)
            selectorWidthAnimatable.animateTo(
                targetValue = targetWidth,
                animationSpec = spring(
                    dampingRatio = 0.25f,
                    stiffness = 250f,
                ),
            )
        }
        val newIndex = (clampedX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        if (!isSelected(items[newIndex])) {
            haptics.click()
            onItemClick(items[newIndex], false)
        }
    }

    fun onDragEnd() {
        if (items.isEmpty() || tabWidthPx <= 0f) {
            isDragging = false
            return
        }
        val nearestIndex = (selectorXAnimatable.value / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        val targetCenter = (nearestIndex + 0.5f) * tabWidthPx
        coroutineScope.launch {
            val animX = launch {
                selectorXAnimatable.animateTo(
                    targetValue = targetCenter,
                    animationSpec = spring(
                        dampingRatio = 0.25f,
                        stiffness = 250f,
                    ),
                )
            }
            val animWidth = launch {
                selectorWidthAnimatable.animateTo(
                    targetValue = tabWidthPx,
                    animationSpec = spring(
                        dampingRatio = 0.25f,
                        stiffness = 250f,
                    ),
                )
            }
            animX.join()
            animWidth.join()
            isDragging = false
        }
    }

    fun onDragCancel() {
        onDragEnd()
    }
}

@Composable
internal fun rememberNavigationTabDragState(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptics: YumaHaptics = LocalYumaHaptics.current,
): NavigationTabDragState {
    return remember(items, isSelected, onItemClick, coroutineScope, haptics) {
        NavigationTabDragState(
            items = items,
            isSelected = isSelected,
            onItemClick = onItemClick,
            coroutineScope = coroutineScope,
            haptics = haptics,
        )
    }
}

internal fun Modifier.navigationTabDragGestures(
    dragState: NavigationTabDragState,
): Modifier = this
    .onSizeChanged { size ->
        dragState.rowWidthPx = size.width.toFloat()
    }
    .pointerInput(dragState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragState.onDragStart(offset.x)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragState.onDrag(dragAmount.x)
            },
            onDragEnd = {
                dragState.onDragEnd()
            },
            onDragCancel = {
                dragState.onDragCancel()
            },
        )
    }

@Composable
internal fun NavigationDragSelectorOverlay(
    dragState: NavigationTabDragState,
    modifier: Modifier = Modifier,
) {
    if (dragState.isDragging && dragState.tabWidthPx > 0f) {
        val currentWidth = if (dragState.selectorWidthAnimatable.value > 0f) {
            dragState.selectorWidthAnimatable.value
        } else {
            dragState.tabWidthPx
        }
        val tabWidthDp = with(LocalDensity.current) { currentWidth.toDp() }
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(tabWidthDp)
                .padding(vertical = NavigationTabSlotPaddingVertical)
                .graphicsLayer {
                    translationX = dragState.selectorXAnimatable.value - currentWidth / 2f
                }
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha),
                    shape = CircleShape,
                ),
        )
    }
}
