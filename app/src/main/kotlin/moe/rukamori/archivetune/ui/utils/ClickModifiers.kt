package moe.rukamori.archivetune.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Applies a snappy spring-based scale down (compression) on press.
 * Instantly reacts on touch down (0ms delay), bypassing scroll container press delays.
 * Zero ripples (indication = null), zero alpha/fade, purely responsive physical scaling.
 * Supports optional long click callback.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    pressedScale: Float = 0.94f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val isPressed = remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed.value) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BounceClickScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed.value = true
                waitForUpOrCancellation()
                isPressed.value = false
            }
        }
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // Disable default sharp ripple
            onClick = onClick,
            onLongClick = onLongClick
        )
}
