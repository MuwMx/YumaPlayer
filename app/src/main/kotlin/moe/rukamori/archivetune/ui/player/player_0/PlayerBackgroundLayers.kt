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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.ExtractedColors
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.FastBlurTransformation

@Composable
fun PlayerBackgroundLayers(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    gradientColor: Color = Color(state.gradientColor),
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
    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled && !isOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ImmersiveThemeTransition"
    )

    val targetUrl = state.coverUrl.takeIf { it.isNotEmpty() }

    val blurImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .size(240)
            .transformations(FastBlurTransformation(radius = 18, sampling = 1f))
            .build()
    }

    val clearImageRequest = remember(targetUrl) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .allowHardware(false)
            .build()
    }

    var currentClearPainter by remember { mutableStateOf<Painter?>(null) }
    var currentBlurPainter by remember { mutableStateOf<Painter?>(null) }
    var activeGradientColor by remember { mutableStateOf(gradientColor) }

    val blurPainter = rememberAsyncImagePainter(model = blurImageRequest)
    val blurState by blurPainter.state.collectAsState()

    val clearPainter = rememberAsyncImagePainter(model = clearImageRequest)
    val clearState by clearPainter.state.collectAsState()

    LaunchedEffect(targetUrl) {
        if (targetUrl != null) {
            val cached = colorCache[targetUrl]
            if (cached != null) {
                onColorsExtracted(cached.vibrant, cached.darkMuted, cached.gradient)
            }
        }
    }

    LaunchedEffect(clearState) {
        when (val s = clearState) {
            is AsyncImagePainter.State.Success -> {
                currentClearPainter = s.painter
                if (targetUrl != null) {
                    val cached = colorCache[targetUrl]
                    if (cached != null) {
                        onColorsExtracted(cached.vibrant, cached.darkMuted, cached.gradient)
                    } else {
                        val bitmap = runCatching { s.result.image.toBitmap() }.getOrNull()
                        if (bitmap != null) {
                            withContext(Dispatchers.Default) {
                                val colors = PlayerColorExtractor.extractColors(bitmap)
                                colorCache[targetUrl] = colors
                                withContext(Dispatchers.Main) {
                                    onColorsExtracted(colors.vibrant, colors.darkMuted, colors.gradient)
                                }
                            }
                        }
                    }
                }
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                currentClearPainter = null
            }
            else -> {}
        }
    }

    LaunchedEffect(clearState, gradientColor) {
        when (clearState) {
            is AsyncImagePainter.State.Success -> {
                activeGradientColor = gradientColor
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                activeGradientColor = Color(0xFF121212)
            }
            else -> {}
        }
    }

    LaunchedEffect(blurState) {
        when (val s = blurState) {
            is AsyncImagePainter.State.Success -> {
                currentBlurPainter = s.painter
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                currentBlurPainter = null
            }
            else -> {}
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

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = currentBlurPainter,
            animationSpec = tween(500),
            label = "BlurCrossfade"
        ) { painter ->
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = blurOverlayAlpha },
                    contentScale = ContentScale.Crop
                )
            }
        }

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
                        .graphicsLayer {
                            alpha = immersiveTransitionAlpha
                            compositingStrategy = if (immersiveTransitionAlpha > 0f) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                        }
                        .drawWithCache {
                            val maskBrush = Brush.verticalGradient(
                                0.0f to Color.Black,
                                0.80f to Color.Black,
                                1.0f to Color.Transparent,
                                startY = 0f,
                                endY = size.height
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = maskBrush,
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        },
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val tintVeil = lerp(Color.Black, animatedBgColor, 0.20f)
                val topAlpha = 0.05f
                val bottomAlpha = if (immersiveTransitionAlpha > 0f) 0.35f else 0.45f

                val veilBrush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = topAlpha),
                    0.40f to Color.Transparent,
                    0.75f to tintVeil.copy(alpha = bottomAlpha * 0.6f),
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