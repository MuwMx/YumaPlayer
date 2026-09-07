package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.PI
import kotlin.math.sin

private data class CircleSpec(
    val xRatio: Float,
    val yRatio: Float,
    val radiusRatio: Float,
    val maxRadiusDp: Float,
    val colorIndex: Int,
    val alpha: Float,
    val xWave: Float,
    val yWave: Float,
    val period: Float,
    val parallaxWeight: Float,
)

private val circleSpecs = listOf(
    CircleSpec(0.18f, 0.22f, 0.32f, 170f, 0, 0.25f, 0.025f, 0.020f, 8000f, 0.8f),
    CircleSpec(0.84f, 0.19f, 0.32f, 140f, 2, 0.18f, 0.020f, 0.025f, 9000f, 0.6f),
    CircleSpec(0.70f, 0.46f, 0.25f, 130f, 2, 0.20f, 0.035f, 0.040f, 7500f, 0.5f),
    CircleSpec(0.80f, 0.80f, 0.38f, 200f, 1, 0.18f, 0.025f, 0.035f, 9500f, 0.7f),
    CircleSpec(0.25f, 0.79f, 0.26f, 140f, 0, 0.20f, 0.040f, 0.035f, 8200f, 0.6f),
    CircleSpec(0.52f, 0.97f, 0.30f, 160f, 1, 0.20f, 0.035f, 0.030f, 8800f, 0.6f),
)

@Composable
fun CirclesBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val primaryColor   = MaterialTheme.colorScheme.primaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryColor  = MaterialTheme.colorScheme.tertiaryContainer
    val context        = LocalContext.current

    val disableAnimations = LocalAnimationsDisabled.current

    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "circlesPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "circlesSecondaryColor",
    )
    val animatedTertiaryColor by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "circlesTertiaryColor",
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
        label = "CirclesBackground",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val t      = time.value
        val tiltX  = parallaxState.tiltX.value
        val tiltY  = parallaxState.tiltY.value
        val twoPi  = 2f * PI.toFloat()

        val colors = arrayOf(animatedPrimaryColor, animatedSecondaryColor, animatedTertiaryColor)
        val unit   = minOf(size.width, size.height)
        val parallaxBase = unit * 0.1f

        circleSpecs.forEach { spec ->
            val radius = (spec.radiusRatio * unit).coerceAtMost(spec.maxRadiusDp.dp.toPx())
            val parallaxStrength = spec.parallaxWeight * parallaxBase

            val restX = spec.xRatio * size.width
            val restY = spec.yRatio * size.height

            val center = Offset(
                restX + size.width * spec.xWave * sin(t * twoPi / spec.period) + tiltX * parallaxStrength,
                restY + size.height * spec.yWave * sin(t * twoPi / (spec.period * 0.9f)) + tiltY * parallaxStrength,
            )

            drawCircle(
                color = colors[spec.colorIndex].copy(alpha = spec.alpha * alphaScale),
                radius = radius,
                center = center,
            )
        }
    }
}