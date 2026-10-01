package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerProgressSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeStarted: () -> Unit,
    onValueChangeFinished: () -> Unit,
    accentColor: Color,
    progressFractionProvider: () -> Float,
    markerMs: Long?,
    durationMs: Long,
    maxRange: Float,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    var isDragged by remember { mutableStateOf(false) }
    val isInteracting = isPressed || isDragged

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 7.dp else 4.dp,
        animationSpec = tween(durationMillis = 250),
        label = "PlayerProgressSliderTrackHeight",
    )

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeStarted by rememberUpdatedState(onValueChangeStarted)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValueRange by rememberUpdatedState(valueRange)

    val rangeSpan = valueRange.endInclusive - valueRange.start
    val currentFraction = if (rangeSpan > 0f) {
        ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentFraction, 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            currentOnValueChangeFinished()
                        }
                    },
                    onTap = { offset ->
                        currentOnValueChangeStarted()
                        val range = currentValueRange
                        val span = range.endInclusive - range.start
                        val fraction = if (size.width > 0) (offset.x / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                        val newValue = if (span > 0f) (range.start + fraction * span).coerceIn(range) else range.start
                        currentOnValueChange(newValue)
                        currentOnValueChangeFinished()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isPressed = false
                        isDragged = true
                        currentOnValueChangeStarted()
                        val range = currentValueRange
                        val span = range.endInclusive - range.start
                        val fraction = if (size.width > 0) (offset.x / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                        val newValue = if (span > 0f) (range.start + fraction * span).coerceIn(range) else range.start
                        currentOnValueChange(newValue)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val range = currentValueRange
                        val span = range.endInclusive - range.start
                        val fraction = if (size.width > 0) (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                        val newValue = if (span > 0f) (range.start + fraction * span).coerceIn(range) else range.start
                        currentOnValueChange(newValue)
                    },
                    onDragEnd = {
                        isDragged = false
                        currentOnValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragged = false
                        currentOnValueChangeFinished()
                    },
                )
            },
    ) {
        val trackHeightPx = trackHeight.toPx()
        val top = (size.height - trackHeightPx) / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(0f, top),
            size = Size(size.width, trackHeightPx),
            cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f),
        )

        if (markerMs != null && durationMs > 0L) {
            val markerFraction = (markerMs / maxRange).coerceIn(0f, 1f)
            if (markerFraction < 1f) {
                val startX = size.width * markerFraction
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.45f),
                    topLeft = Offset(startX, top),
                    size = Size(size.width - startX, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f),
                )
            }
        }

        val fraction = progressFractionProvider()
        val fillWidth = size.width * fraction
        drawRoundRect(
            color = if (isInteracting) accentColor else Color.White,
            topLeft = Offset(0f, top),
            size = Size(fillWidth, trackHeightPx),
            cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f),
        )
    }
}
