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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

    val clearPainter = rememberAsyncImagePainter(model = clearImageRequest)
    val clearState by clearPainter.state.collectAsState()

    val blurPainter = rememberAsyncImagePainter(model = blurImageRequest)
    val blurState by blurPainter.state.collectAsState()

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
        when (blurState) {
            is AsyncImagePainter.State.Success -> {
                currentBlurPainter = blurState.painter
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

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - blurOverlayAlpha }
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        colors = listOf(animatedBgColor, Color(0xFF121212)),
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
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                val maskBrush = Brush.verticalGradient(
                                    0.0f to Color.Black,
                                    0.65f to Color.Black,
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
                .graphicsLayer { alpha = (1f - blurOverlayAlpha) * (1f - immersiveTransitionAlpha) }
                .drawWithCache {
                    val veilBrush = Brush.verticalGradient(
                        colors = listOf(
                            standardVeilColor.copy(alpha = 0.50f),
                            standardVeilColor.copy(alpha = 0.30f),
                            standardVeilColor.copy(alpha = 0.70f)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(brush = veilBrush)
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blurOverlayAlpha * (1f - immersiveTransitionAlpha) }
                .drawWithCache {
                    val veilBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f)
                        ),
                        startY = 0f,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(brush = veilBrush)
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = immersiveTransitionAlpha }
                .drawWithCache {
                    val veilBrush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.65f to Color.Black.copy(alpha = 0.35f),
                        1.0f to Color.Black.copy(alpha = 0.30f),
                        startY = 0f,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(brush = veilBrush)
                    }
                }
        )
    }
}