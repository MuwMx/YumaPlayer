/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import moe.rukamori.archivetune.ui.state.PlayerUiState

internal val LocalCoverMorphState = staticCompositionLocalOf<CoverMorphState?> { null }

@Immutable
internal data class MorphEndpointsPx(
    val miniLeftPx: Float,
    val miniTopInsetPx: Float,
    val miniSizePx: Float,
    val immersiveRect: Rect,
    val cardLeftPx: Float,
    val cardDefaultTopPx: Float,
    val cardWidthPx: Float,
) {
    fun miniRect(collapsedY: Float): Rect {
        val top = collapsedY + miniTopInsetPx
        return Rect(
            left = miniLeftPx,
            top = top,
            right = miniLeftPx + miniSizePx,
            bottom = top + miniSizePx,
        )
    }

    fun cardRect(cardTopPxOverride: Float, scale: Float): Rect {
        val top = if (cardTopPxOverride > 0f) cardTopPxOverride else cardDefaultTopPx
        val scaledW = cardWidthPx * scale
        val left = cardLeftPx + cardWidthPx * (1f - scale) / 2f
        return Rect(
            left = left,
            top = top,
            right = left + scaledW,
            bottom = top + scaledW,
        )
    }
}

@Stable
internal class CoverMorphState(
    val expansionFraction: () -> Float,
    val isImmersive: () -> Boolean,
    val vinylRotation: Animatable<Float, AnimationVector1D>,
    val collapsedYProvider: () -> Float,
    endpoints: MorphEndpointsPx,
) {
    val vinylRotationDeg: () -> Float = { vinylRotation.value }

    var endpoints by mutableStateOf(endpoints)
    var cardTopPx by mutableFloatStateOf(0f)
        private set
    var fullCardScale by mutableFloatStateOf(1f)
        private set

    fun updateFullCardScale(scale: Float) {
        if (fullCardScale != scale) {
            fullCardScale = scale
        }
    }

    fun updateCardTopPx(topPx: Float) {
        if (topPx > 0f && cardTopPx != topPx) {
            cardTopPx = topPx
        }
    }

    fun miniRect(): Rect = endpoints.miniRect(collapsedYProvider())

    fun targetRect(): Rect {
        return if (isImmersive()) {
            endpoints.immersiveRect
        } else {
            endpoints.cardRect(cardTopPxOverride = cardTopPx, scale = fullCardScale)
        }
    }

    fun hasValidAnchors(): Boolean = true

    fun isMorphActive(): Boolean {
        val p = expansionFraction()
        return p in 0.001f..0.999f && hasValidAnchors()
    }
}

@Composable
internal fun rememberCoverMorphState(
    isPlaying: Boolean,
    isReady: Boolean,
    isImmersive: () -> Boolean,
    expansionFraction: () -> Float,
    collapsedYProvider: () -> Float,
    collapsedHorizontalInsetPx: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    statusBarTopPx: Float,
): CoverMorphState {
    val density = LocalDensity.current
    val endpoints = remember(
        screenWidthPx,
        screenHeightPx,
        collapsedHorizontalInsetPx,
        statusBarTopPx,
        density,
    ) {
        val safeW = screenWidthPx.coerceAtLeast(1f)
        val miniSizePx = with(density) { 40.dp.toPx() }
        val miniLeftPx = collapsedHorizontalInsetPx + with(density) { 16.dp.toPx() }
        val miniTopInsetPx = with(density) { 12.dp.toPx() }
        val cardHorizontalPaddingPx = with(density) { 22.dp.toPx() }
        val cardWidthPx = (safeW - cardHorizontalPaddingPx * 2f).coerceAtLeast(1f)
        val cardDefaultTopPx = statusBarTopPx + with(density) { 72.dp.toPx() }
        MorphEndpointsPx(
            miniLeftPx = miniLeftPx,
            miniTopInsetPx = miniTopInsetPx,
            miniSizePx = miniSizePx,
            immersiveRect = Rect(0f, 0f, safeW, safeW / 0.75f),
            cardLeftPx = cardHorizontalPaddingPx,
            cardDefaultTopPx = cardDefaultTopPx,
            cardWidthPx = cardWidthPx,
        )
    }
    val vinylRotation = remember { Animatable(0f) }
    val morphState = remember(
        expansionFraction,
        isImmersive,
        vinylRotation,
        collapsedYProvider,
    ) {
        CoverMorphState(
            expansionFraction = expansionFraction,
            isImmersive = isImmersive,
            vinylRotation = vinylRotation,
            collapsedYProvider = collapsedYProvider,
            endpoints = endpoints,
        )
    }
    morphState.endpoints = endpoints

    val isMiniSpinning by remember(isPlaying, isReady) {
        derivedStateOf {
            isPlaying && isReady && expansionFraction() <= 0.01f
        }
    }

    LaunchedEffect(isMiniSpinning) {
        if (isMiniSpinning) {
            while (true) {
                val currentDegree = vinylRotation.value % 360f
                vinylRotation.snapTo(currentDegree)
                vinylRotation.animateTo(
                    targetValue = currentDegree + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
                )
            }
        }
    }

    return morphState
}

