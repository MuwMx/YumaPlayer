/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    shouldLoadImage: Boolean = true,
    @DrawableRes placeholderIconRes: Int? = null,
    thumbnailRatio: Float = 1f,
    cropToSquare: Boolean? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetDim = if (cropToSquare != null) 200 else 544
    val targetSizePx = with(density) {
        if (cropToSquare != null) ListThumbnailSize.roundToPx() else 544.dp.roundToPx()
    }
    val cropThumbnailToSquare = cropToSquare ?: rememberPreference(CropThumbnailToSquareKey, false).value
    val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
    val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        if (albumIndex == null) {
            if (placeholderIconRes != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(placeholderIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ListThumbnailSize * 0.48f),
                    )
                }
            }

            if (shouldLoadImage && !thumbnailUrl.isNullOrBlank()) {
                val request =
                    remember(thumbnailUrl, targetDim, targetSizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(thumbnailUrl.resize(targetDim, targetDim))
                            .size(targetSizePx, targetSizePx)
                            .allowHardware(true)
                            .build()
                    }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
                )
            } else if (placeholderIconRes == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null,
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color =
                if (albumIndex != null) {
                    if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                } else {
                    Color.White
                },
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color =
                            if (albumIndex != null) {
                                Color.Transparent
                            } else {
                                Color.Black.copy(alpha = ActiveBoxAlpha)
                            },
                        shape = shape,
                    ),
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val request =
            remember(thumbnailUrl, widthPx, heightPx) {
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(true)
                    .apply {
                        if (widthPx != null && heightPx != null) {
                            size(widthPx, heightPx)
                        }
                    }.build()
            }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), shape),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        if (showCenterPlay) {
            AnimatedVisibility(
                visible = !(isActive && isPlaying),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        if (playButtonVisible) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = ActiveBoxAlpha)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    when (thumbnails.size) {
        0 -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                placeHolder()
            }
        }

        1 -> {
            val request =
                remember(thumbnails, sizePx) {
                    ImageRequest
                        .Builder(context)
                        .data(thumbnails[0].resize((sizePx * 1.5).toInt(), (sizePx * 1.5).toInt()))
                        .size(sizePx, sizePx)
                        .allowHardware(true)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            )
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd,
                ).fastForEachIndexed { index, alignment ->
                    val halfPx = (sizePx / 2).coerceAtLeast(1)
                    val url = thumbnails.getOrNull(index)
                    val request =
                        remember(url, halfPx) {
                            ImageRequest
                                .Builder(context)
                                .data(url?.resize((halfPx * 1.5).toInt(), (halfPx * 1.5).toInt()))
                                .size(halfPx, halfPx)
                                .allowHardware(true)
                                .build()
                        }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .align(alignment)
                                .size(size / 2),
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.OverlayPlayButton(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .align(Alignment.Center),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = ActiveBoxAlpha)),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                    .clickable(onClick = onClick),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
