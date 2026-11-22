/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.AudioTap
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.lowMidHighAverages
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ListenToAudioTap(active: Boolean) {
    DisposableEffect(active) {
        if (active) AudioTap.listen()
        onDispose {
            if (active) AudioTap.stopListening()
        }
    }
}

@Composable
fun EqualizerSketch(
    eq: AudioLabEqSettings,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val scratch = remember { FloatArray(512) }
    val pulseTick = remember { mutableFloatStateOf(0f) }

    val shouldAnimate = eq.enabled && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            pulseTick.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                pulseTick.floatValue = ((now - startNanos) / 1_000_000_000f)
            }
        }
    }

    val (low, mid, high) = eq.lowMidHighAverages()
    val bands = remember(low, mid, high) { floatArrayOf(low, mid, high) }

    Canvas(modifier = modifier) {
        val tick = pulseTick.floatValue
        val liveLevel = if (shouldAnimate) AudioTap.level(scratch) else 0f
        val activeColor = if (eq.enabled) primary else onSurfaceVariant.copy(alpha = 0.55f)
        val trackColor = onSurfaceVariant.copy(alpha = 0.25f)
        val strokeWidth = 2.5.dp.toPx()
        val thumbRadius = 4.dp.toPx()

        val w = size.width
        val h = size.height
        val stepX = w / 4f
        val topY = h * 0.16f
        val bottomY = h * 0.84f
        val tiltX = w * 0.06f

        for (i in 0..2) {
            val cx = stepX * (i + 1)
            val start = Offset(cx - tiltX, bottomY)
            val end = Offset(cx + tiltX, topY)

            drawLine(
                color = trackColor,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            val modulation = if (shouldAnimate) {
                val wave = sin(tick * 5f + i * 1.4f) * 0.18f
                (liveLevel * 0.65f + wave).coerceIn(-0.35f, 0.35f)
            } else {
                0f
            }
            val norm = ((bands[i] + modulation) * 0.5f + 0.5f).coerceIn(0.08f, 0.92f)
            val thumb = Offset(
                x = start.x + (end.x - start.x) * norm,
                y = start.y + (end.y - start.y) * norm,
            )

            drawLine(
                color = activeColor,
                start = start,
                end = thumb,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = surface,
                radius = thumbRadius + 1.dp.toPx(),
                center = thumb,
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = thumb,
            )
        }
    }
}

@Composable
fun FxSketch(
    activeCount: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val sweepState = remember { mutableFloatStateOf(0f) }

    val shouldAnimate = activeCount > 0 && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            sweepState.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                sweepState.floatValue = ((now - startNanos) / 1_600_000_000f) % 1f
            }
        }
    }

    Canvas(modifier = modifier) {
        val phase = sweepState.floatValue
        val isActive = activeCount > 0
        val strokeColor = if (isActive) primary else onSurfaceVariant.copy(alpha = 0.55f)
        val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        val w = size.width
        val h = size.height
        val top = h * 0.24f
        val bottom = h * 0.76f
        val midY = (top + bottom) * 0.5f

        val fPath = Path().apply {
            moveTo(w * 0.22f, bottom)
            lineTo(w * 0.22f, top)
            lineTo(w * 0.44f, top)
            moveTo(w * 0.22f, midY)
            lineTo(w * 0.40f, midY)
        }
        drawPath(path = fPath, color = strokeColor, style = stroke)

        val xPath = Path().apply {
            moveTo(w * 0.54f, top)
            lineTo(w * 0.78f, bottom)
            moveTo(w * 0.78f, top)
            lineTo(w * 0.54f, bottom)
        }
        drawPath(path = xPath, color = strokeColor, style = stroke)

        if (shouldAnimate) {
            val sweepX = w * (0.12f + phase * 0.76f)
            drawLine(
                color = primary.copy(alpha = 0.35f),
                start = Offset(sweepX, h * 0.14f),
                end = Offset(sweepX - w * 0.08f, h * 0.86f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun StageSketch(
    stage: StageSettings,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lineColor = if (stage.enabled) onSurfaceVariant.copy(alpha = 0.5f) else onSurfaceVariant.copy(alpha = 0.28f)
        val backInsetX = w * 0.22f
        val backTopY = h * 0.22f
        val backBottomY = h * 0.66f
        val frontInsetX = w * 0.08f
        val frontTopY = h * 0.08f
        val frontBottomY = h * 0.90f

        val btl = Offset(backInsetX, backTopY)
        val btr = Offset(w - backInsetX, backTopY)
        val bbl = Offset(backInsetX, backBottomY)
        val bbr = Offset(w - backInsetX, backBottomY)

        val ftl = Offset(frontInsetX, frontTopY)
        val ftr = Offset(w - frontInsetX, frontTopY)
        val fbl = Offset(frontInsetX, frontBottomY)
        val fbr = Offset(w - frontInsetX, frontBottomY)

        val strokeW = 1.2.dp.toPx()
        val edges = listOf(
            btl to btr, btr to bbr, bbr to bbl, bbl to btl,
            ftl to ftr, ftr to fbr, fbr to fbl, fbl to ftl,
            btl to ftl, btr to ftr, bbl to fbl, bbr to fbr,
        )
        for ((a, b) in edges) {
            drawLine(color = lineColor, start = a, end = b, strokeWidth = strokeW)
        }

        fun projectDot(nx: Float, ny: Float, nz: Float): Offset {
            val depthT = ((nz + 1f) * 0.5f).coerceIn(0f, 1f)
            val leftX = frontInsetX + (backInsetX - frontInsetX) * depthT
            val rightX = w - leftX
            val topY = frontTopY + (backTopY - frontTopY) * depthT
            val bottomY = frontBottomY + (backBottomY - frontBottomY) * depthT
            val px = leftX + ((nx + 1f) * 0.5f).coerceIn(0f, 1f) * (rightX - leftX)
            val py = bottomY - ny.coerceIn(0f, 1f) * (bottomY - topY)
            return Offset(px, py)
        }

        val dotColor = if (stage.enabled) primary else onSurfaceVariant.copy(alpha = 0.55f)
        val dotRadius = if (stage.enabled && isPlaying) 3.dp.toPx() else 2.5.dp.toPx()
        stage.objects.forEach { obj ->
            val p = projectDot(obj.x, obj.y, obj.z)
            drawCircle(
                color = if (obj.muted) Color.Transparent else dotColor,
                radius = dotRadius,
                center = p,
            )
            if (obj.muted) {
                drawCircle(
                    color = onSurfaceVariant.copy(alpha = 0.45f),
                    radius = dotRadius,
                    center = p,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        val listenerPos = projectDot(stage.listener.x, stage.listener.y, stage.listener.z)
        val rad = Math.toRadians(stage.listener.yaw.toDouble())
        val dir = Offset(sin(rad).toFloat() * 7.dp.toPx(), -cos(rad).toFloat() * 5.dp.toPx())
        drawLine(
            color = if (stage.enabled) tertiary else onSurfaceVariant.copy(alpha = 0.5f),
            start = listenerPos,
            end = listenerPos + dir,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = if (stage.enabled) tertiary else onSurfaceVariant.copy(alpha = 0.65f),
            radius = 3.2.dp.toPx(),
            center = listenerPos,
        )
    }
}
