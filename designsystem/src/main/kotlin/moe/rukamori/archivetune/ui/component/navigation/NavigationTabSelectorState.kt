package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationTabSelectorState(
    internal val items: List<Screens>,
    private val isSelectedProvider: () -> (Screens) -> Boolean,
    private val onItemClickProvider: () -> (Screens, Boolean) -> Unit,
    private val hapticsProvider: () -> YumaHaptics,
    private val animationsDisabledProvider: () -> Boolean,
    private val coroutineScope: CoroutineScope,
) {
    var isDragging by mutableStateOf(false)
        internal set
    var candidateIndex by mutableIntStateOf(-1)
        internal set

    var hoveredSelectedTab: Screens? = null
        internal set
    private var dwellJob: Job? = null

    var rowWidthPx by mutableFloatStateOf(0f)
    var isInitialized by mutableStateOf(false)

    val selectorXAnimatable = Animatable(0f)
    val selectorWidthAnimatable = Animatable(0f)

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    private fun <T> settleSpring(): AnimationSpec<T> =
        if (animationsDisabledProvider()) {
            snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        }

    fun syncToTab(index: Int, animate: Boolean = true) {
        if (isDragging || index !in items.indices || tabWidthPx <= 0f) return
        val targetCenter = (index + 0.5f) * tabWidthPx
        if (!isInitialized) {
            coroutineScope.launch {
                selectorXAnimatable.snapTo(targetCenter)
                selectorWidthAnimatable.snapTo(tabWidthPx)
                isInitialized = true
            }
        } else if (animate) {
            coroutineScope.launch {
                selectorXAnimatable.animateTo(targetCenter, settleSpring())
                selectorWidthAnimatable.animateTo(tabWidthPx, settleSpring())
            }
        } else {
            coroutineScope.launch {
                selectorXAnimatable.snapTo(targetCenter)
                selectorWidthAnimatable.snapTo(tabWidthPx)
            }
        }
    }

    fun onTabTap(screen: Screens, isSelected: Boolean) {
        val index = items.indexOf(screen)
        if (index in items.indices && tabWidthPx > 0f) {
            val targetCenter = (index + 0.5f) * tabWidthPx
            coroutineScope.launch {
                val animX = launch { selectorXAnimatable.animateTo(targetCenter, settleSpring()) }
                val animW = launch { selectorWidthAnimatable.animateTo(tabWidthPx, settleSpring()) }
                animX.join()
                animW.join()
            }
        }
        onItemClickProvider()(screen, isSelected)
    }

    suspend fun onDragStart(offsetX: Float) {
        hapticsProvider().longPress()
        isDragging = true
        hoveredSelectedTab = null
        dwellJob?.cancel()

        val initialIndex = (offsetX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        candidateIndex = initialIndex
        val center = (initialIndex + 0.5f) * tabWidthPx

        selectorXAnimatable.snapTo(center)
        selectorWidthAnimatable.snapTo(tabWidthPx)

        startDwell(initialIndex)
    }

    suspend fun onDrag(dragAmountX: Float) {
        val currentX = selectorXAnimatable.value + dragAmountX
        val firstCenter = 0.5f * tabWidthPx
        val lastCenter = (items.size - 0.5f) * tabWidthPx
        val clampedX = currentX.coerceIn(firstCenter, lastCenter)

        val fraction = ((clampedX - firstCenter) / tabWidthPx).coerceIn(0f, (items.size - 1).toFloat())
        val baseIndex = fraction.toInt().coerceIn(0, items.size - 1)
        val t = (fraction - baseIndex).coerceIn(0f, 1f)
        val stretchFactor = 4f * t * (1f - t)
        val targetWidth = tabWidthPx * (1f + 0.18f * stretchFactor)

        selectorXAnimatable.snapTo(clampedX)
        selectorWidthAnimatable.snapTo(targetWidth)

        val newIndex = (clampedX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        if (newIndex != candidateIndex) {
            dwellJob?.cancel()
            candidateIndex = newIndex
            hapticsProvider().click()
            startDwell(newIndex)
        }
    }

    private fun startDwell(index: Int) {
        if (index !in items.indices) return
        val candidate = items[index]
        if (isSelectedProvider()(candidate) || hoveredSelectedTab == candidate) return

        dwellJob?.cancel()
        dwellJob = coroutineScope.launch {
            delay(DWELL_DELAY_MS)
            if (candidateIndex == index && !isSelectedProvider()(candidate) && hoveredSelectedTab != candidate) {
                hoveredSelectedTab = candidate
                onItemClickProvider()(candidate, false)
            }
        }
    }

    fun onRelease() {
        dwellJob?.cancel()
        val targetIndex = if (candidateIndex in items.indices) {
            candidateIndex
        } else {
            (selectorXAnimatable.value / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        }

        if (targetIndex in items.indices) {
            val candidate = items[targetIndex]
            if (!isSelectedProvider()(candidate) && hoveredSelectedTab != candidate) {
                hoveredSelectedTab = candidate
                onItemClickProvider()(candidate, false)
            }
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
                animationSpec = settleSpring(),
            )
        }
        val animWidth = launch {
            selectorWidthAnimatable.animateTo(
                targetValue = tabWidthPx,
                animationSpec = settleSpring(),
            )
        }

        animX.join()
        animWidth.join()
    }

    fun onCancel() {
        dwellJob?.cancel()
        candidateIndex = -1
        hoveredSelectedTab = null
    }

    suspend fun animateCancel() = coroutineScope {
        val confirmedIndex = items.indexOfFirst { isSelectedProvider()(it) }.coerceAtLeast(0)
        val targetCenter = (confirmedIndex + 0.5f) * tabWidthPx

        val animX = launch {
            selectorXAnimatable.animateTo(
                targetValue = targetCenter,
                animationSpec = settleSpring(),
            )
        }
        val animWidth = launch {
            selectorWidthAnimatable.animateTo(
                targetValue = tabWidthPx,
                animationSpec = settleSpring(),
            )
        }

        animX.join()
        animWidth.join()
    }

    companion object {
        const val DWELL_DELAY_MS = 250L
    }
}
