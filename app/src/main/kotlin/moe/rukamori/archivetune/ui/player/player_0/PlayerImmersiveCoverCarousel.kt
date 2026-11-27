package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.abs
import moe.rukamori.archivetune.ui.component.LikeBurstState
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState

internal const val CoverParallax = 0.3f

@Composable
internal fun PlayerImmersiveCoverCarousel(
    carousel: SongCarousel,
    state: PlayerUiState,
    canvasState: PlayerCanvasState,
    immersiveTransitionAlpha: Float,
    queueArtworkFade: Float,
    isCanvasActive: Boolean,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    backgroundRootCoordinates: LayoutCoordinates? = null,
    likeBurst: LikeBurstState? = null,
    onAction: (PlayerAction) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverMorphState = LocalCoverMorphState.current

    var isCanvasReady by remember(state.trackUrl) { mutableStateOf(false) }
    var artworkWidthPx by remember { mutableFloatStateOf(0f) }

    val isImmersiveSwipeEnabled = state.isImmersiveEnabled &&
        !state.isLyricsVisible &&
        lyricsFractionProvider() < 0.05f &&
        queueFractionProvider() < 0.05f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .onSizeChanged { artworkWidthPx = it.width.toFloat() }
    ) {
        for (slot in carousel.slots) {
            key(slot.uid) {
                val slotUrl = slot.artworkUri?.trim()?.takeIf(String::isNotBlank)
                var holdOverUrl by remember { mutableStateOf(slotUrl) }
                LaunchedEffect(slotUrl) {
                    if (slotUrl != null) {
                        holdOverUrl = slotUrl
                    }
                }
                val slotRequest = remember(slotUrl, holdOverUrl) {
                    ImageRequest.Builder(context)
                        .data(slotUrl)
                        .apply {
                            if (slotUrl != null) {
                                memoryCacheKey(slotUrl)
                                diskCacheKey(slotUrl)
                            }
                            if (holdOverUrl != null && holdOverUrl != slotUrl) {
                                placeholderMemoryCacheKey(holdOverUrl)
                            }
                        }
                        .build()
                }
                val slotPainter = rememberAsyncImagePainter(model = slotRequest)
                val isCurrent = slot.step == carousel.step
                if (!(isCurrent && isCanvasActive && isCanvasReady)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val o = carousel.offsetOf(slot)
                                val hideForMorph = isCurrent && state.isImmersiveEnabled && (coverMorphState?.isMorphActive() == true)
                                alpha = if (abs(o) >= 1f || hideForMorph) 0f else immersiveTransitionAlpha * queueArtworkFade
                                translationX = o * size.width
                                clip = true
                            }
                            .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade)
                    ) {
                        Image(
                            painter = slotPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val o = carousel.offsetOf(slot)
                                    translationX = -o * size.width * CoverParallax
                                },
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter
                        )
                    }
                }
            }
        }

        if (isCanvasActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val o = carousel.playingOffset
                        val hideForMorph = state.isImmersiveEnabled && (coverMorphState?.isMorphActive() == true)
                        alpha = if (abs(o) >= 1f || hideForMorph) 0f else (1f - abs(o)).coerceIn(0f, 1f)
                        translationX = o * size.width
                        clip = true
                    }
                    .artworkBottomFade(immersiveTransitionAlpha * queueArtworkFade)
            ) {
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
                            val o = carousel.playingOffset
                            translationX = -o * size.width * CoverParallax
                        }
                )
            }
        }

        var rootBounds by remember { mutableStateOf(Rect.Zero) }
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .carouselDragGesture(
                    carousel = carousel,
                    enabled = isImmersiveSwipeEnabled,
                    spacingPxProvider = {
                        artworkWidthPx.takeIf { it > 0f }
                            ?: with(density) { 360.dp.toPx() }
                    },
                    flingThresholdPx = with(density) { 400.dp.toPx() },
                    onNext = { carousel.swipeSkip?.invoke(true) },
                    onPrevious = { carousel.swipeSkip?.invoke(false) },
                )
                .then(
                    if (isImmersiveSwipeEnabled && likeBurst != null) {
                        Modifier
                            .onGloballyPositioned { rootBounds = it.boundsInRoot() }
                            .pointerInput(likeBurst) {
                                detectTapGestures(
                                    onDoubleTap = { pos ->
                                        likeBurst.likeFromDoubleTap(rootBounds.topLeft + pos, state.isLiked) {
                                            onAction(PlayerAction.Like)
                                        }
                                    }
                                )
                            }
                    } else Modifier
                )
        )
    }
}
