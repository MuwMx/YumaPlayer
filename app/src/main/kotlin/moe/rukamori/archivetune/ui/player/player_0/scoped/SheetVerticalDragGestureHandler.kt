package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.state.PlayerSheetState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Инкапсулирует состояние жеста вертикального перетаскивания и разрешение целевой точки для шторки плеера.
 * Поведение идентично оригинальной реализации, но отвязано от ViewModel.
 */
internal class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val sheetMotionController: SheetMotionController,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onDraggingPlayerAreaChange: (Boolean) -> Unit,
    private val onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragSnapJob: Job? = null

    fun onDragStart() {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.stop()
        }
        onDraggingChange(true)
        onDraggingPlayerAreaChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f
    }

    fun onVerticalDrag(
        uptimeMillis: Long,
        position: Offset,
        dragAmount: Float
    ) {
        accumulatedDragYSinceStart += dragAmount
        val dragFrame = computeSheetVerticalDragFrame(
            currentTranslationY = currentSheetTranslationY.value,
            dragAmount = dragAmount,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )

        val safeTranslationY = dragFrame.translationY.coerceAtLeast(expandedYProvider())
        // Дополнительно страхуем фракцию раскрытия, чтобы она не превышала 100% (1f)
        val safeExpansionFraction = dragFrame.expansionFraction.coerceIn(0f, 1f)

        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.snapTo(
                translationYValue = safeTranslationY,
                expansionFractionValue = safeExpansionFraction
            )
        }

        velocityTracker.addPosition(uptimeMillis, Offset(0f, accumulatedDragYSinceStart))
    }

    fun onDragEnd() {
        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)

        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }

        // ИСПРАВЛЕНО: подняли порог до адекватных пикселей в секунду для флика
        val velocityThreshold = 1000f


        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            verticalVelocity = verticalVelocity,
            velocityThreshold = velocityThreshold,
            currentFraction = currentFraction,
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx
        )



        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                launch {
                    // ИСПРАВЛЕНО: передаем реальную скорость для плавного подхвата пружиной
                    onAnimateSheet(true, null, verticalVelocity)
                }
                onExpandSheetState()
            } else {
                val dynamicDamping = collapseSpringDampingForFraction(currentFraction)
                launch {
                    val initialSquash = collapseInitialSquashForFraction(currentFraction)
                    visualOvershootScaleY.snapTo(initialSquash)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    )
                }
                launch {
                    onAnimateSheet(
                        false,
                        spring(
                            dampingRatio = dynamicDamping,
                            stiffness = Spring.StiffnessLow
                        ),
                        verticalVelocity
                    )
                }
                onCollapseSheetState()
            }
        }

        accumulatedDragYSinceStart = 0f
    }

    fun onDragCancel() {
        onDragEnd()
    }
}

/**
 * Модификатор для привязки обработчика жестов к UI-компоненту.
 */
internal fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(
                    uptimeMillis = change.uptimeMillis,
                    position = change.position,
                    dragAmount = dragAmount
                )
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
