package moe.rukamori.archivetune.ui.player.player_0.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
    internal val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    internal val lyricsFraction: Animatable<Float, AnimationVector1D>,
    internal val queueFraction: Animatable<Float, AnimationVector1D>,
    internal val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val screenHeightPxProvider: () -> Float,
    internal val screenWidthPxProvider: () -> Float,
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
    private val onCollapseSheetState: () -> Unit,
    private val onExpandLyrics: () -> Unit,
    private val onCollapseLyrics: () -> Unit,
    private val onExpandQueue: () -> Unit = {},
    private val onCollapseQueue: () -> Unit = {}
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var initialLyricsFractionOnDragStart = 0f
    private var initialQueueFractionOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragSnapJob: Job? = null
    private var activeDragSheet: ActiveDragSheet = ActiveDragSheet.LYRICS

    fun onDragStart(position: Offset = Offset.Zero) {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.stop()
            lyricsFraction.stop()
            queueFraction.stop()
        }
        onDraggingChange(true)
        onDraggingPlayerAreaChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        initialLyricsFractionOnDragStart = lyricsFraction.value
        initialQueueFractionOnDragStart = queueFraction.value
        accumulatedDragYSinceStart = 0f

        val screenWidth = screenWidthPxProvider()
        activeDragSheet = when {
            lyricsFraction.value > 0.05f -> ActiveDragSheet.LYRICS
            queueFraction.value > 0.05f -> ActiveDragSheet.QUEUE
            position.x < screenWidth / 2f -> ActiveDragSheet.LYRICS
            else -> ActiveDragSheet.QUEUE
        }
    }

    fun onVerticalDrag(
        uptimeMillis: Long,
        dragAmount: Float
    ) {
        accumulatedDragYSinceStart += dragAmount
        velocityTracker.addPosition(uptimeMillis, Offset(0f, accumulatedDragYSinceStart))

        val expandedY = expandedYProvider()
        val collapsedY = collapsedYProvider()
        val miniHeightPx = miniHeightPxProvider()
        val layerTwoDistance = (screenHeightPxProvider() * 0.4f).coerceAtLeast(300f)

        val currentY = currentSheetTranslationY.value
        val targetFractionAnimatable = if (activeDragSheet == ActiveDragSheet.LYRICS) lyricsFraction else queueFraction
        val otherFractionAnimatable = if (activeDragSheet == ActiveDragSheet.LYRICS) queueFraction else lyricsFraction
        val currentTargetFraction = targetFractionAnimatable.value

        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (dragAmount < 0) {
                if (currentY > expandedY) {
                    val dragFrame = computeSheetVerticalDragFrame(
                        currentTranslationY = currentY,
                        dragAmount = dragAmount,
                        expandedY = expandedY,
                        collapsedY = collapsedY,
                        miniHeightPx = miniHeightPx,
                        initialFractionOnDragStart = initialFractionOnDragStart,
                        initialYOnDragStart = initialYOnDragStart
                    )
                    if (dragFrame.translationY < expandedY) {
                        val overshootPx = expandedY - dragFrame.translationY
                        val delta = overshootPx / layerTwoDistance
                        val newFraction = (currentTargetFraction + delta).coerceIn(0f, 1f)
                        sheetMotionController.snapTo(expandedY, 1f)
                        targetFractionAnimatable.snapTo(newFraction)
                        if (otherFractionAnimatable.value > 0f) {
                            otherFractionAnimatable.snapTo(0f)
                        }
                    } else {
                        val safeTranslationY = dragFrame.translationY.coerceAtLeast(expandedY)
                        val safeExpansionFraction = dragFrame.expansionFraction.coerceIn(0f, 1f)
                        sheetMotionController.snapTo(safeTranslationY, safeExpansionFraction)
                        if (lyricsFraction.value > 0f) lyricsFraction.snapTo(0f)
                        if (queueFraction.value > 0f) queueFraction.snapTo(0f)
                    }
                } else {
                    val delta = -dragAmount / layerTwoDistance
                    val newFraction = (currentTargetFraction + delta).coerceIn(0f, 1f)
                    if (currentY != expandedY || playerContentExpansionFraction.value != 1f) {
                        sheetMotionController.snapTo(expandedY, 1f)
                    }
                    targetFractionAnimatable.snapTo(newFraction)
                    if (otherFractionAnimatable.value > 0f) {
                        otherFractionAnimatable.snapTo(0f)
                    }
                }
            } else {
                if (currentTargetFraction > 0f) {
                    val delta = dragAmount / layerTwoDistance
                    if (currentTargetFraction >= delta) {
                        val newFraction = (currentTargetFraction - delta).coerceIn(0f, 1f)
                        if (currentY != expandedY || playerContentExpansionFraction.value != 1f) {
                            sheetMotionController.snapTo(expandedY, 1f)
                        }
                        targetFractionAnimatable.snapTo(newFraction)
                        if (otherFractionAnimatable.value > 0f) {
                            otherFractionAnimatable.snapTo(0f)
                        }
                    } else {
                        targetFractionAnimatable.snapTo(0f)
                        if (otherFractionAnimatable.value > 0f) {
                            otherFractionAnimatable.snapTo(0f)
                        }
                        sheetMotionController.snapTo(expandedY, 1f)
                    }
                } else {
                    val dragFrame = computeSheetVerticalDragFrame(
                        currentTranslationY = currentY,
                        dragAmount = dragAmount,
                        expandedY = expandedY,
                        collapsedY = collapsedY,
                        miniHeightPx = miniHeightPx,
                        initialFractionOnDragStart = initialFractionOnDragStart,
                        initialYOnDragStart = initialYOnDragStart
                    )
                    val safeTranslationY = dragFrame.translationY.coerceAtLeast(expandedY)
                    val safeExpansionFraction = dragFrame.expansionFraction.coerceIn(0f, 1f)
                    if (lyricsFraction.value > 0f) lyricsFraction.snapTo(0f)
                    if (queueFraction.value > 0f) queueFraction.snapTo(0f)
                    sheetMotionController.snapTo(safeTranslationY, safeExpansionFraction)
                }
            }
        }
    }

    fun onDragEnd(customVelocity: Float? = null) {
        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)

        val rawVelocity = customVelocity ?: velocityTracker.calculateVelocity().y
        val verticalVelocity = if (rawVelocity.isNaN()) 0f else rawVelocity
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        val velocityThreshold = 500f
        val layerTwoDistance = (screenHeightPxProvider() * 0.4f).coerceAtLeast(300f)
        val l2Velocity = (-verticalVelocity / layerTwoDistance).coerceIn(-20f, 20f)

        val activeSheet = when {
            lyricsFraction.value > 0f && queueFraction.value == 0f -> ActiveDragSheet.LYRICS
            queueFraction.value > 0f && lyricsFraction.value == 0f -> ActiveDragSheet.QUEUE
            else -> activeDragSheet
        }

        val targetFractionAnimatable = if (activeSheet == ActiveDragSheet.LYRICS) lyricsFraction else queueFraction
        val currentTargetFraction = targetFractionAnimatable.value
        val initialFraction = if (activeSheet == ActiveDragSheet.LYRICS) initialLyricsFractionOnDragStart else initialQueueFractionOnDragStart
        val onExpandTarget = if (activeSheet == ActiveDragSheet.LYRICS) onExpandLyrics else onExpandQueue
        val onCollapseTarget = if (activeSheet == ActiveDragSheet.LYRICS) onCollapseLyrics else onCollapseQueue

        if (currentTargetFraction > 0f) {
            val targetExpanded = when {
                verticalVelocity < -velocityThreshold -> true
                verticalVelocity > velocityThreshold -> false
                initialFraction > 0.5f -> currentTargetFraction > 0.5f
                else -> currentTargetFraction > 0.3f
            }

            scope.launch {
                launch {
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }

                if (targetExpanded) {
                    launch {
                        targetFractionAnimatable.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialVelocity = l2Velocity
                        )
                    }
                    if (currentFraction < 0.99f || currentSheetTranslationY.value != expandedYProvider()) {
                        launch {
                            onAnimateSheet(
                                true,
                                spring(
                                    dampingRatio = 0.78f,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                verticalVelocity
                            )
                        }
                    }
                    onExpandSheetState()
                    onExpandTarget()
                } else {
                    launch {
                        targetFractionAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialVelocity = l2Velocity
                        )
                    }
                    onCollapseTarget()
                    if (currentFraction < 0.99f || currentSheetTranslationY.value > expandedYProvider()) {
                        val targetState = resolveVerticalSheetTargetState(
                            currentSheetContentState = currentSheetStateProvider(),
                            accumulatedDragY = accumulatedDragYSinceStart,
                            minDragThresholdPx = minDragThresholdPx,
                            verticalVelocity = verticalVelocity,
                            velocityThreshold = velocityThreshold,
                            currentFraction = currentFraction
                        )
                        if (targetState == PlayerSheetState.EXPANDED) {
                            launch {
                                onAnimateSheet(
                                    true,
                                    spring(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    verticalVelocity
                                )
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
                    } else {
                        onExpandSheetState()
                    }
                }
            }
        } else {
            val targetState = resolveVerticalSheetTargetState(
                currentSheetContentState = currentSheetStateProvider(),
                accumulatedDragY = accumulatedDragYSinceStart,
                minDragThresholdPx = minDragThresholdPx,
                verticalVelocity = verticalVelocity,
                velocityThreshold = velocityThreshold,
                currentFraction = currentFraction
            )

            scope.launch {
                if (targetState == PlayerSheetState.EXPANDED) {
                    launch {
                        visualOvershootScaleY.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        onAnimateSheet(
                            true,
                            spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            verticalVelocity
                        )
                    }
                    onExpandSheetState()
                    onCollapseLyrics()
                    onCollapseQueue()
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
                    onCollapseLyrics()
                    onCollapseQueue()
                }
            }
        }

        accumulatedDragYSinceStart = 0f
    }

    fun onDragCancel() {
        onDragEnd()
    }

    fun createNestedScrollConnection(
        canDragProvider: () -> Boolean,
        targetSheet: ActiveDragSheet = ActiveDragSheet.LYRICS
    ): NestedScrollConnection = createNestedScrollConnection(
        handler = this,
        canDragProvider = canDragProvider,
        targetSheet = targetSheet
    )
}
