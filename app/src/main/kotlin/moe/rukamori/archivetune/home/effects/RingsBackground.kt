package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.PI
import kotlin.math.sin

private data class RingConfig(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationX: Int,
    val durationY: Int,
    val radii: List<Float>,
    val depth: Float // Depth for parallax effect
)

@Composable
fun RingsBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val disableAnimations = LocalAnimationsDisabled.current
    val context = LocalContext.current

    val animatedPrimaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ringsPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ringsSecondaryColor",
    )
    val animatedTertiaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.tertiary,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ringsTertiaryColor",
    )
    val alphaScale = brightness.coerceIn(0.1f, 2f)

    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity,
        context = context
    )
    val time = rememberAnimatedTime(
        speedMultiplier = if (disableAnimations) 0f else 1f,
        isVisible = isVisible,
        label = "RingsBackground",
    )

    // Ring configurations - defined once, positions oscillate via sin() each frame
    val ringConfigs = remember {
        listOf(
            RingConfig(0.2f,  0.2f,  0.3f,  0.25f, 9000,  8000, listOf(0.194f, 0.264f, 0.333f), 0.8f),
            RingConfig(0.85f, 0.15f, 0.8f,  0.2f,  10000, 7500, listOf(0.181f, 0.250f),       0.6f),
            RingConfig(0.5f,  0.5f,  0.55f, 0.55f, 8500,  9500, listOf(0.153f, 0.222f, 0.292f), 0.5f),
            RingConfig(0.15f, 0.75f, 0.2f,  0.8f,  7000,  8000, listOf(0.208f, 0.278f),       0.7f),
            RingConfig(0.8f,  0.85f, 0.85f, 0.8f,  8800,  7600, listOf(0.167f, 0.236f, 0.306f), 0.6f),
            RingConfig(0.75f, 0.4f,  0.8f,  0.45f, 9200,  8400, listOf(0.188f, 0.257f),       0.4f)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()
        val unit = minOf(size.width, size.height)
        val colors = arrayOf(animatedPrimaryColor, animatedSecondaryColor, animatedTertiaryColor)

        ringConfigs.forEachIndexed { index, config ->
            // Each ring oscillates between start/end using sin - mirrors the original Reverse tween
            val halfX = (config.endX - config.startX) / 2f
            val halfY = (config.endY - config.startY) / 2f
            val cx = config.startX + halfX + halfX * sin(t * twoPi / config.durationX)
            val cy = config.startY + halfY + halfY * sin(t * twoPi / config.durationY)

            val parallaxStrength = config.depth * unit * 0.1f
            val center = Offset(
                size.width  * cx + tiltX * parallaxStrength,
                size.height * cy + tiltY * parallaxStrength
            )

            // Select color based on index
            val baseColor = colors[index % 3]

            // Draw multiple concentric rings per group
            config.radii.forEachIndexed { ringIndex, radiusRatio ->
                val alpha = when (ringIndex) {
                    0    -> 0.14f
                    1    -> 0.10f
                    2    -> 0.07f
                    else -> 0.06f
                }
                val strokeWidth = when (ringIndex) {
                    0    -> 3.dp.toPx()
                    1    -> 2.5.dp.toPx()
                    else -> 2.dp.toPx()
                }
                val radius = (radiusRatio * unit).coerceAtMost(220.dp.toPx())
                
                drawCircle(
                    color  = baseColor.copy(alpha = alpha * alphaScale),
                    radius = radius,
                    center = center,
                    style  = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
