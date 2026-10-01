package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun rememberMiniHazeStyle(
    pureBlack: Boolean,
    blurRadius: Float,
): HazeStyle {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val fixedTintAlpha = if (pureBlack) SettingsDimensions.HazePureBlackTintAlpha else SettingsDimensions.HazeDefaultTintAlpha
    return remember(containerColor, blurRadius, pureBlack) {
        HazeDefaults.style(
            backgroundColor = containerColor,
            tint = HazeTint(containerColor.copy(alpha = fixedTintAlpha)),
            blurRadius = blurRadius.dp,
            noiseFactor = SettingsDimensions.HazeNoiseFactor,
        )
    }
}

@Composable
internal fun rememberBackgroundGradient(gradientColor: Int): Brush {
    return remember(gradientColor) {
        Brush.verticalGradient(
            listOf(
                Color(gradientColor),
                Color(0xFF121212)
            )
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.playerSheetBackdrop(
    hazeState: HazeState?,
    hazeStyle: HazeStyle,
    isDragging: Boolean = false,
): Modifier = if (hazeState != null) {
    this.hazeEffect(
        state = hazeState,
        style = hazeStyle
    ) {
        inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
        this.blurEnabled = !isDragging
    }
} else {
    this
}

internal fun Modifier.playerSheetGlassBorder(
    expansionFractionProvider: () -> Float,
    density: Density,
): Modifier = this.drawWithCache {
    val strokeWidthPx = SettingsDimensions.GlassBorderThickness.toPx()
    val halfStroke = strokeWidthPx / 2f
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = SettingsDimensions.GlassBorderTopAlpha),
        1.0f to Color.Black.copy(alpha = SettingsDimensions.GlassBorderBottomAlpha)
    )

    onDrawWithContent {
        val fraction = expansionFractionProvider()
        drawContent()

        if (fraction < SettingsDimensions.FullyExpandedThreshold) {
            val borderFade = (1f - (fraction / SettingsDimensions.ExpansionThresholdFraction)).coerceIn(0f, 1f)
            if (borderFade > 0f) {
                val radiusPx = with(density) {
                    androidx.compose.ui.unit.lerp(32.dp, 0.dp, fraction.coerceIn(0f, 1f)).toPx()
                }
                drawRoundRect(
                    brush = borderBrush,
                    alpha = borderFade,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }
    }
}

@Composable
internal fun rememberPlayerSheetPillShape(
    density: Density,
    expansionFraction: Animatable<Float, *>,
): Shape = remember(density) {
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
            val fraction = expansionFraction.value
            if (fraction >= 0.99f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
            val radiusPx = with(density) {
                androidx.compose.ui.unit.lerp(32.dp, 0.dp, fraction.coerceIn(0f, 1f)).toPx()
            }
            val corner = CornerRadius(radiusPx, radiusPx)
            return Outline.Rounded(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = corner,
                    topRightCornerRadius = corner,
                    bottomRightCornerRadius = corner,
                    bottomLeftCornerRadius = corner
                )
            )
        }
    }
}
