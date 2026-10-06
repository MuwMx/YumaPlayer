package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.glassBorder

@Composable
internal fun Modifier.sheetBackground(state: PlayerUiState): Modifier {
    val animatedDarkMuted by animateColorAsState(
        targetValue = Color(state.darkMutedColor),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "SheetDarkMutedAnimation",
    )

    val cardBackgroundBrush = remember(animatedDarkMuted) {
        val midTone = lerp(animatedDarkMuted, Color(0xFF101010), 0.35f)
        val deepTone = lerp(animatedDarkMuted, Color(0xFF0A0A0A), 0.60f)

        Brush.verticalGradient(
            0.0f to animatedDarkMuted,
            0.50f to midTone,
            1.0f to deepTone,
        )
    }

    val cardShape = remember { RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) }
    val blurBackgroundBrush = remember {
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.2f)))
    }

    return this
        .glassBorder(
            shape = cardShape,
            strokeWidth = SettingsDimensions.GlassBorderThickness,
            topAlpha = 0.20f,
            bottomAlpha = 0.04f,
        )
        .clip(cardShape)
        .background(
            if (state.isBlurBackgroundEnabled || state.isImmersiveEnabled) {
                blurBackgroundBrush
            } else {
                cardBackgroundBrush
            }
        )
}

@Composable
internal fun ColumnScope.PlayerSheetBorderContainer(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val cardShape = remember { RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .then(modifier)
            .clip(cardShape) // Режет контент строго по дуге скругления углов, а не прямоугольником
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    if (!constraints.hasBoundedWidth) {
                        val placeable = measurable.measure(constraints)
                        return@layout layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    val borderPx = 1.dp.roundToPx()
                    val expandedConstraints = constraints.copy(
                        minWidth = constraints.maxWidth + (borderPx * 2),
                        maxWidth = constraints.maxWidth + (borderPx * 2),
                        minHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.minHeight,
                        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + borderPx else constraints.maxHeight
                    )
                    val placeable = measurable.measure(expandedConstraints)
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.placeRelative(-borderPx, 0)
                    }
                }
                .sheetBackground(state)
        )
        content()
    }
}
