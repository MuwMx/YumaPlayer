package moe.rukamori.archivetune.home.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CirclesBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val disableAnimations = LocalAnimationsDisabled.current

    val alphaScale = brightness.coerceIn(0.1f, 2f)

    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity,
        context = context
    )

    val time = rememberAnimatedTime(speedMultiplier = if (disableAnimations) 0f else 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val t     = time.value
        val tiltX = parallaxState.tiltX.value
        val tiltY = parallaxState.tiltY.value
        val twoPi = 2f * PI.toFloat()

        // Circle 1 - large top left
        var parallaxStrength = 0.8f * 80f
        var center = Offset(
            size.width  * (0.20f  + 0.05f  * sin(t * twoPi / 8000f)) + tiltX * parallaxStrength,
            size.height * (0.225f + 0.025f * sin(t * twoPi / 7000f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = primaryColor.copy(alpha = 0.28f * alphaScale), // было 0.12f
            radius = 400f,
            center = center
        )

        // Circle 2 - medium top right
        parallaxStrength = 0.6f * 80f
        center = Offset(
            size.width  * (0.85f  + 0.03f  * sin(t * twoPi / 9000f)) + tiltX * parallaxStrength,
            size.height * (0.185f + 0.035f * sin(t * twoPi / 6500f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = tertiaryColor.copy(alpha = 0.20f * alphaScale), // было 0.09f
            radius = 280f,
            center = center
        )

        // Circle 3 - small center right
        parallaxStrength = 0.4f * 80f
        center = Offset(
            size.width  * (0.715f + 0.035f * sin(t * twoPi / 7500f)) + tiltX * parallaxStrength,
            size.height * (0.44f  + 0.04f  * sin(t * twoPi / 8500f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = tertiaryColor.copy(alpha = 0.22f * alphaScale), // было 0.1f
            radius = 200f,
            center = center
        )

        // Circle 4 - medium bottom right
        parallaxStrength = 0.7f * 80f
        center = Offset(
            size.width  * (0.815f + 0.035f * sin(t * twoPi / 9500f)) + tiltX * parallaxStrength,
            size.height * (0.785f + 0.035f * sin(t * twoPi / 7200f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = secondaryColor.copy(alpha = 0.20f * alphaScale), // было 0.09f
            radius = 320f,
            center = center
        )

        // Circle 5 - small bottom left
        parallaxStrength = 0.5f * 80f
        center = Offset(
            size.width  * (0.24f  + 0.04f  * sin(t * twoPi / 8200f)) + tiltX * parallaxStrength,
            size.height * (0.765f + 0.035f * sin(t * twoPi / 6800f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = primaryColor.copy(alpha = 0.22f * alphaScale), // было 0.1f
            radius = 180f,
            center = center
        )
        // Circle 6 - bottom center
        parallaxStrength = 0.6f * 80f
        center = Offset(
            size.width  * (0.525f + 0.025f * sin(t * twoPi / 8800f)) + tiltX * parallaxStrength,
            size.height * (0.895f + 0.025f * sin(t * twoPi / 7800f)) + tiltY * parallaxStrength
        )
        drawCircle(
            color  = secondaryColor.copy(alpha = 0.22f * alphaScale), // было 0.1f
            radius = 220f,
            center = center
        )
    }
}
