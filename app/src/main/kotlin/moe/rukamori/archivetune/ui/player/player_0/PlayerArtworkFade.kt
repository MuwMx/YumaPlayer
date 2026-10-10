package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

internal object PlayerArtworkFadeDefaults {
    const val PeekFraction = 0.45f

    const val TopFadeEnd = 0.18f
    const val BottomFadeStart = 0.78f
}

internal fun Modifier.playerArtworkFade(
    queueFractionProvider: () -> Float,
): Modifier = this
    .graphicsLayer {
        val q = queueFractionProvider().coerceIn(0f, 1f)
        val progress = (q / PlayerArtworkFadeDefaults.PeekFraction).coerceIn(0f, 1f)
        compositingStrategy = if (progress > 0.005f) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithCache {
        val q = queueFractionProvider().coerceIn(0f, 1f)

        val progress = (
            q / PlayerArtworkFadeDefaults.PeekFraction
        ).coerceIn(0f, 1f)

        val edgeAlpha = 1f - progress

        val mask = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = edgeAlpha),
                PlayerArtworkFadeDefaults.TopFadeEnd to Color.Black,
                PlayerArtworkFadeDefaults.BottomFadeStart to Color.Black,
                1f to Color.Black.copy(alpha = edgeAlpha),
            ),
            startY = 0f,
            endY = size.height,
        )

        onDrawWithContent {
            drawContent()

            if (progress > 0f) {
                drawRect(
                    brush = mask,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
