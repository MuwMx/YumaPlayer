package moe.rukamori.archivetune.ui.player.player_0

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
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
    private var lastVibeTime: Long = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrateContinuousFriction(dragFraction: Float) {
        if (!hapticFeedbackEnabled || vibrator?.hasVibrator() != true) return
        val now = System.currentTimeMillis()
        if (now - lastVibeTime < 20L) return
        lastVibeTime = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Прогрессивная амплитуда: от 70 до 140 по мере натяжения резинки
            val amplitude = lerp(70f, 140f, dragFraction).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(35L, amplitude)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator?.vibrate(effect, attributes)
            } else {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                vibrator?.vibrate(effect, audioAttributes)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    }

    private fun performHaptic(feedbackConstant: Int) {
        if (!hapticFeedbackEnabled) return
        ViewCompat.performHapticFeedback(hapticView, feedbackConstant)
    }

    fun onDragStart() {
        accumulatedDragX = 0f
        lastVibeTime = 0L
        vibrator?.cancel()
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

                    // Передаем dragFraction для нарастания силы
                    vibrateContinuousFriction(dragFraction)

                    val maxTensionOffsetPx = 30f * density.density
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    vibrator?.cancel()
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
        vibrator?.cancel()
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
    density: Density = LocalDensity.current,
    context: Context = LocalContext.current,
    hapticView: View = LocalView.current,
    hapticFeedbackEnabled: Boolean,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissPlaylistAndShowUndo: () -> Unit,
    onDismissStarted: () -> Unit = {}
): MiniPlayerDismissGestureHandler {
    val onDismissPlaylistAndShowUndoState = rememberUpdatedState(onDismissPlaylistAndShowUndo)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    return remember(scope, density, context, hapticView, hapticFeedbackEnabled, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            context = context,
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
    return this.pointerInput(true, handler) {
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