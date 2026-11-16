/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.ambient

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val sharedAmbientShader: Any? by lazy {
    if (Build.VERSION.SDK_INT >= 33) createAgslShader(AmbientShaderSource) else null
}

private const val LoopSeconds = 60f
private const val Downscale = 4
private const val StepsPerSecond = 30f

@Composable
fun rememberAmbientClock(running: Boolean = true): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { nanos ->
                time.floatValue = ((nanos / 1_000_000L) % (LoopSeconds * 1000f).toLong()) / 1000f
            }
        }
    }
    return { time.floatValue }
}

@Composable
fun AmbientBackdrop(
    colors: AmbientColors,
    modifier: Modifier = Modifier,
    running: Boolean = true,
    time: () -> Float = rememberAmbientClock(running),
) {
    val currentTime by rememberUpdatedState(time)
    val step by remember { derivedStateOf { floor(currentTime() * StepsPerSecond) } }
    val shader = sharedAmbientShader

    Spacer(
        modifier = modifier
            .downscaledLayer()
            .then(
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    Modifier.drawWithCache {
                        val brush = ShaderBrush(shader.asAgslPaintShader())
                        onDrawBehind {
                            shader.setAgslUniform("resolution", size.width, size.height)
                            shader.setAgslUniform("time", step / StepsPerSecond)
                            shader.setAgslColorUniform("seamLeft", colors.tone(ToneSeamLeft).toArgb())
                            shader.setAgslColorUniform("seamRight", colors.tone(ToneSeamRight).toArgb())
                            shader.setAgslColorUniform("deep", colors.tone(ToneDeep).toArgb())
                            shader.setAgslColorUniform("accent", colors.tone(ToneAccent).toArgb())
                            drawRect(brush)
                        }
                    }
                } else {
                    Modifier.drawWithCache {
                        val deep = colors.tone(ToneDeep)
                        val radius = size.maxDimension * 0.55f
                        onDrawBehind {
                            drawRect(deep)
                            val t = step / StepsPerSecond * (2f * PI.toFloat() / LoopSeconds)
                            val blobs = listOf(
                                colors.tone(ToneSeamLeft) to (0.24f + 0.22f * sin(t * 4.0f + 0.4f) to 0.55f + 0.12f * cos(t * 6.0f)),
                                colors.tone(ToneSeamRight) to (0.76f + 0.20f * cos(t * 4.0f + 1.3f) to 0.60f + 0.14f * sin(t * 6.8f + 0.7f)),
                                colors.tone(ToneAccent) to (0.58f + 0.28f * sin(t * 5.6f + 3.4f) to 0.45f + 0.18f * cos(t * 7.2f + 0.4f)),
                            )
                            blobs.forEach { (tone, pos) ->
                                val (nx, ny) = pos
                                val cx = size.width * nx
                                val cy = size.height * ny
                                val radial = Brush.radialGradient(
                                    colors = listOf(tone, tone.copy(alpha = 0f)),
                                    center = Offset.Zero,
                                    radius = radius,
                                )
                                translate(cx, cy) {
                                    drawCircle(radial, radius = radius, center = Offset.Zero)
                                }
                            }
                        }
                    }
                }
            ),
    )
}

private fun Modifier.downscaledLayer(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val small = measurable.measure(
        Constraints.fixed(
            ((width + Downscale - 1) / Downscale).coerceAtLeast(1),
            ((height + Downscale - 1) / Downscale).coerceAtLeast(1),
        )
    )
    layout(width, height) {
        small.placeWithLayer(0, 0) {
            scaleX = Downscale.toFloat()
            scaleY = Downscale.toFloat()
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

private const val AmbientShaderSource = """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 seamLeft;
layout(color) uniform half4 seamRight;
layout(color) uniform half4 deep;
layout(color) uniform half4 accent;

const float TAU = 6.2831853;

float pool(float2 p, float2 centre, float spread) {
    float2 d = p - centre;
    return exp(-dot(d, d) * spread);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.y / max(resolution.x, 1.0);
    float2 p = float2(uv.x, uv.y * aspect);
    float w = TAU / 15.0;
    float t = time;

    float2 a = float2(0.24 + 0.22 * sin(t * w + 0.4), (0.55 + 0.12 * cos(t * w * 1.5)) * aspect);
    float2 b = float2(0.76 + 0.20 * cos(t * w + 1.3), (0.60 + 0.14 * sin(t * w * 1.7 + 0.7)) * aspect);
    float2 c = float2(0.50 + 0.26 * cos(t * w * 0.8 + 2.1), (0.85 + 0.12 * sin(t * w * 1.2 + 1.9)) * aspect);
    float2 d = float2(0.58 + 0.28 * sin(t * w * 1.4 + 3.4), (0.45 + 0.18 * cos(t * w * 1.8 + 0.4)) * aspect);

    float wa = pool(p, a, 3.2);
    float wb = pool(p, b, 3.2);
    float wc = pool(p, c, 2.8);
    float wd = pool(p, d, 4.4) * 1.15;
    float sum = wa + wb + wc + wd + 0.0001;
    float3 col = (float3(seamLeft.rgb) * wa + float3(seamRight.rgb) * wb + float3(deep.rgb) * wc + float3(accent.rgb) * wd) / sum;

    col *= 1.0 - 0.22 * smoothstep(0.55, 1.0, uv.y);
    float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""
