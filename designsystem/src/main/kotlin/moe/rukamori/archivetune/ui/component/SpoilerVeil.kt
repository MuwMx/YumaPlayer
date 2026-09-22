/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.graphics.Paint
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.designsystem.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MIN_PARTICLES = 24
private const val MAX_PARTICLES = 160
private const val PARTICLE_STRIDE = 8
private const val TWO_PI = (2.0 * PI).toFloat()
private val PARTICLE_ALPHAS = floatArrayOf(0.3f, 0.6f, 1.0f)

@Immutable
private class SpoilerParticleField(
    val data: FloatArray,
    val layerPoints: Array<FloatArray>,
    val renderCount: IntArray,
    val random: Random = Random(42),
) {
    var lastDrawTimeMs: Long = 0L
    var lastWidth: Float = 0f
    var lastHeight: Float = 0f
}

@Composable
fun SpoilerVeil(
    revealed: Boolean,
    onRevealChange: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(SettingsDimensions.GlassCornerRadius),
    content: @Composable () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current

    val revealProgressState = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (animationsDisabled) {
            snap()
        } else {
            tween(durationMillis = 350, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        },
        label = "spoilerReveal",
    )

    val sparklePhaseState: State<Float>? =
        if (!revealed && !animationsDisabled) {
            val veilTransition = rememberInfiniteTransition(label = "spoilerSparkle")
            veilTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "sparklePhase",
            )
        } else {
            null
        }

    val particleField = remember {
        val data = FloatArray(MAX_PARTICLES * PARTICLE_STRIDE)
        val layerPoints = Array(3) { FloatArray(MAX_PARTICLES * 2) }
        val renderCount = IntArray(3)
        val random = Random(42)

        val g = 1.324717957244746f
        val a1 = 1f / g
        val a2 = 1f / (g * g)

        for (i in 0 until MAX_PARTICLES) {
            val base = i * PARTICLE_STRIDE
            val rx = ((0.5f + a1 * (i + 1)) % 1.0f)
            val ry = ((0.5f + a2 * (i + 1)) % 1.0f)
            val jitterX = (random.nextFloat() - 0.5f) * 0.12f
            val jitterY = (random.nextFloat() - 0.5f) * 0.12f
            val normX = (rx + jitterX).coerceIn(0.04f, 0.96f)
            val normY = (ry + jitterY).coerceIn(0.08f, 0.92f)

            val angle = random.nextFloat() * TWO_PI
            val vecX = cos(angle)
            val vecY = sin(angle)
            val velocity = 4.0f + random.nextFloat() * 4.0f
            val lifeTimeMs = 1000f + random.nextFloat() * 2000f
            val currentTimeMs = random.nextFloat() * lifeTimeMs
            val layer = (i % 3).toFloat()

            data[base] = normX
            data[base + 1] = normY
            data[base + 2] = vecX
            data[base + 3] = vecY
            data[base + 4] = velocity
            data[base + 5] = lifeTimeMs
            data[base + 6] = currentTimeMs
            data[base + 7] = layer
        }
        SpoilerParticleField(data, layerPoints, renderCount, random)
    }

    val density = LocalDensity.current
    val yumaColors = LocalYumaColors.current
    val colorScheme = MaterialTheme.colorScheme

    val glassBorder = yumaColors.glassBorder
    val onSurfaceColor = colorScheme.onSurface

    val cornerRadiusPx = remember(density) {
        with(density) { SettingsDimensions.GlassCornerRadius.toPx() }
    }
    val veilCornerSize = remember(shape) {
        (shape as? RoundedCornerShape)?.topStart
    }
    val borderStroke = remember(density) {
        Stroke(width = with(density) { SettingsDimensions.GlassBorderThickness.toPx() })
    }
    val maxDispersePx = remember(density) {
        with(density) { 16.dp.toPx() }
    }
    val borderBrush = remember(glassBorder) {
        Brush.verticalGradient(
            0.0f to glassBorder.copy(alpha = 0.14f),
            1.0f to glassBorder.copy(alpha = 0.04f),
        )
    }
    val densityScale = density.density

    val baseStrokeWidths = remember(density) {
        floatArrayOf(
            with(density) { 1.4.dp.toPx() },
            with(density) { 1.2.dp.toPx() },
            with(density) { 1.2.dp.toPx() },
        )
    }
    val particlePaints = remember(density) {
        Array(3) { index ->
            Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = baseStrokeWidths[index]
                isAntiAlias = true
            }
        }
    }

    val hiddenDesc = stringResource(R.string.spoiler_hidden)
    val revealedDesc = stringResource(R.string.spoiler_revealed)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .semantics(mergeDescendants = true) {
                stateDescription = if (revealed) revealedDesc else hiddenDesc
            }
            .then(
                if (!revealed) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onRevealChange,
                    )
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                val revealProgress = revealProgressState.value
                val veilAlpha = 1f - revealProgress

                if (revealProgress > 0.001f) {
                    drawContent()
                }

                if (veilAlpha > 0.001f) {
                    val veilRadiusPx = veilCornerSize?.toPx(size, this) ?: cornerRadiusPx
                    val veilRadius = CornerRadius(veilRadiusPx, veilRadiusPx)
                    drawRoundRect(
                        color = onSurfaceColor.copy(alpha = 0.04f * veilAlpha),
                        cornerRadius = veilRadius,
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = veilRadius,
                        style = borderStroke,
                        alpha = veilAlpha * 0.4f,
                    )

                    val w = size.width
                    val h = size.height
                    if (w > 1f && h > 1f) {
                        val data = particleField.data
                        if (particleField.lastWidth <= 0f) {
                            for (i in 0 until MAX_PARTICLES) {
                                val base = i * PARTICLE_STRIDE
                                data[base] = data[base] * w
                                data[base + 1] = data[base + 1] * h
                            }
                            particleField.lastWidth = w
                            particleField.lastHeight = h
                        } else if (particleField.lastWidth != w || particleField.lastHeight != h) {
                            val scaleX = w / particleField.lastWidth
                            val scaleY = h / particleField.lastHeight
                            for (i in 0 until MAX_PARTICLES) {
                                val base = i * PARTICLE_STRIDE
                                data[base] = (data[base] * scaleX).coerceIn(0f, w)
                                data[base + 1] = (data[base + 1] * scaleY).coerceIn(0f, h)
                            }
                            particleField.lastWidth = w
                            particleField.lastHeight = h
                        }

                        val curTime = System.currentTimeMillis()
                        val dt = if (particleField.lastDrawTimeMs == 0L || curTime - particleField.lastDrawTimeMs > 200L) {
                            16f
                        } else {
                            (curTime - particleField.lastDrawTimeMs).coerceAtMost(34L).toFloat()
                        }
                        particleField.lastDrawTimeMs = curTime

                        @Suppress("UNUSED_VARIABLE")
                        val phase = sparklePhaseState?.value ?: 0f

                        val widthDp = w / densityScale
                        val activeParticles = ((widthDp / 6f) * 2.5f).toInt().coerceIn(MIN_PARTICLES, MAX_PARTICLES)

                        val layerPoints = particleField.layerPoints
                        val renderCount = particleField.renderCount
                        renderCount[0] = 0
                        renderCount[1] = 0
                        renderCount[2] = 0

                        val dispersePx = revealProgress * maxDispersePx
                        val speedScale = densityScale * (dt / 1000f)
                        val random = particleField.random

                        for (i in 0 until activeParticles) {
                            val base = i * PARTICLE_STRIDE
                            var px = data[base]
                            var py = data[base + 1]
                            var vecX = data[base + 2]
                            var vecY = data[base + 3]
                            var velocity = data[base + 4]
                            var lifeTimeMs = data[base + 5]
                            var currentTimeMs = data[base + 6]
                            val layer = data[base + 7].toInt()

                            if (!animationsDisabled) {
                                currentTimeMs += dt
                                val outOfBounds = px < 0f || px > w || py < 0f || py > h
                                if (currentTimeMs >= lifeTimeMs || outOfBounds) {
                                    currentTimeMs = 0f
                                    lifeTimeMs = 1000f + random.nextFloat() * 2000f
                                    px = (0.04f + random.nextFloat() * 0.92f) * w
                                    py = (0.08f + random.nextFloat() * 0.84f) * h
                                    val angle = random.nextFloat() * TWO_PI
                                    vecX = cos(angle)
                                    vecY = sin(angle)
                                    velocity = 4.0f + random.nextFloat() * 4.0f

                                    data[base + 2] = vecX
                                    data[base + 3] = vecY
                                    data[base + 4] = velocity
                                    data[base + 5] = lifeTimeMs
                                } else {
                                    val drift = velocity * speedScale
                                    px += vecX * drift
                                    py += vecY * drift
                                }
                                data[base] = px
                                data[base + 1] = py
                                data[base + 6] = currentTimeMs
                            }

                            var drawX = px
                            var drawY = py
                            if (revealProgress > 0.001f) {
                                val centerDirX = (px / w) - 0.5f
                                val centerDirY = (py / h) - 0.5f
                                drawX += (vecX * 0.7f + centerDirX * 1.3f) * dispersePx
                                drawY += (vecY * 0.7f + centerDirY * 1.3f) * dispersePx
                            }

                            val points = layerPoints[layer]
                            val count = renderCount[layer]
                            if (count + 1 < points.size) {
                                points[count] = drawX
                                points[count + 1] = drawY
                                renderCount[layer] = count + 2
                            }
                        }

                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        for (a in 0 until 3) {
                            val count = renderCount[a]
                            if (count > 0) {
                                val paint = particlePaints[a]
                                val layerAlpha = PARTICLE_ALPHAS[a] * veilAlpha
                                paint.color = onSurfaceColor.copy(alpha = onSurfaceColor.alpha * layerAlpha).toArgb()
                                paint.strokeWidth = baseStrokeWidths[a] * (1f - revealProgress * 0.3f)
                                nativeCanvas.drawPoints(layerPoints[a], 0, count, paint)
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = revealProgressState.value
                }
                .then(if (!revealed) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            content()
        }
    }
}

@ThemePreviews
@Composable
private fun SpoilerVeilPreview() {
    TestThemeWrapper {
        var revealed by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SpoilerVeil(
                revealed = revealed,
                onRevealChange = { revealed = !revealed },
            ) {
                Text(
                    text = "sp_dc=AQD7...secret_token_12345",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SpoilerVeilComparisonPreview() {
    TestThemeWrapper {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Veiled State",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpoilerVeil(
                revealed = false,
                onRevealChange = {},
            ) {
                Text(
                    text = "super_secret_qobuz_token_xyz987",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Text(
                text = "Revealed State",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpoilerVeil(
                revealed = true,
                onRevealChange = {},
            ) {
                Text(
                    text = "super_secret_qobuz_token_xyz987",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
