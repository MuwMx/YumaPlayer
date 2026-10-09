package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationBarGestureState(
    val selectorState: NavigationTabSelectorState,
    val repulsionState: NavigationBarRepulsionState,
    private val coroutineScope: CoroutineScope,
) {
    var isDragging by mutableStateOf(false)
        internal set

    private var settlingJob: Job? = null
    val barScaleAnimatable get() = repulsionState.barScaleAnimatable
    val barTranslationXAnimatable get() = repulsionState.barTranslationXAnimatable
    val barTranslationYAnimatable get() = repulsionState.barTranslationYAnimatable

    fun onDragStart(offsetX: Float, offsetY: Float, maxRepulsionXPx: Float, maxRepulsionYPx: Float) {
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) return
        settlingJob?.cancel()
        settlingJob = null

        isDragging = true
        selectorState.isDragging = true

        coroutineScope.launch {
            selectorState.onDragStart(offsetX)
            repulsionState.onDragStart(
                offsetX = offsetX,
                offsetY = offsetY,
                maxRepulsionXPx = maxRepulsionXPx,
                maxRepulsionYPx = maxRepulsionYPx,
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
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) return

        coroutineScope.launch {
            selectorState.onDrag(dragAmountX)
            repulsionState.onDrag(
                currentTouchX = currentTouchX,
                currentTouchY = currentTouchY,
                maxRepulsionXPx = maxRepulsionXPx,
                maxRepulsionYPx = maxRepulsionYPx,
            )
        }
    }

    fun onDragEnd() {
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) {
            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
            return
        }

        selectorState.onRelease()

        settlingJob?.cancel()
        settlingJob = coroutineScope.launch {
            val animSelector = launch { selectorState.animateRelease() }
            val animRepulsion = launch { repulsionState.animateRelease() }

            animSelector.join()
            animRepulsion.join()

            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
        }
    }

    fun onDragCancel() {
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) {
            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
            return
        }

        selectorState.onCancel()

        settlingJob?.cancel()
        settlingJob = coroutineScope.launch {
            val animSelector = launch { selectorState.animateCancel() }
            val animRepulsion = launch { repulsionState.animateRelease() }

            animSelector.join()
            animRepulsion.join()

            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
        }
    }
}

@Composable
internal fun rememberNavigationBarGestureState(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptics: YumaHaptics = LocalYumaHaptics.current,
): NavigationBarGestureState {
    val currentIsSelected by androidx.compose.runtime.rememberUpdatedState(isSelected)
    val currentOnItemClick by androidx.compose.runtime.rememberUpdatedState(onItemClick)
    val currentHaptics by androidx.compose.runtime.rememberUpdatedState(haptics)
    val animationsDisabled = moe.rukamori.archivetune.LocalAnimationsDisabled.current
    val currentAnimationsDisabled by androidx.compose.runtime.rememberUpdatedState(animationsDisabled)

    val selectorState =
        remember(items) {
            NavigationTabSelectorState(
                items = items,
                isSelectedProvider = { currentIsSelected },
                onItemClickProvider = { currentOnItemClick },
                hapticsProvider = { currentHaptics },
                animationsDisabledProvider = { currentAnimationsDisabled },
                coroutineScope = coroutineScope,
            )
        }
    val repulsionState =
        remember {
            NavigationBarRepulsionState()
        }
    return remember(selectorState, repulsionState) {
        NavigationBarGestureState(
            selectorState = selectorState,
            repulsionState = repulsionState,
            coroutineScope = coroutineScope,
        )
    }
}

internal fun Modifier.navigationTabDragGestures(
    gestureState: NavigationBarGestureState,
): Modifier = this
    .onSizeChanged { size ->
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        gestureState.selectorState.rowWidthPx = width
        gestureState.repulsionState.rowWidthPx = width
        gestureState.repulsionState.rowHeightPx = height
    }
    .pointerInput(gestureState) {
        val maxRepulsionXPx = 7.dp.toPx()
        val maxRepulsionYPx = 4.dp.toPx()

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                gestureState.onDragStart(
                    offsetX = offset.x,
                    offsetY = offset.y,
                    maxRepulsionXPx = maxRepulsionXPx,
                    maxRepulsionYPx = maxRepulsionYPx,
                )
            },
            onDrag = { change, dragAmount ->
                change.consume()
                gestureState.onDrag(
                    dragAmountX = dragAmount.x,
                    currentTouchX = change.position.x,
                    currentTouchY = change.position.y,
                    maxRepulsionXPx = maxRepulsionXPx,
                    maxRepulsionYPx = maxRepulsionYPx,
                )
            },
            onDragEnd = { gestureState.onDragEnd() },
            onDragCancel = { gestureState.onDragCancel() },
        )
    }

@Deprecated(
    message = "Use rememberNavigationBarGestureState instead",
    replaceWith = ReplaceWith("rememberNavigationBarGestureState(items, isSelected, onItemClick, coroutineScope, haptics)"),
)
@Composable
internal fun rememberNavigationTabDragState(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptics: YumaHaptics = LocalYumaHaptics.current,
): NavigationBarGestureState = rememberNavigationBarGestureState(items, isSelected, onItemClick, coroutineScope, haptics)

internal typealias NavigationTabDragState = NavigationBarGestureState
