package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.state.PlayerUiState
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

    val immersiveMaskBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Black,
            0.65f to Color.Black,
            1.0f to Color.Transparent
        )
    }

    val standardVeilBrush = remember(standardVeilColor) {
        Brush.verticalGradient(
            colors = listOf(
                standardVeilColor.copy(alpha = 0.50f),
                standardVeilColor.copy(alpha = 0.30f),
                standardVeilColor.copy(alpha = 0.70f)
            )
        )
    }

    val blurVeilBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.45f),
                Color.Black.copy(alpha = 0.30f),
                Color.Black.copy(alpha = 0.72f)
            )
        )
    }

    val immersiveVeilBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.35f to Color.Transparent,
            0.65f to Color.Black.copy(alpha = 0.35f),
            1.0f to Color.Black.copy(alpha = 0.30f)
        )
    }

    val blurOverlayAlpha by animateFloatAsState(
        targetValue = if (state.isBlurBackgroundEnabled) 1f else 0f,
        animationSpec = tween(500),
        label = "BlurOverlayTransition"
    )

    val isOverlayVisible = state.isLyricsVisible || lyricsFractionProvider() > 0.5f || queueFractionProvider() > 0.5f

    val immersiveTransitionAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled && !isOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
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

    LaunchedEffect(clearState) {
        when (val state = clearState) {
            is AsyncImagePainter.State.Success -> {
                currentClearPainter = state.painter
                val bitmap = runCatching { state.result.image.toBitmap() }.getOrNull()
                if (bitmap != null) {
                    withContext(Dispatchers.Default) {
                        val colors = PlayerColorExtractor.extractColors(bitmap)
                        onColorsExtracted(colors.vibrant, colors.darkMuted, colors.gradient)
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
            else -> {
                // Keep previous state during Loading
            }
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
                .drawBehind {
                    val brush = Brush.verticalGradient(
                        colors = listOf(animatedBgColor, Color(0xFF121212))
                    )
                    drawRect(brush = brush)
                }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.animation.Crossfade(
                targetState = currentBlurPainter,
                animationSpec = tween(500),
                label = "BlurCrossfade"
            ) { painter ->
                if (painter != null) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = blurOverlayAlpha },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            androidx.compose.animation.Crossfade(
                targetState = currentClearPainter,
                animationSpec = tween(500),
                label = "ClearCrossfade"
            ) { painter ->
                if (painter != null) {
                    androidx.compose.foundation.Image(
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
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = immersiveMaskBrush,
                                    blendMode = BlendMode.DstIn
                                )
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
                .background(standardVeilBrush)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = blurOverlayAlpha * (1f - immersiveTransitionAlpha) }
                .background(blurVeilBrush)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = immersiveTransitionAlpha }
                .background(immersiveVeilBrush)
        )
    }
}