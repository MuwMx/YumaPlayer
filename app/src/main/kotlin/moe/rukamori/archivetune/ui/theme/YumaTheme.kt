package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class YumaColorScheme(
    val glassBackground: Color,
    val glassBorder: Color,
    val cardBackgroundOpaque: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val rippleColor: Color
)

fun lightYumaColorScheme(
    colorScheme: ColorScheme
): YumaColorScheme = YumaColorScheme(
    glassBackground = Color.White.copy(alpha = 0.65f),
    glassBorder = colorScheme.outline.copy(alpha = 0.25f),
    cardBackgroundOpaque = colorScheme.surfaceContainerLow,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurface.copy(alpha = 0.65f),
    rippleColor = colorScheme.onSurface.copy(alpha = 0.12f)
)

fun darkYumaColorScheme(
    colorScheme: ColorScheme
): YumaColorScheme = YumaColorScheme(
    glassBackground = colorScheme.onSurface.copy(alpha = 0.06f),
    glassBorder = colorScheme.outline.copy(alpha = 0.10f),
    cardBackgroundOpaque = Color(0xFF1C1C1E),
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurface.copy(alpha = 0.65f),
    rippleColor = colorScheme.onSurface.copy(alpha = 0.16f)
)

val LocalYumaColors = staticCompositionLocalOf<YumaColorScheme> {
    error("No YumaColorScheme provided. Make sure to wrap content in YumaTheme.")
}

@Composable
fun YumaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val yumaColors = remember(darkTheme, colorScheme) {
        if (darkTheme) {
            darkYumaColorScheme(colorScheme)
        } else {
            lightYumaColorScheme(colorScheme)
        }
    }

    CompositionLocalProvider(
        LocalYumaColors provides yumaColors,
        content = content
    )
}