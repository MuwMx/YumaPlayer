package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import android.graphics.Bitmap
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.ExtractedColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberPreference

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

    if (needsBlur) {
        val preBlurred = rememberPreBlurredArtwork(targetUrl)

        Crossfade(
            targetState = preBlurred,
            animationSpec = tween(500),
            label = "BlurCrossfade"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            alpha = blurOverlayAlpha
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - blurOverlayAlpha }
            .drawWithCache {
                // Оставляем цвет сочным: подмешиваем всего 50-65% темного, а не 92%
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
            .clipToBounds()
    ) {
        Crossfade(
            targetState = currentClearPainter,
            animationSpec = tween(500),
            label = "ClearCrossfade"
        ) { painter ->
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

                val bottomAlpha = if (immersiveTransitionAlpha > 0f) 0.35f else 0.50f

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

/**
 * Backdrop blurred once off the main thread instead of with `Modifier.blur`: a blur
 * modifier is a live RenderEffect priced by render-target area, so a full-screen one is
 * re-paid on every redraw for as long as the player is open. Upscaling a 96px source
 * also leaves no detail, which is what the large radius was standing in for.
 */
@Composable
private fun rememberPreBlurredArtwork(imageUrl: String?): ImageBitmap? {
    val context = LocalContext.current

    val blurred by produceState<ImageBitmap?>(
        initialValue = imageUrl?.let(preBlurCache::get),
        imageUrl,
    ) {
        if (imageUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        preBlurCache[imageUrl]?.let { cached ->
            value = cached
            return@produceState
        }
        val bitmap = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCacheKey("blur:$imageUrl")
                .diskCacheKey(imageUrl)
                .size(PRE_BLUR_SOURCE_PX)
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        } ?: return@produceState

        val ready = withContext(Dispatchers.Default) {
            bitmap.boxBlurred(PRE_BLUR_PASSES).asImageBitmap()
        }
        preBlurCache[imageUrl] = ready
        value = ready
    }
    return blurred
}

private const val PRE_BLUR_SOURCE_PX = 96
private const val PRE_BLUR_PASSES = 3
private const val PRE_BLUR_CACHE_ENTRIES = 8

private val preBlurCache = object : LinkedHashMap<String, ImageBitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) =
        size > PRE_BLUR_CACHE_ENTRIES
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a shl 24) or (r shl 16) or (g shl 8) or b

private fun Bitmap.boxBlurred(passes: Int): Bitmap {
    val width = width
    val height = height
    if (width < 2 || height < 2) return this
    var source = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
    var target = IntArray(source.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)

    repeat(passes) {
        source.boxBlurPass(target, width, height, radius, horizontal = true)
        target.boxBlurPass(source, width, height, radius, horizontal = false)
    }
    return Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888)
}

private fun IntArray.boxBlurPass(
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    repeat(minor) { fixed ->
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) this[fixed * width + position] else this[position * width + fixed]
        }
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        repeat(major) { moving ->
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = argb(0xFF, red / window, green / window, blue / window)
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

private fun Modifier.artworkBottomFade(alpha: Float): Modifier = this
    .graphicsLayer { this.alpha = alpha }
    .drawWithCache {
        val fadeBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.45f to Color.Transparent,
            0.80f to Color.Black.copy(alpha = 0.7f),
            1.0f to Color.Black,
            startY = 0f,
            endY = size.height
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = fadeBrush)
        }
    }