/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.AudioTap
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.lowMidHighAverages
import moe.rukamori.archivetune.ui.screens.audiolab.stage.DefaultCameraPitch
import moe.rukamori.archivetune.ui.screens.audiolab.stage.DefaultCameraYaw
import moe.rukamori.archivetune.ui.screens.audiolab.stage.StageView
import moe.rukamori.archivetune.ui.screens.audiolab.stage.drawStageRoom
import moe.rukamori.archivetune.ui.screens.audiolab.stage.rememberStageCanvasPalette
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import kotlin.math.sin
import kotlin.math.sqrt

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
    val thumbFill = LocalYumaColors.current.cardBackgroundOpaque
    val scratch = remember { FloatArray(512) }
    val pulseTick = remember { mutableFloatStateOf(0f) }
    val punchState = remember { floatArrayOf(0f, -1f) }

    val shouldAnimate = eq.enabled && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            pulseTick.floatValue = 0f
            punchState[0] = 0f
            punchState[1] = -1f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                pulseTick.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    val (lowTarget, midTarget, highTarget) = remember(eq) {
        if (eq.enabled) eq.lowMidHighAverages() else Triple(0f, 0f, 0f)
    }
    val low by animateFloatAsState(lowTarget, SettingsAnimations.pressSpring(), label = "eqLow")
    val mid by animateFloatAsState(midTarget, SettingsAnimations.pressSpring(), label = "eqMid")
    val high by animateFloatAsState(highTarget, SettingsAnimations.pressSpring(), label = "eqHigh")

    Canvas(modifier = modifier) {
        val t = pulseTick.floatValue
        if (shouldAnimate && t != punchState[1]) {
            val raw = AudioTap.level(scratch)
            val rate = if (raw > punchState[0]) 0.5f else 0.12f
            punchState[0] += (raw - punchState[0]) * rate
            punchState[1] = t
        }
        val punch = if (shouldAnimate) punchState[0] else 0f

        val panel = size.height * 0.92f
        val origin = Offset((size.width - panel) * 0.5f, (size.height - panel) * 0.5f)
        val panelStroke = 2.dp.toPx()
        val trackStroke = 2.4.dp.toPx()
        val thumbRadius = 5.dp.toPx()
        val frameColor = onSurfaceVariant.copy(alpha = 0.28f)
        val activeColor = if (eq.enabled) primary else onSurfaceVariant.copy(alpha = 0.55f)

        rotate(-14f, pivot = Offset(origin.x + panel * 0.5f, origin.y + panel * 0.5f)) {
            drawRoundRect(
                color = frameColor,
                topLeft = origin,
                size = Size(panel, panel),
                cornerRadius = CornerRadius(panel * 0.2f),
                style = Stroke(panelStroke),
            )
            val gains = floatArrayOf(low, mid, high)
            for (i in 0..2) {
                val x = origin.x + panel * (0.26f + i * 0.24f)
                val top = origin.y + panel * 0.18f
                val bottom = origin.y + panel * 0.82f
                drawLine(frameColor, Offset(x, top), Offset(x, bottom), trackStroke, StrokeCap.Round)
                val bob = punch * 3.5.dp.toPx() * sin(t * 9f + i * 2f)
                val y = ((top + bottom) * 0.5f - gains[i] * (bottom - top) * 0.5f + bob)
                    .coerceIn(top, bottom)
                drawCircle(thumbFill, thumbRadius, Offset(x, y))
                drawCircle(activeColor, thumbRadius, Offset(x, y), style = Stroke(trackStroke))
            }
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

    val isActive = activeCount > 0
    val glow by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(500),
        label = "fxGlow",
    )

    val shouldAnimate = isActive && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            sweepState.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                sweepState.floatValue = ((now - startNanos) / 1_800_000_000f) % 1f
            }
        }
    }

    Canvas(modifier = modifier) {
        val phase = sweepState.floatValue
        val baseColor = if (isActive) primary.copy(alpha = 0.85f) else onSurfaceVariant.copy(alpha = 0.55f)
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        val w = size.width
        val h = size.height
        val top = h * 0.22f
        val bottom = h * 0.78f
        val midY = (top + bottom) * 0.5f

        val fPath = Path().apply {
            moveTo(w * 0.26f, bottom)
            lineTo(w * 0.26f, top)
            lineTo(w * 0.46f, top)
            moveTo(w * 0.26f, midY)
            lineTo(w * 0.42f, midY)
        }
        val xPath = Path().apply {
            moveTo(w * 0.54f, top)
            lineTo(w * 0.76f, bottom)
            moveTo(w * 0.76f, top)
            lineTo(w * 0.54f, bottom)
        }

        drawPath(path = fPath, color = baseColor, style = stroke)
        drawPath(path = xPath, color = baseColor, style = stroke)

        if (shouldAnimate && glow > 0.01f) {
            val sweepX = w * (-0.1f + phase * 1.2f)
            val sheen = Brush.linearGradient(
                colors = listOf(Color.Transparent, primary.copy(alpha = 0.95f * glow), Color.Transparent),
                start = Offset(sweepX - w * 0.26f, 0f),
                end = Offset(sweepX + w * 0.26f, h),
            )
            drawPath(path = fPath, brush = sheen, style = stroke)
            drawPath(path = xPath, brush = sheen, style = stroke)
        }
    }
}

@Composable
fun StageSketch(
    stage: StageSettings,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    stageLevelsProvider: (() -> FloatArray)? = null,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val palette = rememberStageCanvasPalette()
    val timeState = remember { mutableFloatStateOf(0f) }
    val smoothedLevels = remember { FloatArray(6) }

    val on by animateFloatAsState(
        targetValue = if (stage.enabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "stageSketchOn",
    )

    val shouldAnimate = stage.enabled && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            smoothedLevels.fill(0f)
            timeState.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val raw = stageLevelsProvider?.invoke()
                if (raw != null) {
                    for (i in smoothedLevels.indices) {
                        val target = sqrt(raw.getOrElse(i) { 0f }.coerceIn(0f, 1f))
                        val cur = smoothedLevels[i]
                        smoothedLevels[i] += (target - cur) * if (target > cur) 0.45f else 0.12f
                    }
                }
                timeState.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier) {
        val t = timeState.floatValue
        val yaw = DefaultCameraYaw + if (shouldAnimate) sin(t * 0.45f) * 14f else 0f
        val intSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
        val view = StageView(
            size = intSize,
            box = stage.box,
            yawDeg = yaw,
            pitchDeg = DefaultCameraPitch + 6f,
        )
        drawStageRoom(
            view = view,
            stage = stage,
            selectedKey = null,
            levels = smoothedLevels,
            palette = palette,
            on = on,
            compact = true,
        )
    }
}
