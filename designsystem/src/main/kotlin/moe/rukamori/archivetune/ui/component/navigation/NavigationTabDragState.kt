package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
    var candidateIndex by mutableIntStateOf(-1)
        internal set

    var rowWidthPx by mutableFloatStateOf(0f)
    var rowHeightPx by mutableFloatStateOf(0f)

    val selectorXAnimatable = Animatable(0f)
    val selectorWidthAnimatable = Animatable(0f)

    // Лонгпресс-скейл бара из Telegram: 1.019f
    val barScaleAnimatable = Animatable(1.0f)

    // 2D антимагнитное смещение бара от пальца
    val barTranslationXAnimatable = Animatable(0f)
    val barTranslationYAnimatable = Animatable(0f)

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    fun onDragStart(offsetX: Float, offsetY: Float, maxRepulsionXPx: Float, maxRepulsionYPx: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        isDragging = true
        haptics.longPress()

        val initialIndex = (offsetX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        candidateIndex = initialIndex
        val center = (initialIndex + 0.5f) * tabWidthPx

        val normX = if (rowWidthPx > 0f) ((offsetX - rowWidthPx / 2f) / (rowWidthPx / 2f)).coerceIn(-1f, 1f) else 0f
        val normY = if (rowHeightPx > 0f) ((offsetY - rowHeightPx / 2f) / (rowHeightPx / 2f)).coerceIn(-1f, 1f) else 0f

        coroutineScope.launch {
            selectorXAnimatable.snapTo(center)
            selectorWidthAnimatable.snapTo(tabWidthPx)
            barTranslationXAnimatable.snapTo(-normX * maxRepulsionXPx)
            barTranslationYAnimatable.snapTo(-normY * maxRepulsionYPx)

            barScaleAnimatable.animateTo(
                targetValue = 1.019f,
                animationSpec = tween(
                    durationMillis = 380,
                    easing = EaseOutQuint,
                ),
            )
        }
    }

    fun onDrag(
        dragAmountX: Float,
        currentTouchX: Float,
        currentTouchY: Float,
        maxRepulsionXPx: Float,
        maxRepulsionYPx: Float,
    ) {
        if (items.isEmpty() || tabWidthPx <= 0f) return

        val currentX = selectorXAnimatable.value + dragAmountX
        val firstCenter = 0.5f * tabWidthPx
        val lastCenter = (items.size - 0.5f) * tabWidthPx
        val clampedX = currentX.coerceIn(firstCenter, lastCenter)

        // Мгновенный расчет желе-растяжения без перезапуска анимаций
        val fraction = ((clampedX - firstCenter) / tabWidthPx).coerceIn(0f, (items.size - 1).toFloat())
        val baseIndex = fraction.toInt().coerceIn(0, items.size - 1)
        val t = (fraction - baseIndex).coerceIn(0f, 1f)
        val stretchFactor = 4f * t * (1f - t)
        val targetWidth = tabWidthPx * (1f + 0.28f * stretchFactor)

        // Антимагнитная репульсия (отталкивание в противоположную от пальца сторону)
        val normX = if (rowWidthPx > 0f) ((currentTouchX - rowWidthPx / 2f) / (rowWidthPx / 2f)).coerceIn(-1f, 1f) else 0f
        val normY = if (rowHeightPx > 0f) ((currentTouchY - rowHeightPx / 2f) / (rowHeightPx / 2f)).coerceIn(-1f, 1f) else 0f
        val targetRepulsionX = -normX * maxRepulsionXPx
        val targetRepulsionY = -normY * maxRepulsionYPx

        coroutineScope.launch {
            selectorXAnimatable.snapTo(clampedX)
            selectorWidthAnimatable.snapTo(targetWidth)
            barTranslationXAnimatable.snapTo(targetRepulsionX)
            barTranslationYAnimatable.snapTo(targetRepulsionY)
        }

        val newIndex = (clampedX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        if (newIndex != candidateIndex) {
            candidateIndex = newIndex
            haptics.click()
        }
    }

    fun onDragEnd() {
        if (items.isEmpty() || tabWidthPx <= 0f) {
            isDragging = false
            candidateIndex = -1
            return
        }

        val targetIndex = if (candidateIndex in items.indices) {
            candidateIndex
        } else {
            (selectorXAnimatable.value / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        }
        val targetCenter = (targetIndex + 0.5f) * tabWidthPx

        if (targetIndex in items.indices && !isSelected(items[targetIndex])) {
            onItemClick(items[targetIndex], false)
        }

        coroutineScope.launch {
            val animX = launch {
                selectorXAnimatable.animateTo(
                    targetValue = targetCenter,
                    animationSpec = spring(dampingRatio = 0.25f, stiffness = 250f),
                )
            }
            val animWidth = launch {
                selectorWidthAnimatable.animateTo(
                    targetValue = tabWidthPx,
                    animationSpec = spring(dampingRatio = 0.25f, stiffness = 250f),
                )
            }
            val animBarScale = launch {
                barScaleAnimatable.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(dampingRatio = 0.25f, stiffness = 250f),
                )
            }
            val animBarX = launch {
                barTranslationXAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
                )
            }
            val animBarY = launch {
                barTranslationYAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
                )
            }

            animX.join()
            animWidth.join()
            animBarScale.join()
            animBarX.join()
            animBarY.join()
            isDragging = false
            candidateIndex = -1
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
        dragState.rowHeightPx = size.height.toFloat()
    }
    .pointerInput(dragState) {
        val maxRepulsionXPx = 7.dp.toPx()
        val maxRepulsionYPx = 4.dp.toPx()

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragState.onDragStart(
                    offsetX = offset.x,
                    offsetY = offset.y,
                    maxRepulsionXPx = maxRepulsionXPx,
                    maxRepulsionYPx = maxRepulsionYPx,
                )
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragState.onDrag(
                    dragAmountX = dragAmount.x,
                    currentTouchX = change.position.x,
                    currentTouchY = change.position.y,
                    maxRepulsionXPx = maxRepulsionXPx,
                    maxRepulsionYPx = maxRepulsionYPx,
                )
            },
            onDragEnd = { dragState.onDragEnd() },
            onDragCancel = { dragState.onDragCancel() },
        )
    }

@Composable
internal fun NavigationDragSelectorOverlay(
    dragState: NavigationTabDragState,
    modifier: Modifier = Modifier,
) {
    val selectorColor = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha)
    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { NavigationTabSlotPaddingVertical.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                if (dragState.isDragging && dragState.tabWidthPx > 0f) {
                    val currentWidth = if (dragState.selectorWidthAnimatable.value > 0f) {
                        dragState.selectorWidthAnimatable.value
                    } else {
                        dragState.tabWidthPx
                    }
                    val currentX = dragState.selectorXAnimatable.value
                    val height = size.height - (verticalPaddingPx * 2)
                    val top = verticalPaddingPx
                    val left = currentX - currentWidth / 2f
                    val cornerRadius = CornerRadius(height / 2f, height / 2f)

                    drawRoundRect(
                        color = selectorColor,
                        topLeft = Offset(left, top),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )
                }
            },
    )
}