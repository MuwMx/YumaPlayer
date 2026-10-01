package moe.rukamori.archivetune.ui.player.player_0

import android.content.Context
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
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
    private val context: Context,
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

    private val haptics = MiniPlayerDismissHaptics(
        context = context,
        hapticView = hapticView,
        hapticFeedbackEnabled = hapticFeedbackEnabled
    )

    fun onDragStart() {
        accumulatedDragX = 0f
        haptics.cancel()
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

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)

                    haptics.triggerTension(dragFraction)

                    val maxTensionOffsetPx = 30f * density.density
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.FREE_DRAG
                    haptics.triggerRelease()

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
        haptics.cancel()
        offsetJob?.cancel()

        val dismissThreshold = screenWidthPx * 0.4f
        if (abs(accumulatedDragX) > dismissThreshold) {
            onDismissStarted()
            haptics.performHaptic(HapticFeedbackConstantsCompat.GESTURE_END)
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
