package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
fun PlayerBackgroundLayers(
    state: PlayerUiState,
    gradientBrush: Brush,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Оптимизированный запрос для блюра (низкое разрешение 128x128)
    val blurImageRequest = remember(state.coverUrl) {
        ImageRequest.Builder(context)
            .data(state.coverUrl)
            .size(128)
            .build()
    }

    // Переключатель альфы: 0f = градиент, 1f = блюр
    val themeTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isBlurBackgroundEnabled) 1f else 0f,
        animationSpec = tween(500),
        label = "PlayerThemeTransition"
    )

    // Переключатель иммерсивного режима (выключается при тексте песен)
    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled && !state.isLyricsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "ImmersiveThemeTransition"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. СЛОЙ ГРАДИЕНТА
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - themeTransitionAlpha }
                .background(gradientBrush)
        )

        // 2. СЛОЙ БЛЮРА И ИММЕРСИВНОГО ФОНА С КРОССФЕЙДОМ
        Crossfade(
            targetState = state.coverUrl,
            animationSpec = tween(durationMillis = 800),
            label = "BackgroundCoverCrossfade"
        ) { url ->
            if (url.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // А. Оптимизированный блюр
                    AsyncImage(
                        model = blurImageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = themeTransitionAlpha
                                clip = true
                            }
                            .blur(32.dp),
                        contentScale = ContentScale.Crop
                    )

                    // Б. Иммерсивная четкая обложка с мягким затуханием книзу
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                alpha = immersiveTransitionAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.0f to Color.Black,
                                        0.75f to Color.Black,
                                        1.0f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                }
            }
        }

        // 3. СТАНДАРТНОЕ ЗАТЕМНЕНИЕ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - immersiveTransitionAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // 4. ДВОЙНОЕ ЗАТЕМНЕНИЕ ДЛЯ ИММЕРСИВНОГО РЕЖИМА
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = immersiveTransitionAlpha }
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.18f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.90f)
                    )
                )
        )
    }
}
