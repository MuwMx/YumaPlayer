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
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GridBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val disableAnimations = LocalAnimationsDisabled.current

    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gridPrimaryColor",
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gridSecondaryColor",
    )
    val animatedTertiaryColor by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gridTertiaryColor",
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
        label = "GridBackground",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()

        val cols  = 11
        val rows  = 20
        val cellW = size.width  / (cols - 1).toFloat()
        val cellH = size.height / (rows - 1).toFloat()
        val maxDist = sqrt(size.width * size.width + size.height * size.height) * 0.5f
        val unit = minOf(size.width, size.height)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val baseX = col * cellW + tiltX * unit * 0.017f
                val baseY = row * cellH + tiltY * unit * 0.017f

                // Distance from screen center - drives the ripple phase offset
                val dx = baseX - size.width  * 0.5f
                val dy = baseY - size.height * 0.5f
                val dist = sqrt(dx * dx + dy * dy)

                // Continuous ripple wave: phase offset by distance so wave propagates outward
                val ripplePhase = dist * 0.012f
                val wave = sin(t * twoPi / 3800f - ripplePhase)

                // Base dot radius oscillates with the wave
                val baseRadius = unit * 0.007f + wave * unit * 0.0035f
                val finalRadius = baseRadius.coerceAtLeast(unit * 0.001f)

                // Color cycles gently across the grid
                val colorPhase = (col + row) % 3
                val color = when (colorPhase) {
                    0    -> animatedPrimaryColor
                    1    -> animatedSecondaryColor
                    else -> animatedTertiaryColor
                }

                // Alpha: dims toward edges
                val edgeDim   = (1f - dist / maxDist).coerceIn(0.55f, 1f)
                val baseAlpha = 0.30f + wave * 0.10f
                val finalAlpha = (baseAlpha * edgeDim).coerceIn(0f, 0.50f) * alphaScale

                drawCircle(
                    color  = color.copy(alpha = finalAlpha),
                    radius = finalRadius,
                    center = Offset(baseX, baseY)
                )
            }
        }
    }
}
