package moe.rukamori.archivetune.ui.player.player_0

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class MiniDismissDragPhase { IDLE, TENSION, FREE_DRAG }

internal class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticView: View,
    private val hapticFeedbackEnabled: Boolean,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissPlaylistAndShowUndo: () -> Unit,
    private val onDismissStarted: () -> Unit = {}
) {
    private var dragPhase: MiniDismissDragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f
    private var offsetJob: Job? = null
    private var lastTickTime: Long = 0L
    private var lastHapticTickOffset: Float = 0f
    private var isInDismissZone: Boolean = false

    private fun performHaptic(feedbackConstant: Int) {
        if (!hapticFeedbackEnabled) return
        ViewCompat.performHapticFeedback(hapticView, feedbackConstant)
    }

    fun onDragStart() {
        accumulatedDragX = 0f
        lastTickTime = 0L
        lastHapticTickOffset = 0f
        isInDismissZone = false
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (dragPhase == MiniDismissDragPhase.IDLE) {
                offsetAnimatable.snapTo(0f)
            } else {
                offsetAnimatable.stop()
            }
        }
        dragPhase = MiniDismissDragPhase.TENSION
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        val currentTime = System.currentTimeMillis()
        val tickDistancePx = 10f * density.density

        if (abs(accumulatedDragX - lastHapticTickOffset) >= tickDistancePx && (currentTime - lastTickTime) >= 25L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                performHaptic(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            } else {
                performHaptic(HapticFeedbackConstants.CLOCK_TICK)
            }
            lastHapticTickOffset = accumulatedDragX
            lastTickTime = currentTime
        }

        val dismissThreshold = screenWidthPx * 0.4f
        val currentlyInDismissZone = abs(accumulatedDragX) >= dismissThreshold
        if (currentlyInDismissZone != isInDismissZone) {
            isInDismissZone = currentlyInDismissZone
            if (currentlyInDismissZone) {
                performHaptic(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)
            } else {
                performHaptic(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE)
            }
        }

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.FREE_DRAG
                    performHaptic(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.animateTo(
                            targetValue = accumulatedDragX,
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                }
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }

            MiniDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        isInDismissZone = false
        offsetJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.4f
        if (abs(accumulatedDragX) > dismissThreshold) {
            onDismissStarted()
            performHaptic(HapticFeedbackConstantsCompat.GESTURE_END)
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismissPlaylistAndShowUndo()
                delay(300)
                offsetAnimatable.snapTo(0f)
            }
        } else {
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
}

@Composable
internal fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticView: View,
    hapticFeedbackEnabled: Boolean,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissPlaylistAndShowUndo: () -> Unit,
    onDismissStarted: () -> Unit = {}
): MiniPlayerDismissGestureHandler {
    val onDismissPlaylistAndShowUndoState = rememberUpdatedState(onDismissPlaylistAndShowUndo)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    return remember(scope, density, hapticView, hapticFeedbackEnabled, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissPlaylistAndShowUndo = { onDismissPlaylistAndShowUndoState.value() },
            onDismissStarted = { onDismissStartedState.value() }
        )
    }
}

internal fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}
