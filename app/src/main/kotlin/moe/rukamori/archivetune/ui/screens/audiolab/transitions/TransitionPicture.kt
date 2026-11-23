/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.transitions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.screens.audiolab.FxPictureCanvas
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class TransitionStyle { Crossfade, Automix, Crossmix }

@Composable
internal fun TransitionPicture(
    style: TransitionStyle,
    overlap: Float,
    active: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val span by animateFloatAsState(
        targetValue = overlap,
        animationSpec = if (animationsDisabled) snap() else spring(dampingRatio = 0.85f, stiffness = 200f),
        label = "overlap",
    )
    val on by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(500),
        label = "active",
    )
    val primary = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = lerp(inactiveColor, primary, on)
    val tools = remember { PictureTools() }

    FxPictureCanvas(
        isPlaying = active && isPlaying,
        modifier = modifier.fillMaxSize(),
    ) { t, level, punch, _ ->
        tools.fitTo(density)
        val left = (size.width * 0.06f).coerceIn(10.dp.toPx(), 22.dp.toPx())
        val right = size.width - left
        val wide = (right - left).coerceAtLeast(1f)
        val shape = Shape(
            left = left,
            right = right,
            cy = size.height / 2f,
            half = size.height * 0.3f,
            fadeStart = left + wide * (1f - span) / 2f,
            fade = (wide * span).coerceAtLeast(1f),
            linearBlend = style == TransitionStyle.Crossfade,
        )
        val sweep = left + ((t * 0.16f) % 1f * 1.3f - 0.15f) * wide
        val breath = 0.94f + 0.1f * level * on

        drawWaveBars(shape, accent, sweep, glow = on, breath = breath)
        if (style == TransitionStyle.Crossmix) {
            drawKnot(tools, shape, accent, t, on, punch)
        }
        drawCrossLines(tools, shape, accent, alpha = 0.5f + 0.3f * on)
    }
}

private class Shape(
    val left: Float,
    val right: Float,
    val cy: Float,
    val half: Float,
    val fadeStart: Float,
    val fade: Float,
    val linearBlend: Boolean,
) {
    val mid = left + (right - left) / 2f

    fun opening(x: Float): Float {
        val p = ((x - fadeStart) / fade).coerceIn(0f, 1f)
        val eased = if (linearBlend) 0.35f * p + 0.65f * smoothStep(p) else smootherStep(p)
        return 1f - 2f * eased
    }
}

private class PictureTools {
    val upper = Path()
    val lower = Path()
    val strands = List(3) { Path() }
    var line = Stroke(1f)
        private set
    var haze = Stroke(1f)
        private set
    var strand = Stroke(1f)
        private set
    var strandHaze = Stroke(1f)
        private set
    private var fittedDensity = 0f
    private var glow: Brush? = null
    private var glowCenter = Offset.Zero
    private var glowRadius = 0f
    private var glowColor = Color.Unspecified

    fun fitTo(density: Float) {
        if (density == fittedDensity) return
        fittedDensity = density
        line = Stroke(1.6f * density, cap = StrokeCap.Round)
        haze = Stroke(5f * density, cap = StrokeCap.Round)
        strand = Stroke(1.1f * density, cap = StrokeCap.Round)
        strandHaze = Stroke(6f * density, cap = StrokeCap.Round)
    }

    fun glowAt(center: Offset, radius: Float, color: Color): Brush {
        val cached = glow
        if (cached != null && center == glowCenter && radius == glowRadius && color == glowColor) {
            return cached
        }
        glowCenter = center
        glowRadius = radius
        glowColor = color
        return Brush.radialGradient(
            0f to color,
            0.45f to color.copy(alpha = 0.35f),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ).also { glow = it }
    }
}

