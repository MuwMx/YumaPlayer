package moe.rukamori.archivetune.ui.component

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.core.common.math.lerp
import moe.rukamori.archivetune.core.common.math.lerp3
import moe.rukamori.archivetune.ui.utils.resize


/**
 * Единая конфигурация для всех типов экранов приложения
 */
enum class HeaderType(
    val heightRatio: Float,
    val showBottomGradient: Boolean
) {
    ARTIST(heightRatio = 1.45f, showBottomGradient = true),
    ALBUM(heightRatio = 1.35f, showBottomGradient = true),
    PLAYLIST(heightRatio = 1.35f, showBottomGradient = true),
    SPOTIFY(heightRatio = 1.0f, showBottomGradient = false)
}

@Composable
fun YumaMorphingHeader(
    imageUrl: String?,
    collapseFraction: Float,
    type: HeaderType,
    modifier: Modifier = Modifier,
    disableBlur: Boolean = false,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val density = LocalDensity.current
    val fraction = collapseFraction.coerceIn(0f, 1f)

    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val screenWidthDp = with(density) { screenWidthPx.toDp() }

    val expandedHeightDp = screenWidthDp * type.heightRatio
    val surfaceColor = MaterialTheme.colorScheme.background

    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val targetCameraY = if (statusBarHeightPx > 0f) statusBarHeightPx / 2f else with(density) { 24.dp.toPx() }
    val containerCenterY = screenWidthPx / 2f
    val endOffset = targetCameraY - containerCenterY

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(expandedHeightDp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)

                    val currentScale = lerp3(start = 1f, mid = 0.44f, end = 0f, fraction = fraction)
                    scaleX = currentScale
                    scaleY = currentScale

                    translationY = lerp3(start = 0f, mid = 0f, end = endOffset, fraction = fraction)

                    val cornerPercent = lerp3(start = 0f, mid = 32f, end = 50f, fraction = fraction).toInt()
                    shape = RoundedCornerShape(percent = cornerPercent)
                    clip = true

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (!disableBlur && fraction > 0.70f) {
                            val blurProgress = (fraction - 0.70f) / 0.30f
                            val blurRadius = lerp(0.1f, 40f, blurProgress)
                            renderEffect = BlurEffectCache.get(blurRadius)
                        } else {
                            renderEffect = null
                        }
                    }
                }
                .height(
                    lerp(expandedHeightDp.value, screenWidthDp.value, (fraction * 2f).coerceIn(0f, 1f)).dp
                )
        ) {
            AsyncImage(
                model = if (type == HeaderType.ARTIST) {
                    imageUrl?.resize(1080, 1080).orEmpty()
                } else {
                    imageUrl.orEmpty()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )

            if (type.showBottomGradient) {
                val immersiveAlpha = (1f - fraction * 2f).coerceIn(0f, 1f)
                if (!disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && immersiveAlpha > 0.01f) {
                    AsyncImage(
                        model = if (type == HeaderType.ARTIST) {
                            imageUrl?.resize(1080, 1080).orEmpty()
                        } else {
                            imageUrl.orEmpty()
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = immersiveAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                                renderEffect = BlurEffectCache.get(28f)
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.55f to Color.Transparent,
                                        0.85f to Color.Black,
                                        1.0f to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(138.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = immersiveAlpha }
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.55f to surfaceColor.copy(alpha = 0.7f),
                                1.0f to surfaceColor
                            )
                        )
                )
            }

            overlayContent?.invoke(this)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (fraction < 0.70f) 0f else lerp(0f, 1f, (fraction - 0.70f) / 0.30f)
                    }
                    .background(Color.Black)
            )
        }
    }
}



/**
 * Единый расчет сжатия шапки с быстрой и отзывчивой динамикой (как на экране артиста).
 */
@Composable
fun rememberCollapseFraction(
    lazyListState: LazyListState,
    maxScrollDistance: Dp = 420.dp // Дистанция быстрого схлопывания с артиста
): State<Float> {
    val density = LocalDensity.current
    return remember(lazyListState, maxScrollDistance) {
        derivedStateOf {
            val maxScrollPx = with(density) { maxScrollDistance.toPx() }
            if (maxScrollPx <= 0f) {
                1f
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / maxScrollPx).coerceIn(0f, 1f)
            }
        }
    }
}

private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val BREAKAWAY_THRESHOLD = 0.33f
private const val RUBBER_BAND_FACTOR = 0.5f

