/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Применяет эффект плавного растворения (fading edge) по краям видимой области скролла.
 * Применяется к родительскому Box (контейнеру сэндвича скролла) до вызова horizontalScroll.
 */
fun Modifier.horizontalFadingEdge(
    scrollState: ScrollState,
    length: Dp = 20.dp,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val lengthPx = length.toPx()
        val maxScroll = scrollState.maxValue.toFloat()
        if (maxScroll <= 0f || lengthPx <= 0f) return@drawWithContent

        val currentScroll = scrollState.value.toFloat()

        // Левый фейд (растворение слева при скролле вправо)
        val leftAlpha = (currentScroll / lengthPx).coerceIn(0f, 1f)
        if (leftAlpha > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 1f - leftAlpha),
                        Color.Black,
                    ),
                    startX = 0f,
                    endX = lengthPx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

        // Правый фейд (растворение справа при остатке скролла)
        val rightAlpha = ((maxScroll - currentScroll) / lengthPx).coerceIn(0f, 1f)
        if (rightAlpha > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black,
                        Color.Black.copy(alpha = 1f - rightAlpha),
                    ),
                    startX = size.width - lengthPx,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.smoothFadingEdge(
    top: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            val topPx = top.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Transparent,
                                0.3f to Color.Black.copy(alpha = 0.15f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.7f),
                                0.85f to Color.Black.copy(alpha = 0.9f),
                                1.0f to Color.Black,
                            ),
                        startY = 0f,
                        endY = topPx,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            val bottomPx = bottom.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Black,
                                0.15f to Color.Black.copy(alpha = 0.9f),
                                0.3f to Color.Black.copy(alpha = 0.7f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.15f),
                                1.0f to Color.Transparent,
                            ),
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.smoothFadingEdge(vertical: Dp) =
    smoothFadingEdge(
        top = vertical,
        bottom = vertical,
    )

