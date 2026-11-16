/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

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
    const val BottomFadeStart = 0.72f
    const val TopFadeIntensity = 0.75f
    const val BottomFadeIntensity = 1.0f
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
        val progress = (q / PlayerArtworkFadeDefaults.PeekFraction).coerceIn(0f, 1f)

        val topEdgeAlpha = 1f - (progress * PlayerArtworkFadeDefaults.TopFadeIntensity)
        val bottomEdgeAlpha = 1f - (progress * PlayerArtworkFadeDefaults.BottomFadeIntensity)

        val mask = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = topEdgeAlpha),
                PlayerArtworkFadeDefaults.TopFadeEnd to Color.Black,
                PlayerArtworkFadeDefaults.BottomFadeStart to Color.Black,
                1f to Color.Black.copy(alpha = bottomEdgeAlpha),
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

internal fun Modifier.artworkBottomFade(alpha: Float): Modifier = this
    .graphicsLayer {
        this.alpha = alpha
        this.compositingStrategy = if (alpha > 0.01f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithCache {
        val maskBrush = Brush.verticalGradient(
            0.0f to Color.Black,
            0.45f to Color.Black,
            0.75f to Color.Black.copy(alpha = 0.4f),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = size.height
        )
        onDrawWithContent {
            if (alpha < 0.01f) return@onDrawWithContent
            drawContent()
            drawRect(
                brush = maskBrush,
                blendMode = BlendMode.DstIn
            )
        }
    }