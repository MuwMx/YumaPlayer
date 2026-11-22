/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.audiodsp.AudioTap
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialMode
import moe.rukamori.archivetune.audiodsp.SpatialParams
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private class RoomWaves {
    val born = FloatArray(14) { -100f }
    val strength = FloatArray(14)
    var next = 0
    var nextEmit = 0f
    var lastEmit = -10f

    fun emit(t: Float, s: Float) {
        born[next] = t
        strength[next] = s
        next = (next + 1) % born.size
        lastEmit = t
    }
}

@Composable
internal fun ReverbPicture(
    reverb: ReverbParams,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val room by animateFloatAsState(
        targetValue = reverb.roomSize,
        animationSpec = if (animationsDisabled) snap() else spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "reverbRoom",
    )
    val on by animateFloatAsState(
        targetValue = if (reverb.enabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "reverbOn",
    )
    val waves = remember { RoomWaves() }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    FxPictureCanvas(isPlaying = isPlaying, modifier = modifier) { t, level, punch, _ ->
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val speakerX = w * 0.15f
        val backX = w * 0.05f
        val wallX = w * (0.52f + 0.4f * room)
        val speed = w * 0.42f

        val live = isPlaying && AudioTap.isLive
        if (t >= waves.nextEmit || (live && punch > 0.55f && t - waves.lastEmit > 0.28f)) {
            waves.emit(t, if (live) 0.55f + 0.45f * min(1f, level * 3f + punch) else 0.75f)
            waves.nextEmit = t + if (live) 0.9f else 1.3f
        }

        val reflectivity = (0.35f + 0.55f * reverb.roomSize) * (1f - 0.55f * reverb.damping)
        val echoAlpha = on * (0.35f + 0.65f * reverb.wet) * (0.4f + 0.6f * reverb.mix)
        var flash = 0f
        val span = (wallX - backX).coerceAtLeast(1f)
        for (i in waves.born.indices) {
            val age = t - waves.born[i]
            if (age < 0f || age > 8f) continue
            val d = age * speed
            val first = wallX - speakerX
            if (d <= first || on < 0.01f) {
                val x = speakerX + d
                if (x > w + 20f) continue
                val a = waves.strength[i] * exp(-d / (w * 1.6f))
                drawWave(ink, x, cy, h, d, a * (if (d > first) 1f - on else 1f), rightward = true, softness = 0f)
                continue
            }
            val past = d - first
            val bounces = 1 + (past / span).toInt()
            val inLeg = past % span
            val rightward = bounces % 2 == 0
            val x = if (rightward) backX + inLeg else wallX - inLeg
            val a = waves.strength[i] * reflectivity.pow(bounces) * exp(-d / (w * 2.2f)) * echoAlpha
            if (a < 0.02f) continue
            if (inLeg < span * 0.08f && !rightward) flash = max(flash, a * (1f - inLeg / (span * 0.08f)))
            drawWave(accent, x, cy, h, d, a, rightward, softness = reverb.damping)
        }

        val wallH = h * 0.62f
        drawRoundRect(
            color = ink.copy(alpha = (0.18f + 0.55f * on + 0.6f * flash).coerceAtMost(1f)),
            topLeft = Offset(wallX, cy - wallH / 2f),
            size = Size(5.dp.toPx(), wallH),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        drawSpeaker(ink, Offset(speakerX, cy), 1f + punch * 0.12f)
    }
}

private fun DrawScope.drawWave(
    color: Color,
    x: Float,
    cy: Float,
    h: Float,
    travelled: Float,
    alpha: Float,
    rightward: Boolean,
    softness: Float,
) {
    if (alpha <= 0.005f) return
    val reach = min(h * 0.42f, 14.dp.toPx() + travelled * 0.18f)
    val bulge = reach * 0.42f * if (rightward) 1f else -1f
    val path = Path().apply {
        moveTo(x - bulge * 0.2f, cy - reach)
        quadraticTo(x + bulge, cy, x - bulge * 0.2f, cy + reach)
    }
    if (softness > 0.15f) {
        drawPath(
            path = path,
            color = color.copy(alpha = alpha * 0.25f * softness),
            style = Stroke(7.dp.toPx() * softness, cap = StrokeCap.Round),
        )
    }
    drawPath(
        path = path,
        color = color.copy(alpha = alpha.coerceAtMost(1f)),
        style = Stroke((2.2f - softness).dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawSpeaker(color: Color, center: Offset, scale: Float) {
    val s = 9.dp.toPx() * scale
    val body = Path().apply {
        moveTo(center.x - s * 1.1f, center.y - s * 0.45f)
        lineTo(center.x - s * 0.4f, center.y - s * 0.45f)
        lineTo(center.x + s * 0.5f, center.y - s * 1.1f)
        lineTo(center.x + s * 0.5f, center.y + s * 1.1f)
        lineTo(center.x - s * 0.4f, center.y + s * 0.45f)
        lineTo(center.x - s * 1.1f, center.y + s * 0.45f)
        close()
    }
    drawPath(body, color.copy(alpha = 0.92f), style = Stroke(1.6.dp.toPx()))
}

@Composable
internal fun SpatialPicture(
    spatial: SpatialParams,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val on by animateFloatAsState(
        targetValue = if (spatial.enabled) spatial.amount else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "spatialOn",
    )
    val speakers by animateFloatAsState(
        targetValue = if (spatial.mode == SpatialMode.Speakers) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(500),
        label = "spatialMode",
    )
    val angle by animateFloatAsState(
        targetValue = spatial.speakerAngle,
        animationSpec = if (animationsDisabled) snap() else spring(dampingRatio = 0.8f, stiffness = 140f),
        label = "spatialAngle",
    )
    val feed by animateFloatAsState(
        targetValue = spatial.feedDb,
        animationSpec = if (animationsDisabled) snap() else tween(300),
        label = "spatialFeed",
    )

    val ink = MaterialTheme.colorScheme.onSurface
    val leftColor = MaterialTheme.colorScheme.primary
    val rightColor = MaterialTheme.colorScheme.tertiary

    FxPictureCanvas(isPlaying = isPlaying, modifier = modifier) { t, _, punch, _ ->
        val w = size.width
        val h = size.height
        val head = Offset(w * 0.5f, h * 0.66f)
        val r = 17.dp.toPx()
        val earL = Offset(head.x - r, head.y)
        val earR = Offset(head.x + r, head.y)

        drawCircle(ink.copy(alpha = 0.9f), r, head, style = Stroke(1.6.dp.toPx()))
        drawPath(
            path = Path().apply {
                moveTo(head.x - 4.dp.toPx(), head.y - r + 1.dp.toPx())
                lineTo(head.x, head.y - r - 6.dp.toPx())
                lineTo(head.x + 4.dp.toPx(), head.y - r + 1.dp.toPx())
            },
            color = ink.copy(alpha = 0.9f),
            style = Stroke(1.6.dp.toPx()),
        )
        drawCircle(ink, 3.dp.toPx(), earL)
        drawCircle(ink, 3.dp.toPx(), earR)

        val inside = 1f - on
        if (inside > 0.01f) {
            drawCircle(leftColor.copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * punch, Offset(head.x - r * 0.45f, head.y))
            drawCircle(rightColor.copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * punch, Offset(head.x + r * 0.45f, head.y))
        }

        val ring = min(w * 0.36f, h * 0.55f)
        val crossfeedAmount = on * (1f - speakers)
        val speakerAmount = on * speakers
        val rad = Math.toRadians(angle.toDouble())
        val spL = Offset(head.x - (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())
        val spR = Offset(head.x + (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())

        if (speakerAmount > 0.01f) {
            listOf(spL to leftColor, spR to rightColor).forEachIndexed { side, (sp, color) ->
                val near = if (side == 0) earL else earR
                val far = if (side == 0) earR else earL
                flowLine(sp, near, color.copy(alpha = 0.8f * speakerAmount), t, 1f)
                flowLine(sp, far, color.copy(alpha = 0.32f * speakerAmount), t - 0.08f, 0.6f)
                drawRoundRect(
                    color = color.copy(alpha = speakerAmount),
                    topLeft = Offset(sp.x - 6.dp.toPx(), sp.y - 8.dp.toPx()),
                    size = Size(12.dp.toPx(), 16.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }

        if (crossfeedAmount > 0.01f) {
            val cutoffScale = ((spatial.cutoffHz - 200f) / 1800f).coerceIn(0.25f, 1f)
            val strength = ((1f - (feed - 1f) / 14f) * (0.5f + 0.5f * cutoffScale)).coerceIn(0.2f, 1f)
            val lift = r * 2.4f
            listOf((earL to earR) to leftColor, (earR to earL) to rightColor).forEach { (pair, color) ->
                val (from, to) = pair
                val path = Path().apply {
                    moveTo(from.x, from.y - 4.dp.toPx())
                    cubicTo(from.x, from.y - lift, to.x, to.y - lift, to.x, to.y - 4.dp.toPx())
                }
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.55f * crossfeedAmount * strength),
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), -t * 40.dp.toPx()),
                    ),
                )
            }
            drawCircle(leftColor.copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * punch, Offset(earL.x - 10.dp.toPx(), earL.y))
            drawCircle(rightColor.copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * punch, Offset(earR.x + 10.dp.toPx(), earR.y))
        }
    }
}

private fun DrawScope.flowLine(from: Offset, to: Offset, color: Color, t: Float, weight: Float) {
    drawLine(color.copy(alpha = color.alpha * 0.35f), from, to, 1.2.dp.toPx() * weight)
    for (k in 0 until 3) {
        val p = (((t * 0.9f + k / 3f) % 1f) + 1f) % 1f
        drawCircle(
            color = color,
            radius = 2.2.dp.toPx() * weight,
            center = Offset(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p),
        )
    }
}
