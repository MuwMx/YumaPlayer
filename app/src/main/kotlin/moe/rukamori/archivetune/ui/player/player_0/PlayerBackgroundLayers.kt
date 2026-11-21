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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.abs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import moe.rukamori.archivetune.ui.player.ambient.AmbientBackdrop
import moe.rukamori.archivetune.ui.player.ambient.FollowPicture
import moe.rukamori.archivetune.ui.player.ambient.ToneSeamRight
import moe.rukamori.archivetune.ui.player.ambient.pictureTones
import moe.rukamori.archivetune.ui.player.ambient.rememberAmbientColors
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState

private const val ImmersiveSwipeParallax = 0.35f

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

    val density = LocalDensity.current
    val context = LocalContext.current
    val cardGapPx = with(density) { 16.dp.toPx() }
    var slotTones by remember { mutableStateOf<Map<String, List<Color>>>(emptyMap()) }

    LaunchedEffect(carousel?.slots) {
        val slots = carousel?.slots ?: run {
            if (slotTones.isNotEmpty()) slotTones = emptyMap()
            return@LaunchedEffect
        }
        val activeUrls = slots.mapNotNull { it.artworkUri?.trim()?.takeIf(String::isNotBlank) }.toSet()
        val nextTones = slotTones.filterKeys { it in activeUrls }.toMutableMap()
        for (url in activeUrls) {
            if (!nextTones.containsKey(url)) {
                val tones = pictureTones(context, url)
                if (tones != null) {
                    nextTones[url] = tones
                }
            }
        }
        slotTones = nextTones
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        if (!state.isLiveBackgroundEnabled) {
            if (carousel != null && carousel.slots.isNotEmpty()) {
                for (slot in carousel.slots) {
                    key(slot.uid) {
                        val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                        val slotBgColor = slotUrl?.let { slotTones[it]?.getOrNull(ToneSeamRight) } ?: animatedBgColor
                        Spacer(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val o = carousel.offsetOf(slot)
                                    val parallax = if (state.isImmersiveEnabled) ImmersiveSwipeParallax else 1f
                                    translationX = o * (size.width + if (!state.isImmersiveEnabled) cardGapPx else 0f) * parallax
                                    alpha = (1f - abs(o)).coerceIn(0f, 1f)
                                }
                                .drawWithCache {
                                    val currentBg = slotBgColor
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
                        )
                    }
                }
            } else {
                Spacer(
                    modifier = Modifier
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
                )
            }
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

        val painters = rememberPlayerBackgroundPainters(
            targetUrl = targetUrl,
            needsBlur = needsBlur
        )


        if (needsBlur) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = blurOverlayAlpha
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(playerHazeState)
                ) {
                    if (carousel != null && carousel.slots.isNotEmpty()) {
                        for (slot in carousel.slots) {
                            key(slot.uid) {
                                val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                                val slotPainters = rememberPlayerBackgroundPainters(
                                    targetUrl = slotUrl,
                                    needsBlur = needsBlur
                                )
                                val blurPainter = slotPainters.currentBlurPainter
                                if (blurPainter != null) {
                                    Image(
                                        painter = blurPainter,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val o = carousel.offsetOf(slot)
                                                val parallax = if (state.isImmersiveEnabled) ImmersiveSwipeParallax else 1f
                                                translationX = o * (size.width + if (!state.isImmersiveEnabled) cardGapPx else 0f) * parallax
                                                alpha = (1f - abs(o)).coerceIn(0f, 1f)
                                            },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    } else {
                        val blurPainter = painters.currentBlurPainter
                        if (blurPainter != null) {
                            Image(
                                painter = blurPainter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
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

        var artworkWidthPx by remember { mutableFloatStateOf(0f) }
        val isImmersiveSwipeEnabled = state.isImmersiveEnabled &&
            !currentState.isLyricsVisible &&
            currentLyricsProvider() < 0.05f &&
            currentQueueProvider() < 0.05f

        if (carousel != null && carousel.slots.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .align(Alignment.TopCenter)
                    .onSizeChanged { artworkWidthPx = it.width.toFloat() }
            ) {
                for (slot in carousel.slots) {
                    key(slot.uid) {
                        val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                        val slotRequest = remember(slotUrl) {
                            ImageRequest.Builder(context)
                                .data(slotUrl)
                                .apply {
                                    if (slotUrl != null) {
                                        memoryCacheKey(slotUrl)
                                        diskCacheKey(slotUrl)
                                    }
                                }
                                .build()
                        }
                        val slotPainter = rememberAsyncImagePainter(model = slotRequest)
                        val isCurrent = slot.step == carousel.step
                        if (!(isCurrent && isCanvasActive && isCanvasReady)) {
                            Image(
                                painter = slotPainter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val o = carousel.offsetOf(slot)
                                        translationX = o * size.width * ImmersiveSwipeParallax
                                        alpha = (1f - abs(o)).coerceIn(0f, 1f) * immersiveTransitionAlpha * queueArtworkFade
                                    }
                                    .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
                        }
                    }
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
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = carousel.playingOffset * size.width * ImmersiveSwipeParallax
                                alpha = (1f - abs(carousel.playingOffset)).coerceIn(0f, 1f)
                            }
                            .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade)
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselDragGesture(
                            carousel = carousel,
                            enabled = isImmersiveSwipeEnabled,
                            spacingPxProvider = {
                                (artworkWidthPx * ImmersiveSwipeParallax)
                                    .takeIf { it > 0f }
                                    ?: with(density) { 360.dp.toPx() }
                            },
                            flingThresholdPx = with(density) { 400.dp.toPx() },
                            onNext = { carousel.swipeSkip?.invoke(true) },
                            onPrevious = { carousel.swipeSkip?.invoke(false) },
                        )
                )
            }
        } else {
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
