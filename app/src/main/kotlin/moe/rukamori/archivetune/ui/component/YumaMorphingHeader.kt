package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.core.common.math.lerp
import moe.rukamori.archivetune.core.common.math.lerp3
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.ui.utils.resize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp


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
                        if (fraction > 0.70f) {
                            val blurProgress = (fraction - 0.70f) / 0.30f
                            val blurRadius = lerp(0.1f, 40f, blurProgress)
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - fraction * 2f).coerceIn(0f, 1f)
                        }
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.60f to Color.Transparent,
                                0.88f to surfaceColor.copy(alpha = 0.75f),
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