private fun DrawScope.drawWaveBars(
    shape: Shape,
    color: Color,
    sweep: Float,
    glow: Float,
    breath: Float,
) {
    val step = 4.5.dp.toPx()
    val barWidth = 1.8.dp.toPx()
    val wide = shape.right - shape.left
    val count = ((wide - step) / step).toInt().coerceAtLeast(1)
    val start = shape.left + (wide - count * step) / 2f
    val sweepWidth = wide * 0.08f
    val tiny = 0.8.dp.toPx()
    for (i in 0..count) {
        val x = start + i * step
        val u = (x - shape.left) / wide
        val open = abs(shape.opening(x))
        val loud = barLevel(i, if (x < shape.mid) 0f else 2.9f)
        val near = if (glow > 0.01f) exp(-square((x - sweep) / sweepWidth)) * glow else 0f
        val edge = smoothStep((u / 0.08f).coerceIn(0f, 1f)) * smoothStep(((1f - u) / 0.08f).coerceIn(0f, 1f))
        val reach = max(shape.half * open * loud * (breath + 0.14f * near), tiny)
        val alpha = ((0.22f + 0.5f * open) * edge + 0.35f * near).coerceAtMost(1f)
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(x, shape.cy - reach),
            end = Offset(x, shape.cy + reach),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCrossLines(tools: PictureTools, shape: Shape, color: Color, alpha: Float) {
    drawCrossLine(tools, tools.upper, shape, color, sign = 1f, alpha = alpha)
    drawCrossLine(tools, tools.lower, shape, color, sign = -1f, alpha = alpha)
}

private fun DrawScope.drawCrossLine(
    tools: PictureTools,
    path: Path,
    shape: Shape,
    color: Color,
    sign: Float,
    alpha: Float,
) {
    val step = 1.5.dp.toPx()
    path.reset()
    var x = shape.left
    path.moveTo(x, shape.cy - sign * shape.half * shape.opening(x))
    while (x < shape.right) {
        x = min(x + step, shape.right)
        path.lineTo(x, shape.cy - sign * shape.half * shape.opening(x))
    }
    drawPath(path, color.copy(alpha = alpha * 0.12f), style = tools.haze)
    drawPath(path, color.copy(alpha = alpha), style = tools.line)
}

private fun DrawScope.drawKnot(
    tools: PictureTools,
    shape: Shape,
    color: Color,
    time: Float,
    on: Float,
    punch: Float,
) {
    val center = Offset(shape.mid, shape.cy)
    val radius = max(shape.fade * 0.5f, size.height * 0.45f)
    val light = 0.08f + 0.10f * on + 0.18f * punch * on
    drawCircle(tools.glowAt(center, radius, color), radius, center, alpha = light)

    val reach = shape.fade * 0.55f + 8.dp.toPx()
    val step = 1.5.dp.toPx()
    tools.strands.forEachIndexed { k, path ->
        path.reset()
        val phase = k * 2.0944f + time * 1.4f
        var x = shape.mid - reach
        path.moveTo(x, shape.cy)
        while (x < shape.mid + reach) {
            x = min(x + step, shape.mid + reach)
            val d = (x - shape.mid) / reach
            val bell = square(1f - d * d)
            path.lineTo(x, shape.cy + shape.half * 0.5f * bell * sin(d * 7.5f + phase))
        }
        drawPath(path, color.copy(alpha = 0.06f + 0.05f * on), style = tools.strandHaze)
        drawPath(path, color.copy(alpha = 0.18f + 0.22f * on), style = tools.strand)
    }
}

private fun barLevel(index: Int, seed: Float): Float {
    val swell = 0.5f + 0.5f * sin(index * 0.31f + seed)
    val ripple = 0.5f + 0.5f * sin(index * 0.83f + seed * 1.7f)
    return 0.38f + 0.36f * swell + 0.16f * ripple + 0.1f * grain(index + (seed * 100f).toInt())
}

private fun grain(index: Int): Float {
    var h = index * 374761393 + 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private fun square(x: Float) = x * x

private fun smoothStep(x: Float) = x * x * (3f - 2f * x)

private fun smootherStep(x: Float) = x * x * x * (x * (x * 6f - 15f) + 10f)
