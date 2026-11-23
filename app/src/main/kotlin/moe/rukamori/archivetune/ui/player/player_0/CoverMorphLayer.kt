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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.layout.LayoutCoordinates
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

@Stable
internal class CoverMorphState(
    val expansionFraction: () -> Float,
    val isImmersive: () -> Boolean,
    val vinylRotation: Animatable<Float, AnimationVector1D>,
    val collapsedYProvider: () -> Float,
    val collapsedHorizontalInsetPx: Float,
    val miniSizePx: Float,
    val miniLeftInsetPx: Float,
    val miniTopInsetPx: Float,
) {
    val vinylRotationDeg: () -> Float = { vinylRotation.value }

    var containerCoordinates: LayoutCoordinates? = null
    var miniRect by mutableStateOf(Rect.Zero)
        private set
    var fullCardRect by mutableStateOf(Rect.Zero)
        private set
    var fullImmersiveRect by mutableStateOf(Rect.Zero)
        private set
    var fullCardScale by mutableFloatStateOf(1f)
        private set

    fun updateFullCardScale(scale: Float) {
        if (fullCardScale != scale) {
            fullCardScale = scale
        }
    }

    fun effectiveMiniRect(): Rect {
        if (miniRect != Rect.Zero) return miniRect
        val left = collapsedHorizontalInsetPx + miniLeftInsetPx
        val top = collapsedYProvider() + miniTopInsetPx
        return Rect(
            left = left,
            top = top,
            right = left + miniSizePx,
            bottom = top + miniSizePx,
        )
    }

    fun targetRect(): Rect {
        if (isImmersive()) return fullImmersiveRect
        val raw = fullCardRect
        if (raw.isEmpty) return raw
        val s = fullCardScale
        if (s == 1f) return raw
        val w = raw.width
        val h = raw.height
        val scaledW = w * s
        val left = raw.left + w * (1f - s) / 2f
        val top = raw.top
        return Rect(
            left = left,
            top = top,
            right = left + scaledW,
            bottom = top + h * s,
        )
    }

    fun hasValidAnchors(): Boolean {
        val target = targetRect()
        return !effectiveMiniRect().isEmpty && !target.isEmpty
    }

    fun isMorphActive(): Boolean {
        val p = expansionFraction()
        return p in 0.001f..0.999f && hasValidAnchors()
    }

    fun updateMiniRect(coords: LayoutCoordinates) {
        if (expansionFraction() > 0.01f || !coords.isAttached) return
        val container = containerCoordinates ?: return
        if (!container.isAttached) return
        val topLeft = container.localPositionOf(coords, Offset.Zero)
        val size = coords.size
        if (size.width <= 0 || size.height <= 0) return
        val next = Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + size.width,
            bottom = topLeft.y + size.height,
        )
        if (next != miniRect) {
            miniRect = next
        }
    }

    fun updateFullCardRect(coords: LayoutCoordinates) {
        if (expansionFraction() < 0.99f || !coords.isAttached) return
        val container = containerCoordinates ?: return
        if (!container.isAttached) return
        val topLeft = container.localPositionOf(coords, Offset.Zero)
        val size = coords.size
        if (size.width <= 0 || size.height <= 0) return
        val next = Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + size.width,
            bottom = topLeft.y + size.height,
        )
        if (next != fullCardRect) {
            fullCardRect = next
        }
    }

    fun updateFullImmersiveRect(coords: LayoutCoordinates) {
        if (expansionFraction() < 0.99f || !coords.isAttached) return
        val container = containerCoordinates ?: return
        if (!container.isAttached) return
        val topLeft = container.localPositionOf(coords, Offset.Zero)
        val size = coords.size
        if (size.width <= 0 || size.height <= 0) return
        val next = Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + size.width,
            bottom = topLeft.y + size.height,
        )
        if (next != fullImmersiveRect) {
            fullImmersiveRect = next
        }
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
): CoverMorphState {
    val density = LocalDensity.current
    val miniSizePx = remember(density) { with(density) { 40.dp.toPx() } }
    val miniLeftInsetPx = remember(density) { with(density) { 16.dp.toPx() } }
    val miniTopInsetPx = remember(density) { with(density) { 12.dp.toPx() } }
    val vinylRotation = remember { Animatable(0f) }
    val morphState = remember(
        expansionFraction,
        isImmersive,
        vinylRotation,
        collapsedYProvider,
        collapsedHorizontalInsetPx,
        miniSizePx,
        miniLeftInsetPx,
        miniTopInsetPx,
    ) {
        CoverMorphState(
            expansionFraction = expansionFraction,
            isImmersive = isImmersive,
            vinylRotation = vinylRotation,
            collapsedYProvider = collapsedYProvider,
            collapsedHorizontalInsetPx = collapsedHorizontalInsetPx,
            miniSizePx = miniSizePx,
            miniLeftInsetPx = miniLeftInsetPx,
            miniTopInsetPx = miniTopInsetPx,
        )
    }

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
                    val t = p * p * (3f - 2f * p)
                    val start = morphState.effectiveMiniRect()
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
                    val t = p * p * (3f - 2f * p)
                    val immersive = morphState.isImmersive()

                    val start = morphState.effectiveMiniRect()
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
