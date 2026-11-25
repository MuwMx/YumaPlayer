package moe.rukamori.archivetune.ui.component

import android.os.Build
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.core.common.math.lerp
import moe.rukamori.archivetune.core.common.math.lerp3
import moe.rukamori.archivetune.ui.utils.resize


/**
 * Единая конфигурация для всех типов экранов приложения
 */
enum class HeaderType(
    val heightRatio: Float,
    val showBottomGradient: Boolean,
    val midScale: Float = 0.44f,
) {
    ARTIST(heightRatio = 1.45f, showBottomGradient = true, midScale = 0.64f),
    ALBUM(heightRatio = 1.35f, showBottomGradient = true),
    PLAYLIST(heightRatio = 1.35f, showBottomGradient = true),
    SPOTIFY(heightRatio = 1.0f, showBottomGradient = false),
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

                    val currentScale = lerp3(start = 1f, mid = type.midScale, end = 0f, fraction = fraction)
                    scaleX = currentScale
                    scaleY = currentScale

                    translationY = lerp3(start = 0f, mid = 0f, end = endOffset, fraction = fraction)

                    val cornerPercent = lerp3(start = 0f, mid = 18f, end = 50f, fraction = fraction).toInt()
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
                    imageUrl?.resize(1080, 1080, smartCrop = false).orEmpty()
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
                        .fillMaxWidth()
                        .height(138.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = (1f - fraction * 2f).coerceIn(0f, 1f)
                            if (!disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = BlurEffectCache.get(20f)
                            }
                        }
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to surfaceColor.copy(alpha = 0.75f),
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


@Composable
fun rememberArtistCollapseFraction(
    lazyListState: LazyListState,
    collapseDistance: Dp = 240.dp,
): State<Float> {
    val density = LocalDensity.current
    return remember(lazyListState, collapseDistance) {
        derivedStateOf {
            val maxScrollPx = with(density) { collapseDistance.toPx() }
            val scrollProgress = when {
                maxScrollPx <= 0f -> 1f
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> (lazyListState.firstVisibleItemScrollOffset / maxScrollPx).coerceIn(0f, 1f)
            }
            0.5f + 0.5f * scrollProgress
        }
    }
}