@Stable
class ArtistHeaderMorphState(
    private val lazyListState: LazyListState,
    private val maxPullPx: Float,
    private val extraExpandPx: Float,
    private val collapseDistancePx: Float,
    private val animationsDisabled: Boolean,
    private val view: android.view.View,
    private val coroutineScope: CoroutineScope,
) {
    private val pullOffset = Animatable(0f)
    private val expandAnimator = Animatable(0f)
    var isPulledDown by mutableStateOf(false)
        private set

    private val scrollCollapse by derivedStateOf {
        if (collapseDistancePx <= 0f) {
            1f
        } else if (lazyListState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (lazyListState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    private val immersiveValue by derivedStateOf {
        val pullProgress = if (maxPullPx > 0f) {
            (pullOffset.value / maxPullPx).coerceIn(0f, BREAKAWAY_THRESHOLD)
        } else {
            0f
        }
        maxOf(
            expandAnimator.value,
            if (isPulledDown) BREAKAWAY_THRESHOLD else pullProgress,
        )
    }

    val collapseFraction: Float by derivedStateOf {
        if (immersiveValue > 0f) {
            0.5f * (1f - immersiveValue)
        } else {
            0.5f + 0.5f * scrollCollapse
        }
    }

    val contentTranslationY: Float = 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val isAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0

            if (available.y > 0f) {
                if (!isAtTop || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                if (!isPulledDown && maxPullPx > 0f) {
                    val delta = available.y * RUBBER_BAND_FACTOR
                    val nextPull = (pullOffset.value + delta).coerceIn(0f, maxPullPx)
                    val expandProgress = (nextPull / maxPullPx).coerceIn(0f, 1f)
                    if (expandProgress >= BREAKAWAY_THRESHOLD) {
                        isPulledDown = true
                        runCatching {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        coroutineScope.launch {
                            pullOffset.snapTo(nextPull)
                            expandAnimator.snapTo(expandProgress)
                            expandAnimator.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = if (animationsDisabled) 0 else 250,
                                    easing = EaseOutQuint,
                                ),
                            )
                        }
                    } else {
                        coroutineScope.launch {
                            pullOffset.snapTo(nextPull)
                        }
                    }
                    return Offset(0f, available.y)
                }
                return if (isPulledDown) Offset(0f, available.y) else Offset.Zero
            }

            if (available.y < 0f) {
                if (!isPulledDown && pullOffset.value == 0f) {
                    return Offset.Zero
                }
                if (isPulledDown) {
                    val nextPull = (pullOffset.value + available.y).coerceAtLeast(0f)
                    val expandProgress = if (maxPullPx > 0f) (nextPull / maxPullPx).coerceIn(0f, 1f) else 0f
                    if (expandProgress < BREAKAWAY_THRESHOLD) {
                        collapseImmersive()
                    } else {
                        coroutineScope.launch {
                            pullOffset.snapTo(nextPull)
                        }
                    }
                    return Offset(0f, available.y)
                } else {
                    val currentPull = pullOffset.value
                    val nextPull = (currentPull + available.y).coerceAtLeast(0f)
                    val consumedY = nextPull - currentPull
                    coroutineScope.launch {
                        pullOffset.snapTo(nextPull)
                    }
                    return Offset(0f, consumedY)
                }
            }

            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isPulledDown) {
                if (available.y < -500f) {
                    collapseImmersive()
                    return Velocity(0f, available.y)
                }
                if (expandAnimator.value < 1f) {
                    expandAnimator.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = if (animationsDisabled) 0 else 250,
                            easing = EaseOutQuint,
                        ),
                    )
                }
                return Velocity.Zero
            }

            if (pullOffset.value > 0f) {
                pullOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = if (animationsDisabled) 0 else 250,
                        easing = EaseOutQuint,
                    ),
                )
                return if (available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
            }

            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!isPulledDown && pullOffset.value > 0f) {
                pullOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = if (animationsDisabled) 0 else 250,
                        easing = EaseOutQuint,
                    ),
                )
            } else if (isPulledDown && expandAnimator.value < 1f && !expandAnimator.isRunning) {
                expandAnimator.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = if (animationsDisabled) 0 else 250,
                        easing = EaseOutQuint,
                    ),
                )
            }
            return Velocity.Zero
        }
    }

    private fun collapseImmersive() {
        isPulledDown = false
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        val spec = tween<Float>(
            durationMillis = if (animationsDisabled) 0 else 250,
            easing = FastOutSlowInEasing,
        )
        coroutineScope.launch {
            expandAnimator.animateTo(0f, spec)
        }
        coroutineScope.launch {
            pullOffset.animateTo(0f, spec)
        }
    }
}

@Composable
fun rememberArtistHeaderMorphState(
    lazyListState: LazyListState,
    animationsDisabled: Boolean = false,
): ArtistHeaderMorphState {
    val density = LocalDensity.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val extraExpandPx = screenWidthPx * HeaderType.ARTIST.heightRatio * 0.4f
    val maxPullPx = with(density) { 160.dp.toPx() }
    val collapseDistancePx = with(density) { 240.dp.toPx() }

    return remember(lazyListState, maxPullPx, extraExpandPx, collapseDistancePx, animationsDisabled, view) {
        ArtistHeaderMorphState(
            lazyListState = lazyListState,
            maxPullPx = maxPullPx,
            extraExpandPx = extraExpandPx,
            collapseDistancePx = collapseDistancePx,
            animationsDisabled = animationsDisabled,
            view = view,
            coroutineScope = coroutineScope,
        )
    }
}