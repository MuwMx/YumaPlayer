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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.placeholder
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
) {
    val vinylRotationDeg: () -> Float = { vinylRotation.value }

    var containerCoordinates: LayoutCoordinates? = null
    var miniRect by mutableStateOf(Rect.Zero)
        private set
    var fullCardRect by mutableStateOf(Rect.Zero)
        private set
    var fullImmersiveRect by mutableStateOf(Rect.Zero)
        private set

    fun targetRect(): Rect = if (isImmersive()) fullImmersiveRect else fullCardRect

    fun hasValidAnchors(): Boolean {
        val target = targetRect()
        return !miniRect.isEmpty && !target.isEmpty
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

    fun updateFullCardRect(rootCoords: LayoutCoordinates?, coords: LayoutCoordinates) {
        if (rootCoords == null || !rootCoords.isAttached || !coords.isAttached) return
        val topLeft = rootCoords.localPositionOf(coords, Offset.Zero)
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

    fun updateFullImmersiveRect(rootCoords: LayoutCoordinates?, coords: LayoutCoordinates) {
        if (rootCoords == null || !rootCoords.isAttached || !coords.isAttached) return
        val topLeft = rootCoords.localPositionOf(coords, Offset.Zero)
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
): CoverMorphState {
    val vinylRotation = remember { Animatable(0f) }
    val morphState = remember(expansionFraction, isImmersive, vinylRotation) {
        CoverMorphState(
            expansionFraction = expansionFraction,
            isImmersive = isImmersive,
            vinylRotation = vinylRotation,
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
    val request = remember(cleanUrl, state.placeholderResId) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .placeholder(state.placeholderResId)
            .error(state.placeholderResId)
            .apply {
                if (cleanUrl != null) {
                    memoryCacheKey(cleanUrl)
                    diskCacheKey(cleanUrl)
                }
            }
            .build()
    }
    val painter = if (cleanUrl != null) {
        rememberAsyncImagePainter(model = request)
    } else {
        painterResource(id = state.placeholderResId)
    }

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
                    val travel = 1f - (1f - p) * (1f - p)
                    val start = morphState.miniRect
                    val end = morphState.targetRect()

                    val cx = lerp(start.center.x, end.center.x, travel)
                    val cy = lerp(start.center.y, end.center.y, travel)
                    val w = lerp(start.width, end.width, travel)
                    val h = lerp(start.height, end.height, travel)

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
                    val travel = 1f - (1f - p) * (1f - p)
                    val immersive = morphState.isImmersive()

                    val start = morphState.miniRect
                    val startRadius = min(start.width, start.height) / 2f
                    val midTargetRadius = if (immersive) midImmersiveRadiusPx else cardCornerRadiusPx
                    val targetRadius = if (immersive) 0f else cardCornerRadiusPx
                    val midRadius = lerp(startRadius, midTargetRadius, travel)
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
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = if (state.isImmersiveEnabled) Alignment.TopCenter else Alignment.Center,
            )
        }
    }
}
