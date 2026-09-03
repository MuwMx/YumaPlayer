/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class MiniPlayerDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

internal class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticView: View,
    private val hapticFeedbackEnabled: Boolean,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val itemWidthPx: Float,
    private val onDismiss: () -> Unit,
) {
    private var dragPhase: MiniPlayerDismissDragPhase = MiniPlayerDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f

    var isInDismissZone: Boolean by mutableStateOf(false)
        private set

    var isDismissing: Boolean by mutableStateOf(false)
        private set

    private fun performHaptic(feedbackConstant: Int) {
        if (hapticFeedbackEnabled) {
            ViewCompat.performHapticFeedback(hapticView, feedbackConstant)
        }
    }

    fun onDragStart() {
        if (isDismissing) return
        dragPhase = MiniPlayerDismissDragPhase.TENSION
        accumulatedDragX = 0f
        isInDismissZone = false
        scope.launch { offsetAnimatable.stop() }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        if (isDismissing) return
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniPlayerDismissDragPhase.TENSION -> {
                val tensionThresholdPx = 60f * density.density
                if (abs(accumulatedDragX) < tensionThresholdPx) {
                    val maxTensionOffsetPx = 20f * density.density
                    val dragFraction = (abs(accumulatedDragX) / tensionThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = if (accumulatedDragX >= 0) maxTensionOffsetPx * dragFraction else -maxTensionOffsetPx * dragFraction
                    scope.launch {
                        offsetAnimatable.snapTo(tensionOffset)
                    }
                } else {
                    dragPhase = MiniPlayerDismissDragPhase.SNAPPING
                }
            }

            MiniPlayerDismissDragPhase.SNAPPING -> {
                performHaptic(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec =
                            spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessLow,
                            ),
                    )
                }
                dragPhase = MiniPlayerDismissDragPhase.FREE_DRAG
            }

            MiniPlayerDismissDragPhase.FREE_DRAG -> {
                val dismissThreshold = itemWidthPx * 0.40f
                val nowInZone = abs(accumulatedDragX) > dismissThreshold
                if (nowInZone != isInDismissZone) {
                    isInDismissZone = nowInZone
                    performHaptic(
                        if (nowInZone) {
                            HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE
                        } else {
                            HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE
                        },
                    )
                }
                scope.launch {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                }
            }

            MiniPlayerDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        if (isDismissing) return
        dragPhase = MiniPlayerDismissDragPhase.IDLE
        val dismissThreshold = itemWidthPx * 0.40f

        if (abs(accumulatedDragX) > dismissThreshold) {
            isDismissing = true
            performHaptic(HapticFeedbackConstantsCompat.GESTURE_END)
            val targetOffset = if (accumulatedDragX > 0) itemWidthPx else -itemWidthPx
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = targetOffset,
                    animationSpec =
                        tween(
                            durationMillis = 180,
                            easing = FastOutSlowInEasing,
                        ),
                )
                onDismiss()
                delay(300)
                offsetAnimatable.snapTo(0f)
                isDismissing = false
                isInDismissZone = false
            }
        } else {
            isInDismissZone = false
            scope.launch {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                )
            }
        }
    }

    fun onDragCancel() {
        if (isDismissing) return
        dragPhase = MiniPlayerDismissDragPhase.IDLE
        isInDismissZone = false
        scope.launch {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
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
    itemWidthPx: Float,
    onDismiss: () -> Unit,
): MiniPlayerDismissGestureHandler {
    return remember(scope, density, hapticView, hapticFeedbackEnabled, offsetAnimatable, itemWidthPx, onDismiss) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticView = hapticView,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            offsetAnimatable = offsetAnimatable,
            itemWidthPx = itemWidthPx,
            onDismiss = onDismiss,
        )
    }
}

internal fun Modifier.miniPlayerDismissGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler?,
): Modifier {
    if (!enabled || handler == null) return this
    return pointerInput(handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { _, dragAmount -> handler.onHorizontalDrag(dragAmount) },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() },
        )
    }
}
