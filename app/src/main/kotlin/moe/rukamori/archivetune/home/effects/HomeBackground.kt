package moe.rukamori.archivetune.home.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.HomeBackgroundStyle

data class HomeBackgroundSettings(
    val style: HomeBackgroundStyle = HomeBackgroundStyle.TONAL,
    val parallaxEnabled: Boolean = true,
    val parallaxSensitivity: Float = 0.6f,
    val brightness: Float = 1f,
)

val LocalHomeBackgroundStyle = staticCompositionLocalOf { HomeBackgroundSettings() }

@Composable
fun ScreenBackground(modifier: Modifier = Modifier, isVisible: Boolean = true) {
    if (!isVisible) return
    val homeBackground = LocalHomeBackgroundStyle.current
    Box(modifier = modifier) {
        when (homeBackground.style) {
            HomeBackgroundStyle.TONAL -> {
                HomePremiumBackground(
                    blobColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .align(Alignment.TopCenter)
                )
            }
            HomeBackgroundStyle.CIRCLES -> {
                CirclesBackground(
                    parallaxEnabled = homeBackground.parallaxEnabled,
                    parallaxSensitivity = homeBackground.parallaxSensitivity,
                    brightness = homeBackground.brightness,
                )
            }
        }
    }
}


