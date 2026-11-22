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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialMode
import moe.rukamori.archivetune.audiodsp.SpatialParams
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private class LevelFollower {
    private val scratch = FloatArray(512)
    private var lastTime = -1f
    var dt = 0f
        private set
    var level = 0f
        private set
    var punch = 0f
        private set

    fun tick(t: Float, isPlaying: Boolean) {
        if (t == lastTime) {
            dt = 0f
            return
        }
        dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.05f)
        lastTime = t

        val raw = if (isPlaying && AudioTap.isLive) AudioTap.level(scratch) else 0f
        val rate = if (raw > level) 0.5f else 0.12f
        level += (raw - level) * rate
        val targetPunch = ((raw - level) * 3.5f + level * 1.4f).coerceIn(0f, 1f)
        val punchRate = if (targetPunch > punch) 0.5f else 0.12f
        punch += (targetPunch - punch) * punchRate
    }
}

@Composable
internal fun FxPictureCanvas(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onDraw: DrawScope.(t: Float, level: Float, punch: Float, dt: Float) -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val time = remember { mutableFloatStateOf(0f) }
    val follower = remember { LevelFollower() }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            time.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time.floatValue
        follower.tick(t, isPlaying && !animationsDisabled)
        onDraw(t, follower.level, follower.punch, follower.dt)
    }
}

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
