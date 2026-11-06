/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.graphics.Shader
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.heroScrollCoverMotion

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroMediaCard(
    item: HeroItem,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    isThisPlaying: Boolean,
    edgeBlur: Boolean,
    blurFactor: Float = 1f,
    onPrimaryClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl, context) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .crossfade(true)
            .build()
    }
    val mediaLayer = rememberGraphicsLayer()
    val scrollProgress = LocalHeroScrollProgress.current
    val animationsDisabled = LocalAnimationsDisabled.current

    HeroPageWipe(
        pageOffset = pageOffset,
        curve = wipeCurve,
        blur = edgeBlur,
        blurFactor = blurFactor,
        modifier = modifier.combinedClickable(
            onClick = onPrimaryClick,
            onLongClick = onLongClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .heroPageMediaDrift(pageOffset)
                .heroScrollCoverMotion(
                    scrollProgress = scrollProgress,
                    animationsDisabled = animationsDisabled,
                    edgeBlur = edgeBlur,
                    blurFactor = blurFactor,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (edgeBlur) {
                            Modifier.drawWithContent {
                                mediaLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(mediaLayer)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (edgeBlur) {
                HeroBlurBand(
                    mediaLayer = mediaLayer,
                    atTop = true,
                    heightFraction = HeroCarouselTokens.TopBlurHeightFraction,
                    radius = HeroCarouselTokens.TopBlurRadius * blurFactor,
                )
                HeroBlurBand(
                    mediaLayer = mediaLayer,
                    atTop = false,
                    heightFraction = HeroCarouselTokens.BottomBlurHeightFraction,
                    radius = HeroCarouselTokens.BottomBlurRadius * blurFactor,
                )
            }

            HeroScrims()
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .heroPageTextDrift(pageOffset)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            ) {
                Text(
                    text = if (isThisPlaying) {
                        stringResource(R.string.now_playing)
                    } else {
                        stringResource(R.string.quick_picks)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = HeroCarouselTokens.ArtOverlayTextSecondary,
                )

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    color = HeroCarouselTokens.ArtOverlayTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )

                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroCarouselTokens.ArtOverlayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = HeroCarouselTokens.MinTouchTarget)
                        .clip(CircleShape)
                        .background(HeroCarouselTokens.ArtOverlayButtonSurface)
                        .combinedClickable(onClick = onPrimaryClick, onLongClick = onLongClick)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isThisPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = HeroCarouselTokens.ArtOverlayButtonContent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isThisPlaying) {
                            stringResource(R.string.widget_pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = HeroCarouselTokens.ArtOverlayButtonContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HeroBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(
                0f to Color.Black,
                0.35f to Color.Black.copy(alpha = 0.85f),
                1f to Color.Transparent,
            )
        } else {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = 0.85f),
                1f to Color.Black,
            )
        }
    }

    HeroDownscaledBlur(
        radius = { HeroBlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
    }
}

@Composable
private fun BoxScope.HeroScrims() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val topEnd = HeroCarouselTokens.ScrimTopEnd
                val bottomStart = HeroCarouselTokens.ScrimBottomStart
                val bottomEnd = HeroCarouselTokens.ScrimBottomEnd
                val scrim = Brush.verticalGradient(
                    0f to heroScrimBlack(0.86f),
                    0.475f * topEnd to heroScrimBlack(0.59f),
                    topEnd to heroScrimBlack(0f),
                    bottomStart to heroScrimBlack(0f),
                    bottomStart + 0.457f * (bottomEnd - bottomStart) to heroScrimBlack(0.59f),
                    bottomEnd to heroScrimBlack(1f),
                    1f to heroScrimBlack(1f),
                    startY = 0f,
                    endY = size.height,
                )
                onDrawBehind { drawRect(scrim) }
            }
    )
}

private fun heroScrimBlack(gradient: Float): Color =
    Color.Black.copy(alpha = 1f - (1f - HeroCarouselTokens.HeroDim) * (1f - gradient))
