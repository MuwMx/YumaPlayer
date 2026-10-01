package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.toBitmap
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.ExtractedColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberPreference

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
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.surface.luminance() > 0.5f
    val standardVeilColor = if (isLightTheme) colorScheme.surface else Color.Black

    val colorCache = remember { ConcurrentHashMap<String, ExtractedColors>() }

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

    val (isCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
    var canvasArtwork by remember(state.trackUrl) { mutableStateOf<CanvasArtwork?>(null) }

    LaunchedEffect(isCanvasEnabled, state.trackUrl, state.title, state.artist) {
        canvasArtwork = null
        if (!isCanvasEnabled || state.trackUrl.isBlank()) {
            return@LaunchedEffect
        }
        CanvasArtworkPlaybackCache.get(state.trackUrl)?.let {
            canvasArtwork = it
            return@LaunchedEffect
        }
        val requestedTrackUrl = state.trackUrl
        val resolved = resolveCanvasArtworkForPlayback(
            mediaId = requestedTrackUrl,
            songTitleRaw = state.title,
            artistNameRaw = state.artist,
            storefront = "us",
            requireVertical = false,
            allowNetwork = true,
        )
        if (state.trackUrl == requestedTrackUrl) {
            canvasArtwork = resolved
        }
    }

    val canPlayImmersiveCanvas by remember {
        derivedStateOf {
            state.isPlaying &&
                    state.isImmersiveEnabled &&
                    immersiveTransitionAlpha > 0.05f &&
                    lyricsFractionProvider() < 0.05f &&
                    queueFractionProvider() < 0.05f
        }
    }

    val targetUrl = state.coverUrl.trim().takeIf(String::isNotBlank)

    val clearImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .apply {
                if (targetUrl != null) {
                    memoryCacheKey(targetUrl)
                    diskCacheKey(targetUrl)
                }
            }
            .crossfade(500)
            .build()
    }

    val paletteImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .apply {
                if (targetUrl != null) {
                    memoryCacheKey("palette:$targetUrl")
                    diskCacheKey(targetUrl)
                }
            }
            .size(128, 128)
            .allowHardware(false)
            .build()
    }

    var currentClearPainter by remember { mutableStateOf<Painter?>(null) }
    var activeGradientColor by remember { mutableStateOf(gradientColor) }

    val blurPainter =
        if (needsBlur) {
            val blurImageRequest = remember(targetUrl) {
                ImageRequest.Builder(context)
                    .data(targetUrl)
                    .apply {
                        if (targetUrl != null) {
                            memoryCacheKey("blur:$targetUrl")
                            diskCacheKey(targetUrl)
                        }
                    }
                    .size(300)
                    .allowHardware(true)
                    .crossfade(400)
                    .build()
            }
            rememberAsyncImagePainter(model = blurImageRequest)
        } else {
            null
        }

    val clearPainter = rememberAsyncImagePainter(model = clearImageRequest)

    LaunchedEffect(gradientColor, targetUrl) {
        if (targetUrl == null) {
            activeGradientColor = Color(0xFF121212)
        } else {
            activeGradientColor = gradientColor
        }
    }

    LaunchedEffect(targetUrl, state.trackUrl) {
        if (targetUrl == null) {
            currentClearPainter = null
            return@LaunchedEffect
        }

        val requestedTargetUrl = targetUrl
        val requestedTrackUrl = state.trackUrl

        val cached = colorCache[requestedTargetUrl]
        if (cached != null) {
            onColorsExtracted(cached.vibrant, cached.darkMuted, cached.gradient)
        } else {
            launch {
                val result = runCatching {
                    context.imageLoader.execute(paletteImageRequest)
                }.getOrNull()
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { result?.image?.toBitmap() }.getOrNull()
                }
                if (bitmap != null &&
                    targetUrl == requestedTargetUrl &&
                    state.trackUrl == requestedTrackUrl
                ) {
                    val colors = withContext(Dispatchers.Default) {
                        PlayerColorExtractor.extractColors(bitmap)
                    }
                    if (targetUrl == requestedTargetUrl &&
                        state.trackUrl == requestedTrackUrl
                    ) {
                        colorCache[requestedTargetUrl] = colors
                        onColorsExtracted(colors.vibrant, colors.darkMuted, colors.gradient)
                    }
                }
            }
        }

        clearPainter.state.collect { s ->
            when (s) {
                is AsyncImagePainter.State.Success -> {
                    if (targetUrl == requestedTargetUrl &&
                        state.trackUrl == requestedTrackUrl &&
                        s.result.request.data == requestedTargetUrl
                    ) {
                        currentClearPainter = s.painter
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    if (targetUrl == requestedTargetUrl &&
                        state.trackUrl == requestedTrackUrl &&
                        s.result.request.data == requestedTargetUrl
                    ) {
                        currentClearPainter = null
                    }
                }
                else -> {}
            }
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = activeGradientColor,
        animationSpec = tween(500),
        label = "VibrantGradientColor"
    )

    if (!needsBlur || blurOverlayAlpha < 0.99f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - blurOverlayAlpha }
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
                        // The blurred artwork above is opaque once the fade completes, so
                        // this full-screen rect would rasterize for nothing.
                        if (blurOverlayAlpha >= 0.99f) return@onDrawBehind
                        drawRect(brush = brush)
                    }
                }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val playerHazeState = remember { HazeState() }
        val activeBlurPainter = blurPainter
        if (needsBlur && activeBlurPainter != null) {
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
                        painter = activeBlurPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
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
                                blurRadius = 32.dp,
                                noiseFactor = SettingsDimensions.HazeNoiseFactor,
                            )
                        ) {
                            inputScale = HazeInputScale.Fixed(SettingsDimensions.HazeInputScaleValue)
                        }
                )
            }
        }

        val painter = currentClearPainter
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

        if (state.isImmersiveEnabled && isCanvasEnabled && canvasArtwork != null) {
            key(state.trackUrl) {
                CanvasArtworkPlayer(
                    primaryUrl = canvasArtwork?.preferredAnimationUrl,
                    fallbackUrl = canvasArtwork?.fallbackUrl,
                    isPlaying = canPlayImmersiveCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .align(Alignment.TopCenter)
                        .artworkBottomFade(immersiveTransitionAlpha),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val tintVeil = lerp(Color.Black, animatedBgColor, 0.20f)

                val bottomAlpha = if (immersiveTransitionAlpha > 0f) 0.18f else 0.25f

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

private val CanvasArtwork.fallbackUrl: String?
    get() = videoUrl.takeIf { it != preferredAnimationUrl }

private fun Modifier.artworkBottomFade(alpha: Float): Modifier = this
    .graphicsLayer {
        this.alpha = alpha
        this.compositingStrategy = CompositingStrategy.Offscreen
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