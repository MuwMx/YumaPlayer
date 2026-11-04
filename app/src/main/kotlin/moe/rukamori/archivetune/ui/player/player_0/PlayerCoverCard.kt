package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import kotlin.math.abs

@Composable
fun PlayerCoverCard(
    coverUrl: String? = null,
    modifier: Modifier = Modifier,
    placeholderResId: Int,
    vibrantColor: Color = Color.Transparent,
    gestureEnabled: Boolean = true,
    mediaId: String? = null,
    songTitle: String? = null,
    artistName: String? = null,
    isPlaying: Boolean = false,
    canvasHostEnabled: Boolean = true,
    canvasState: PlayerCanvasState = rememberPlayerCanvasState(mediaId, songTitle, artistName),
    onCoverLoaded: () -> Unit = {}, // <-- Новый колбэк
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
) {
    val context = LocalContext.current
    val shadowColor = MaterialTheme.colorScheme.scrim
    val surfaceColor: Color = MaterialTheme.colorScheme.surface

    val haptics = LocalYumaHaptics.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val offsetX = remember { Animatable(0f) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }

    val maxTensionOffsetPx = with(density) { 60.dp.toPx() }
    val snapThresholdPx = with(density) { 80.dp.toPx() }

    var currentPainter by remember { mutableStateOf<Painter?>(null) }
    var activeVibrantColor by remember { mutableStateOf(Color.Transparent) }

    val cleanUrl = coverUrl?.trim()?.takeIf(String::isNotBlank)

    val request = remember(cleanUrl) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .apply {
                if (cleanUrl != null) {
                    memoryCacheKey(cleanUrl)
                    diskCacheKey(cleanUrl)
                }
            }
            .build()
    }

    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()

    var previousPainter by remember { mutableStateOf<Painter?>(null) }
    var lastLoadedMediaId by remember { mutableStateOf(mediaId) }
    val newArtAlpha = remember { Animatable(1f) }

    LaunchedEffect(cleanUrl, mediaId) {
        if (cleanUrl == null) {
            currentPainter = null
            previousPainter = null
            activeVibrantColor = Color.Transparent
            if (lastLoadedMediaId != mediaId) {
                lastLoadedMediaId = mediaId
                onCoverLoaded()
            }
        } else if (currentPainter != null && lastLoadedMediaId != mediaId) {
            previousPainter = currentPainter
            newArtAlpha.snapTo(0f)
        }
    }

    LaunchedEffect(state, vibrantColor) {
        when (state) {
            is AsyncImagePainter.State.Success -> {
                currentPainter = state.painter
                activeVibrantColor = vibrantColor
                if (lastLoadedMediaId != mediaId) {
                    lastLoadedMediaId = mediaId
                    onCoverLoaded()
                    launch {
                        newArtAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 240)
                        )
                    }
                } else {
                    newArtAlpha.snapTo(1f)
                }
            }
            is AsyncImagePainter.State.Error -> {
                currentPainter = null
                previousPainter = null
                activeVibrantColor = Color.Transparent
            }
            else -> {}
        }
    }

    val shape24 = remember { RoundedCornerShape(24.dp) }

    Box(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                translationX = offsetX.value
                val isDraggingCover = abs(offsetX.value) > 1f
                shadowElevation = if (isDraggingCover) 6.dp.toPx() else 14.dp.toPx()
                shape = shape24
                clip = false
                ambientShadowColor = shadowColor.copy(alpha = 0.5f)
                spotShadowColor = shadowColor.copy(alpha = 0.4f)
            }
            .then(
                if (gestureEnabled) {
                    Modifier.pointerInput(key1 = onNext, key2 = onPrevious) {
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDragX = 0f },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (abs(accumulatedDragX) > snapThresholdPx) {
                                        haptics.gestureThresholdActivate()
                                        if (accumulatedDragX > 0) onPrevious() else onNext()
                                    }
                                    accumulatedDragX = 0f
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    accumulatedDragX = 0f
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    accumulatedDragX += dragAmount
                                    val dragFraction = (abs(accumulatedDragX) / (size.width.toFloat() * 1.5f)).coerceIn(0f, 1f)
                                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                                    val finalOffset = if (accumulatedDragX > 0) tensionOffset else -tensionOffset
                                    offsetX.snapTo(finalOffset)
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(shape24)
            .background(surfaceColor),
        contentAlignment = Alignment.Center
    ) {
        val isCanvasActive = canvasHostEnabled && canvasState.isCanvasEnabled && canvasState.artwork != null
        val placeholder = painterResource(id = placeholderResId)

        val bgPainter = previousPainter ?: currentPainter ?: placeholder
        Image(
            painter = bgPainter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (currentPainter != null && currentPainter != previousPainter) {
            Image(
                painter = currentPainter!!,
                contentDescription = stringResource(R.string.album_art_large),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = newArtAlpha.value
                    },
                contentScale = ContentScale.Crop,
            )
        }

        // Canvas
        if (isCanvasActive) {
            CanvasArtworkPlayer(
                primaryUrl = canvasState.primaryUrl,
                fallbackUrl = canvasState.fallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
