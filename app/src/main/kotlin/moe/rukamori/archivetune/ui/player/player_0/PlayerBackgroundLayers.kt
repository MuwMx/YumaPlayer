package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState

@OptIn(ExperimentalHazeApi::class)
@Composable
fun PlayerBackgroundLayers(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    gradientColor: Color = Color(state.gradientColor),
    expansionFractionProvider: () -> Float = { 1f },
    lyricsFractionProvider: () -> Float = { if (state.isLyricsVisible) 1f else 0f },
    queueFractionProvider: () -> Float = { 0f },
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit = { _, _, _ -> },
) {
    val blurOverlayAlpha by animateFloatAsState(
        targetValue = if (state.isBlurBackgroundEnabled) 1f else 0f,
        animationSpec = tween(500),
        label = "BlurOverlayTransition"
    )

    val isOverlayVisible by remember {
        derivedStateOf {
            state.isLyricsVisible || lyricsFractionProvider() > 0.5f || queueFractionProvider() > 0.5f
        }
    }
    val isLayerOnScreen by remember {
        derivedStateOf {
            expansionFractionProvider() > 0.005f
        }
    }
    val needsBlur by remember {
        derivedStateOf {
            (state.isBlurBackgroundEnabled || blurOverlayAlpha > 0.005f) && isLayerOnScreen
        }
    }
    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled && !isOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ImmersiveThemeTransition"
    )

    val targetUrl = state.coverUrl.trim().takeIf(String::isNotBlank)

    PlayerPaletteExtractor(
        targetUrl = targetUrl,
        trackUrl = state.trackUrl,
        onColorsExtracted = onColorsExtracted
    )

    val painters = rememberPlayerBackgroundPainters(
        targetUrl = targetUrl,
        needsBlur = needsBlur
    )

    val animatedBgColor by animateColorAsState(
        targetValue = gradientColor,
        animationSpec = tween(500),
        label = "VibrantGradientColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val midTone = lerp(animatedBgColor, Color(0xFF101010), 0.35f)
                val deepTone = lerp(animatedBgColor, Color(0xFF0A0A0A), 0.60f)
                val brush = Brush.verticalGradient(
                    0.0f to animatedBgColor,
                    0.50f to midTone,
                    1.0f to deepTone,
                    startY = 0f,
                    endY = size.height
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val playerHazeState = remember { HazeState() }
        if (needsBlur && painters.currentBlurPainter != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = blurOverlayAlpha }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(playerHazeState)
                ) {
                    Image(
                        painter = painters.currentBlurPainter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeEffect(
                            state = playerHazeState,
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                tint = HazeTint(Color.Transparent),
                                blurRadius = 56.dp,
                                noiseFactor = SettingsDimensions.HazeNoiseFactor,
                            )
                        ) {
                            inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
                        }
                )
            }
        }

        val painter = painters.currentClearPainter
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .align(Alignment.TopCenter)
                    .artworkBottomFade(immersiveTransitionAlpha),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }

        PlayerCanvasArtworkHost(
            state = state,
            immersiveTransitionAlpha = immersiveTransitionAlpha,
            lyricsFractionProvider = lyricsFractionProvider,
            queueFractionProvider = queueFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .align(Alignment.TopCenter)
                .artworkBottomFade(immersiveTransitionAlpha)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val tintVeil = lerp(Color.Black, animatedBgColor, 0.20f)

                val bottomAlpha = if (immersiveTransitionAlpha > 0f) 0.22f else 0.25f

                val veilBrush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.55f to tintVeil.copy(alpha = bottomAlpha * 0.35f),
                    0.75f to tintVeil.copy(alpha = bottomAlpha * 0.75f),
                    1.0f to tintVeil.copy(alpha = bottomAlpha),
                    startY = 0f,
                    endY = size.height
                )
                onDrawBehind {
                    drawRect(brush = veilBrush)
                }
            }
    )
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
