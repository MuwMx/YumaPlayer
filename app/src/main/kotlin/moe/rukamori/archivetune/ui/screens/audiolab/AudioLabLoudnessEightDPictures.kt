/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.audiodsp.AudioTap
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun EightDPicture(
    eightD: EightDParams,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val on by animateFloatAsState(
        targetValue = if (eightD.enabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "eightDOn",
    )
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)

    FxPictureCanvas(isPlaying = isPlaying, modifier = modifier) { t, level, punch, _ ->
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val radius = minOf(size.width * 0.34f, size.height * 0.43f)
        repeat(3) { index ->
            drawCircle(
                color = lineColor,
                radius = radius * (index + 1) / 3f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        drawLine(lineColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(lineColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

        val angleDeg = if (eightD.enabled) (t * 360f * eightD.rotationSpeedHz) % 360f else 0f
        val orbitRadius = radius * (0.25f + eightD.depth * 0.75f)
        val trailSteps = 10
        val trailSpanDeg = 28f + 95f * eightD.reverbLevel
        if (on > 0.05f) {
            for (k in trailSteps downTo 1) {
                val frac = k.toFloat() / trailSteps
                val trailRad = (angleDeg - 90f - frac * trailSpanDeg) * PI.toFloat() / 180f
                val trailPos = Offset(
                    center.x + cos(trailRad) * orbitRadius,
                    center.y + sin(trailRad) * orbitRadius,
                )
                drawCircle(
                    color = accent.copy(alpha = (1f - frac) * 0.42f * on * (0.35f + 0.65f * eightD.reverbLevel)),
                    radius = (4.5f - frac * 2.5f).dp.toPx(),
                    center = trailPos,
                )
            }
        }

        val radians = (angleDeg - 90f) * PI.toFloat() / 180f
        val source = Offset(
            center.x + cos(radians) * orbitRadius,
            center.y + sin(radians) * orbitRadius,
        )
        drawLine(
            color = ink.copy(alpha = if (eightD.enabled) 0.32f else 0.12f),
            start = center,
            end = source,
            strokeWidth = 1.dp.toPx(),
        )
        if (on > 0.05f) {
            drawCircle(
                color = accent.copy(alpha = (0.2f + 0.35f * min(1f, level * 2.5f + punch)) * on),
                radius = (9.dp + (4.dp * punch)).toPx(),
                center = source,
            )
        }
        drawCircle(
            color = if (eightD.enabled) accent else ink.copy(alpha = 0.35f),
            radius = 5.dp.toPx(),
            center = source,
        )

        val headsetRadius = radius * 0.24f
        drawArc(
            color = ink.copy(alpha = 0.9f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - headsetRadius, center.y - headsetRadius),
            size = Size(headsetRadius * 2f, headsetRadius * 2f),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.9f),
            topLeft = Offset(center.x - headsetRadius, center.y - 2.dp.toPx()),
            size = Size(4.5.dp.toPx(), 11.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.9f),
            topLeft = Offset(center.x + headsetRadius - 4.5.dp.toPx(), center.y - 2.dp.toPx()),
            size = Size(4.5.dp.toPx(), 11.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

private class LevelHistory {
    val values = FloatArray(56)
    var head = 0
    var accumulated = 0f
}

@Composable
internal fun LoudnessPicture(
    loudness: LoudnessParams,
    reductionDbProvider: () -> Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val on by animateFloatAsState(
        targetValue = if (loudness.enabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "loudnessOn",
    )
    val history = remember { LevelHistory() }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    FxPictureCanvas(isPlaying = isPlaying, modifier = modifier) { t, level, _, dt ->
        history.accumulated += dt
        if (history.accumulated > 1f / 24f || animationsDisabled) {
            history.accumulated = 0f
            val v = if (isPlaying && AudioTap.isLive) {
                level * 2.6f
            } else {
                0.18f + 0.12f * sin(t * 2.1f) + 0.1f * sin(t * 5.3f + 1f) + 0.06f * sin(t * 11f)
            }
            history.values[history.head] = v.coerceIn(0.005f, 1.4f)
            history.head = (history.head + 1) % history.values.size
        }

        val w = size.width
        val h = size.height
        val top = 16.dp.toPx()
        val bottom = h - 14.dp.toPx()
        fun yOf(db: Float) = bottom - ((db + 42f) / 42f).coerceIn(0f, 1f) * (bottom - top)
        val ceiling = yOf(-0.5f)

        val compression = loudness.compression
        val threshold = -10f - 24f * compression
        val slope = 1f - 1f / (1f + 3f * compression)
        val makeup = if (compression > 0f) -threshold * slope * 0.5f else 0f

        val n = history.values.size
        val barW = (w - 24.dp.toPx()) / n
        val mid = (top + bottom) * 0.5f
        for (k in 0 until n) {
            val v = history.values[(history.head + k) % n]
            val inDb = 20f * log10(max(v, 1e-4f))
            val reduced = if (inDb > threshold) (threshold - inDb) * slope else 0f
            val outDb = (inDb + (loudness.boostDb + makeup + reduced) * on).coerceAtMost(-0.5f)
            val x = 12.dp.toPx() + k * barW
            val yIn = yOf(min(inDb, 0f))
            val yOut = yOf(outDb)
            val inHalf = (bottom - yIn) * 0.5f
            val outHalf = (bottom - yOut) * 0.5f
            drawRoundRect(
                color = ink.copy(alpha = 0.12f),
                topLeft = Offset(x, mid - inHalf),
                size = Size(barW * 0.62f, inHalf * 2f),
                cornerRadius = CornerRadius(barW * 0.3f),
            )
            val limited = on > 0.5f && outDb >= -0.6f
            drawRoundRect(
                color = if (limited) accent else ink.copy(alpha = 0.72f),
                topLeft = Offset(x, mid - outHalf),
                size = Size(barW * 0.62f, outHalf * 2f),
                cornerRadius = CornerRadius(barW * 0.3f),
            )
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val half = (bottom - ceiling) * 0.5f
        val ceilColor = ink.copy(alpha = 0.3f * on + 0.08f)
        drawLine(ceilColor, Offset(10.dp.toPx(), mid - half), Offset(w - 10.dp.toPx(), mid - half), 1.dp.toPx(), pathEffect = dash)
        drawLine(ceilColor, Offset(10.dp.toPx(), mid + half), Offset(w - 10.dp.toPx(), mid + half), 1.dp.toPx(), pathEffect = dash)

        val reduction = (-reductionDbProvider()).coerceIn(0f, 12f) / 12f * on
        if (reduction > 0.01f) {
            val meterH = (bottom - top) * reduction
            drawRoundRect(
                color = accent.copy(alpha = 0.9f),
                topLeft = Offset(w - 7.dp.toPx(), top),
                size = Size(3.dp.toPx(), meterH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}
