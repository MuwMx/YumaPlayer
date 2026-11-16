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

private const val LoopSeconds = 52f
private const val Downscale = 4
private const val StepsPerSecond = 30f

@Composable
fun rememberAmbientClock(running: Boolean = true): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        while (isActive) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (startNanos < 0L) startNanos = nanos
                val elapsedSec = (nanos - startNanos) / 1_000_000_000f
                time.floatValue = elapsedSec % LoopSeconds
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
                                colors.tone(ToneSeamLeft) to (0.24f + 0.18f * sin(t * 2.0f + 0.4f) to 0.58f + 0.12f * cos(t * 4.0f)),
                                colors.tone(ToneSeamRight) to (0.76f + 0.18f * cos(t * 2.0f + 1.3f) to 0.60f + 0.12f * sin(t * 4.0f + 0.7f)),
                                colors.tone(ToneAccent) to (0.54f + 0.24f * sin(t * 2.0f + 3.4f) to 0.42f + 0.18f * cos(t * 4.0f + 0.4f)),
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
    float w = TAU / 26.0;
    float t = time;

    float3 col = float3(deep.rgb);

    float2 pLeft = float2(0.25 + 0.25 * sin(t * w + 0.4), (0.75 + 0.18 * cos(t * w * 2.0)) * aspect);
    col = mix(col, float3(seamLeft.rgb), pool(p, pLeft, 1.6) * 0.85);

    float waveY = 0.50 + 0.24 * sin(t * w + 1.3) + 0.14 * sin(uv.x * 4.0 + t * w * 2.0);
    float distToRibbon = abs(uv.y - waveY);
    col = mix(col, float3(seamRight.rgb), exp(-distToRibbon * distToRibbon * 24.0) * 0.88);

    float2 pLight = float2(0.48 + 0.36 * sin(t * w * 2.0 + 2.6), (0.50 + 0.34 * cos(t * w + 0.9)) * aspect);
    col = mix(col, float3(accent.rgb), pool(p, pLight, 2.0) * 0.80);

    float2 c = uv - 0.5;
    col *= 1.0 - dot(c, c) * 0.22;
    col *= 1.0 - 0.16 * smoothstep(0.65, 1.0, uv.y);

    float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""
