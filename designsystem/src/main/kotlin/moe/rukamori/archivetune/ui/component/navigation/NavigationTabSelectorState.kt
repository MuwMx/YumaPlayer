package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationTabSelectorState(
    internal val items: List<Screens>,
    internal val isSelected: (Screens) -> Boolean,
    internal val onItemClick: (Screens, Boolean) -> Unit,
    private val haptics: YumaHaptics,
) {
    var isDragging by mutableStateOf(false)
        internal set
    var candidateIndex by mutableIntStateOf(-1)
        internal set

    var rowWidthPx by mutableFloatStateOf(0f)

    val selectorXAnimatable = Animatable(0f)
    val selectorWidthAnimatable = Animatable(0f)

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    suspend fun onDragStart(offsetX: Float) {
        haptics.longPress()

        val initialIndex = (offsetX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        candidateIndex = initialIndex
        val center = (initialIndex + 0.5f) * tabWidthPx

        selectorXAnimatable.snapTo(center)
        selectorWidthAnimatable.snapTo(tabWidthPx)
    }

    suspend fun onDrag(dragAmountX: Float) {
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

        selectorXAnimatable.snapTo(clampedX)
        selectorWidthAnimatable.snapTo(targetWidth)

        val newIndex = (clampedX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        if (newIndex != candidateIndex) {
            candidateIndex = newIndex
            haptics.click()
        }
    }

    fun onRelease() {
        val targetIndex = if (candidateIndex in items.indices) {
            candidateIndex
        } else {
            (selectorXAnimatable.value / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        }

        if (targetIndex in items.indices && !isSelected(items[targetIndex])) {
            onItemClick(items[targetIndex], false)
        }
    }

    suspend fun animateRelease() = coroutineScope {
        val targetIndex = if (candidateIndex in items.indices) {
            candidateIndex
        } else {
            (selectorXAnimatable.value / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        }
        val targetCenter = (targetIndex + 0.5f) * tabWidthPx

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

        animX.join()
        animWidth.join()
    }
}
