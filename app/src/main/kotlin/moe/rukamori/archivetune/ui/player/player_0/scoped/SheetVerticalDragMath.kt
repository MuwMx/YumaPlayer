package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Spring
import androidx.compose.ui.util.lerp
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import kotlin.math.abs

/**
 * Хранит снимок состояния шторки в конкретный кадр перетаскивания пальцем.
 */
internal data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

/**
 * Вычисляет новые координаты и прогресс раскрытия шторки на основе сдвига пальца.
 * Содержит логику "резинового" сопротивления (overscroll), если тянуть шторку за пределы экрана.
 */
internal fun computeSheetVerticalDragFrame(
    currentTranslationY: Float,
    dragAmount: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    val newY = (currentTranslationY + dragAmount)
        .coerceIn(
            expandedY - miniHeightPx * 0.2f,
            collapsedY + miniHeightPx * 0.2f
        )

    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)

    val dragRatio = (initialYOnDragStart - newY) / denominator

    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)

    return SheetVerticalDragFrame(
        translationY = newY,
        expansionFraction = newFraction
    )
}
/**
 * Определяет, куда должна примагнититься шторка (свернуться или развернуться)
 * после того, как пользователь отпустил палец.
 */
internal fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {

    if (currentSheetContentState == PlayerSheetState.COLLAPSED && accumulatedDragY > 0) {
        return PlayerSheetState.COLLAPSED
    }

    if (currentSheetContentState == PlayerSheetState.EXPANDED && accumulatedDragY <= 0f) {
        return PlayerSheetState.EXPANDED
    }

    if (abs(accumulatedDragY) <= minDragThresholdPx) {
        return currentSheetContentState
    }

    if (abs(verticalVelocity) > velocityThreshold) {
        return if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }

    return if (currentSheetContentState == PlayerSheetState.EXPANDED) {
        if (currentFraction > 0.75f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    } else {
        if (currentFraction > 0.25f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

internal fun collapseSpringDampingForFraction(currentFraction: Float): Float {
    return lerp(
        start = Spring.DampingRatioNoBouncy,
        stop = Spring.DampingRatioLowBouncy,
        fraction = currentFraction
    )
}

internal fun collapseInitialSquashForFraction(currentFraction: Float): Float {
    return lerp(1.0f, 0.97f, currentFraction)
}