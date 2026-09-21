/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 72
private const val PARTICLE_STRIDE = 6
private const val TWO_PI = (2.0 * PI).toFloat()

@Immutable
private class SpoilerParticleField(
    val data: FloatArray,
)

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
        val array = FloatArray(PARTICLE_COUNT * PARTICLE_STRIDE)
        val random = Random(42)
        val rows = 6
        val cols = 12
        var i = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val base = i * PARTICLE_STRIDE
                val jitterX = (random.nextFloat() - 0.5f) * 0.8f
                val jitterY = (random.nextFloat() - 0.5f) * 0.8f
                val normX = ((c + 0.5f + jitterX) / cols).coerceIn(0.04f, 0.96f)
                val normY = ((r + 0.5f + jitterY) / rows).coerceIn(0.08f, 0.92f)
                val radiusDp = 1.0f + random.nextFloat() * 1.4f
                val phase = random.nextFloat() * TWO_PI
                val speed = 0.8f + random.nextFloat() * 1.0f
                val isAccent = if (random.nextFloat() < 0.25f) 1.0f else 0.0f

                array[base] = normX
                array[base + 1] = normY
                array[base + 2] = radiusDp
                array[base + 3] = phase
                array[base + 4] = speed
                array[base + 5] = isAccent
                i++
            }
        }
        SpoilerParticleField(array)
    }

    val density = LocalDensity.current
    val yumaColors = LocalYumaColors.current
    val colorScheme = MaterialTheme.colorScheme

    val glassBackground = yumaColors.glassBackground
    val glassBorder = yumaColors.glassBorder
    val primaryColor = colorScheme.primary
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
            0.0f to glassBorder.copy(alpha = 0.22f),
            1.0f to glassBorder.copy(alpha = 0.08f),
        )
    }
    val densityScale = density.density

    val hiddenDesc = stringResource(R.string.spoiler_hidden)
    val revealedDesc = stringResource(R.string.spoiler_revealed)

    Box(
        modifier = modifier
            .clip(shape)
            .semantics(mergeDescendants = true) {
                stateDescription = if (revealed) revealedDesc else hiddenDesc
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !revealed,
                role = Role.Button,
                onClick = onRevealChange,
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
                        color = glassBackground.copy(alpha = glassBackground.alpha * veilAlpha),
                        cornerRadius = veilRadius,
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = veilRadius,
                        style = borderStroke,
                        alpha = veilAlpha,
                    )

                    val phase = sparklePhaseState?.value ?: 0f
                    val w = size.width
                    val h = size.height
                    val dispersePx = revealProgress * maxDispersePx
                    val particleData = particleField.data

                    var idx = 0
                    while (idx < particleData.size) {
                        val normX = particleData[idx]
                        val normY = particleData[idx + 1]
                        val radiusDp = particleData[idx + 2]
                        val pPhase = particleData[idx + 3]
                        val pSpeed = particleData[idx + 4]
                        val isAccent = particleData[idx + 5] > 0.5f

                        val shimmer = (sin(phase * TWO_PI * pSpeed + pPhase) + 1f) * 0.5f
                        val particleAlpha = (0.25f + 0.70f * shimmer) * veilAlpha

                        val dirX = normX - 0.5f
                        val dirY = normY - 0.5f
                        val px = normX * w + dirX * dispersePx
                        val py = normY * h + dirY * dispersePx

                        val r = radiusDp * densityScale * (1f - revealProgress * 0.4f)
                        val color = if (isAccent) primaryColor else onSurfaceColor

                        drawCircle(
                            color = color.copy(alpha = particleAlpha),
                            radius = r,
                            center = Offset(px, py),
                        )

                        idx += PARTICLE_STRIDE
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
