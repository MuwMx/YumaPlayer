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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import moe.rukamori.archivetune.ui.component.LikeBurstState
import moe.rukamori.archivetune.ui.player.ambient.AmbientBackdrop
import moe.rukamori.archivetune.ui.player.ambient.FollowPicture
import moe.rukamori.archivetune.ui.player.ambient.rememberAmbientColors
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState

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
    likeBurst: LikeBurstState? = null,
    onAction: (PlayerAction) -> Unit = {},
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
            (currentState.isBlurBackgroundEnabled || blurOverlayAlpha > 0.005f) && isLayerOnScreen
        }
    }

    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled && !isOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ImmersiveThemeTransition"
    )

    val carousel = LocalSongCarousel.current
    val closestSlot = carousel?.slots?.minByOrNull { abs(carousel.offsetOf(it)) }
    val targetUrl = closestSlot?.artworkUri?.trim()?.takeIf(String::isNotBlank)
        ?: state.coverUrl.trim().takeIf(String::isNotBlank)

    key(state.trackUrl) {
        PlayerPaletteExtractor(
            targetUrl = targetUrl,
            trackUrl = state.trackUrl,
            onColorsExtracted = onColorsExtracted
        )
    }

    val animatedBgColor by animateColorAsState(
        targetValue = gradientColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "VibrantGradientColor"
    )

    val glowTransition by animateFloatAsState(
        targetValue = if (state.isAlbumCoverGlowEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "GlowInvertedBackgroundTransition"
    )

    val ambientColors = rememberAmbientColors()
    FollowPicture(colors = ambientColors, url = targetUrl.takeIf { state.isLiveBackgroundEnabled })

    var backgroundRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .onGloballyPositioned { backgroundRootCoordinates = it }
    ) {
        if (!state.isLiveBackgroundEnabled) {
            PlayerGradientBackdrop(
                carousel = carousel,
                animatedBgColor = animatedBgColor,
                glowTransition = glowTransition
            )
        }

        if (state.isLiveBackgroundEnabled) {
            AmbientBackdrop(
                colors = ambientColors,
                modifier = Modifier.fillMaxSize(),
                running = isLayerOnScreen,
            )
            if (glowTransition > 0.01f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val currentGlow = glowTransition
                            val glowScrim = Brush.verticalGradient(
                                0.0f to Color(0xFF080808).copy(alpha = 0.85f * currentGlow),
                                0.45f to Color(0xFF080808).copy(alpha = 0.65f * currentGlow),
                                0.85f to Color.Transparent,
                                startY = 0f,
                                endY = size.height,
                            )
                            onDrawBehind {
                                drawRect(brush = glowScrim)
                            }
                        }
                )
            }
        }

        val shouldRenderHeavyBackground by remember {
            derivedStateOf { currentExpansionProvider() > 0.05f }
        }

        if (!shouldRenderHeavyBackground) {
            return@Box
        }

        PlayerBlurBackdrop(
            carousel = carousel,
            targetUrl = targetUrl,
            needsBlur = needsBlur,
            blurOverlayAlpha = blurOverlayAlpha
        )

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

        if (carousel != null && carousel.slots.isNotEmpty()) {
            PlayerImmersiveCoverCarousel(
                carousel = carousel,
                state = state,
                canvasState = canvasState,
                immersiveTransitionAlpha = immersiveTransitionAlpha,
                queueArtworkFade = queueArtworkFade,
                isCanvasActive = isCanvasActive,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                backgroundRootCoordinates = backgroundRootCoordinates,
                modifier = Modifier.align(Alignment.TopCenter),
                likeBurst = likeBurst,
                onAction = onAction
            )
        } else {
            val painters = rememberPlayerBackgroundPainters(
                targetUrl = targetUrl,
                needsBlur = needsBlur
            )
            var isCanvasReady by remember(state.trackUrl) { mutableStateOf(false) }

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
}