private class MorphRoundedRectShape : Shape {
    var cornerRadiusPx: Float = 0f

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val maxR = min(size.width, size.height) / 2f
        val r = cornerRadiusPx.coerceIn(0f, maxR)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(r, r),
            )
        )
    }
}

@Composable
internal fun CoverMorphLayer(
    state: PlayerUiState,
    morphState: CoverMorphState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardCornerRadiusPx = remember(density) { with(density) { 24.dp.toPx() } }
    val midImmersiveRadiusPx = remember(density) { with(density) { 28.dp.toPx() } }
    val morphShape = remember { MorphRoundedRectShape() }

    val cleanUrl = state.coverUrl.trim().takeIf(String::isNotBlank)
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
    val asyncPainter = rememberAsyncImagePainter(model = request)
    val painterState by asyncPainter.state.collectAsState()
    var currentPainter by remember { mutableStateOf<Painter?>(null) }
    var previousPainter by remember { mutableStateOf<Painter?>(null) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cleanUrl) {
        if (cleanUrl == null) {
            currentPainter = null
            previousPainter = null
            lastLoadedUrl = null
        } else if (currentPainter != null && lastLoadedUrl != cleanUrl) {
            previousPainter = currentPainter
            currentPainter = null
        }
    }

    LaunchedEffect(painterState) {
        when (painterState) {
            is AsyncImagePainter.State.Success -> {
                currentPainter = (painterState as AsyncImagePainter.State.Success).painter
                previousPainter = null
                lastLoadedUrl = cleanUrl
            }
            is AsyncImagePainter.State.Error -> {
                currentPainter = null
                previousPainter = null
            }
            else -> {}
        }
    }

    val placeholder = painterResource(id = state.placeholderResId)
    val displayPainter = currentPainter ?: previousPainter ?: placeholder

    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                if (!morphState.isMorphActive()) {
                    val placeable = measurable.measure(Constraints.fixed(0, 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                } else {
                    val p = morphState.expansionFraction().coerceIn(0f, 1f)
                    val immersive = morphState.isImmersive()
                    val t = if (immersive) p else p * p * (3f - 2f * p)
                    val start = morphState.miniRect()
                    val end = morphState.targetRect()

                    val cx = lerp(start.center.x, end.center.x, t)
                    val cy = lerp(start.center.y, end.center.y, t)
                    val w = lerp(start.width, end.width, t)
                    val h = lerp(start.height, end.height, t)

                    val wInt = w.roundToInt().coerceAtLeast(1)
                    val hInt = h.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(wInt, hInt))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (cx - w / 2f).roundToInt(),
                            y = (cy - h / 2f).roundToInt(),
                        )
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!morphState.isMorphActive()) {
                        alpha = 0f
                        compositingStrategy = CompositingStrategy.Auto
                        return@graphicsLayer
                    }
                    alpha = 1f
                    val p = morphState.expansionFraction().coerceIn(0f, 1f)
                    val immersive = morphState.isImmersive()
                    val t = if (immersive) p else p * p * (3f - 2f * p)

                    val start = morphState.miniRect()
                    val startRadius = min(start.width, start.height) / 2f
                    val midTargetRadius = if (immersive) midImmersiveRadiusPx else cardCornerRadiusPx
                    val targetRadius = if (immersive) 0f else cardCornerRadiusPx
                    val midRadius = lerp(startRadius, midTargetRadius, t)
                    val settleT = ((p - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    morphShape.cornerRadiusPx = lerp(midRadius, targetRadius, settleT)

                    shape = morphShape
                    clip = true

                    val angle = morphState.vinylRotationDeg()
                    val norm = ((angle % 360f) + 540f) % 360f - 180f
                    val alignT = (p / 0.30f).coerceIn(0f, 1f)
                    val easedAlign = FastOutSlowInEasing.transform(alignT)
                    rotationZ = norm * (1f - easedAlign)

                    val sideFade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                    val bottomFade = if (immersive) p else 0f
                    compositingStrategy = if (sideFade > 0.001f || bottomFade > 0.001f) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawWithContent {
                    if (!morphState.isMorphActive()) return@drawWithContent
                    drawContent()

                    val p = morphState.expansionFraction().coerceIn(0f, 1f)
                    val sideFade = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                    if (sideFade > 0.001f) {
                        val edgeAlpha = sideFade * 0.35f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Black.copy(alpha = edgeAlpha),
                                0.12f to Color.Transparent,
                                0.88f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = edgeAlpha),
                                startX = 0f,
                                endX = size.width,
                            ),
                            blendMode = BlendMode.DstOut,
                        )
                    }

                    if (morphState.isImmersive() && p > 0.001f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.75f to Color.Black.copy(alpha = 0.6f * p),
                                1.0f to Color.Black.copy(alpha = p),
                                startY = 0f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                }
        ) {
            Image(
                painter = displayPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = if (state.isImmersiveEnabled) Alignment.TopCenter else Alignment.Center,
            )
        }
    }
}
