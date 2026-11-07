/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState

@OptIn(ExperimentalHazeApi::class)
@Composable
fun PlayerBackgroundLayers(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    canvasState: PlayerCanvasState = rememberPlayerCanvasState(state.trackUrl, state.title, state.artist),
    gradientColor: Color = Color(state.gradientColor),
    expansionFractionProvider: () -> Float = { 1f },
    lyricsFractionProvider: () -> Float = { if (state.isLyricsVisible) 1f else 0f },
    queueFractionProvider: () -> Float = { 0f },
    onColorsExtracted: (vibrant: Int, darkMuted: Int, gradient: Int) -> Unit = { _, _, _ -> },
) {

    val currentState by rememberUpdatedState(state)
    val currentCanvasState by rememberUpdatedState(canvasState)
    val currentExpansionProvider by rememberUpdatedState(expansionFractionProvider)
    val currentLyricsProvider by rememberUpdatedState(lyricsFractionProvider)
    val currentQueueProvider by rememberUpdatedState(queueFractionProvider)

    val blurOverlayAlpha by animateFloatAsState(
        targetValue = if (state.isBlurBackgroundEnabled) 1f else 0f,
        animationSpec = tween(500),
        label = "BlurOverlayTransition"
    )
    val playerHazeState = remember { HazeState() }

    val isOverlayVisible by remember {
        derivedStateOf {
            currentState.isLyricsVisible || currentLyricsProvider() > 0.5f
        }
    }

    val isLayerOnScreen by remember {
        derivedStateOf { currentExpansionProvider() > 0.005f }
    }
    val needsBlur by remember {
        derivedStateOf {
            val lyricsFraction = currentLyricsProvider().coerceIn(0f, 1f)
            val isLyricsFullyOpen = lyricsFraction >= 0.99f
            (currentState.isBlurBackgroundEnabled || blurOverlayAlpha > 0.005f) && isLayerOnScreen && !isLyricsFullyOpen
        }
    }
    val needsGlow by remember {
        derivedStateOf {
            val lyricsFraction = currentLyricsProvider().coerceIn(0f, 1f)
            val isLyricsFullyOpen = lyricsFraction >= 0.99f
            currentState.isAlbumCoverGlowEnabled && !isLyricsFullyOpen
        }
    }
    val needsImmersive by remember {
        derivedStateOf {
            val lyricsFraction = currentLyricsProvider().coerceIn(0f, 1f)
            val isLyricsFullyOpen = lyricsFraction >= 0.99f
            currentState.isImmersiveEnabled && !isOverlayVisible && !isLyricsFullyOpen
        }
    }
    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (needsImmersive) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ImmersiveThemeTransition"
    )

    val targetUrl = state.coverUrl.trim().takeIf(String::isNotBlank)


    key(state.trackUrl) {
        PlayerPaletteExtractor(
            targetUrl = targetUrl,
            trackUrl = state.trackUrl,
            onColorsExtracted = onColorsExtracted
        )
    }


    // ПОСТАВИТЬ:
    val animatedBgColor by animateColorAsState(
        targetValue = gradientColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "VibrantGradientColor"
    )

    val glowTransition by animateFloatAsState(
        targetValue = if (needsGlow) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "GlowInvertedBackgroundTransition"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val currentBg = animatedBgColor
                val currentGlow = glowTransition

                val charcoalTone = lerp(Color(0xFF080808), currentBg, 0.05f)
                val midTone = lerp(currentBg, Color(0xFF101010), 0.50f)
                val deepTone = lerp(currentBg, Color(0xFF060606), 0.90f)

                val stop0 = lerp(currentBg, charcoalTone, currentGlow)
                val stop35 = lerp(currentBg, charcoalTone, currentGlow)
                val stop70 = lerp(midTone, currentBg.copy(alpha = 0.70f), currentGlow)
                val stop100 = lerp(deepTone, charcoalTone, currentGlow)

                val brush = Brush.verticalGradient(
                    0.0f to stop0,
                    0.35f to stop35,
                    0.70f to stop70,
                    1.0f to stop100,
                    startY = 0f,
                    endY = size.height
                )

                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    ) {
        val shouldRenderHeavyBackground by remember {
            derivedStateOf { currentExpansionProvider() > 0.05f }
        }

        if (!shouldRenderHeavyBackground) {
            return@Box
        }

        val painters = rememberPlayerBackgroundPainters(
            targetUrl = targetUrl,
            needsBlur = needsBlur
        )


        if (needsBlur && painters.currentBlurPainter != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val lyricsFraction = currentLyricsProvider().coerceIn(0f, 1f)
                        alpha = blurOverlayAlpha * (1f - lyricsFraction)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(playerHazeState)
                ) {
                    Image(
                        painter = painters.currentBlurPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeBlur(
                            input = HazeInput.Sources(playerHazeState),
                            style = HazeBlurStyle {
                                val tintEffect = HazeColorEffect.tint(Color.Black.copy(alpha = 0.35f))
                                backgroundColor(Color.Transparent)
                                colorEffects(listOf(tintEffect))
                                fallbackColorEffect(tintEffect)
                                blurRadius(56.dp)
                                noiseFactor(SettingsDimensions.HazeNoiseFactor)
                            },
                            performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                        )
                )
            }
        }

        var isCanvasReady by remember(state.trackUrl) { mutableStateOf(false) }

        val isCanvasActive by remember {
            derivedStateOf {
                currentCanvasState.isCanvasEnabled &&
                        currentCanvasState.artwork != null &&
                        currentExpansionProvider() > 0.05f
            }
        }

        val queueArtworkFade by remember {
            derivedStateOf {
                (1f - (currentQueueProvider() - 0.45f) / (0.85f - 0.45f)).coerceIn(0f, 1f)
            }
        }

        val painter = painters.currentClearPainter
        if (painter != null && !(isCanvasActive && isCanvasReady)) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .align(Alignment.TopCenter)
                    .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }

        if (isCanvasActive) {
            PlayerCanvasArtworkHost(
                state = state,
                canvasState = canvasState,
                immersiveTransitionAlpha = immersiveTransitionAlpha * queueArtworkFade,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                onReadyChange = { isCanvasReady = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .align(Alignment.TopCenter)
                    .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade)
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