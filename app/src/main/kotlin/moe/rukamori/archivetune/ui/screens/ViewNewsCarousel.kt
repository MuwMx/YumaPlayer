/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.NewsItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ViewNewsCarousel(
    imageUrls: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.size == 1) {
        val context = LocalContext.current
        val model =
            remember(context, imageUrls.first()) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrls.first())
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                modifier
                    .padding(horizontal = 24.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Image) { onImageClick(imageUrls.first()) },
        )
        return
    }

    val carouselState = rememberCarouselState { imageUrls.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier,
    ) { index ->
        val context = LocalContext.current
        val imageUrl = imageUrls[index]
        val model =
            remember(context, imageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Image) { onImageClick(imageUrl) },
        )
    }
}
@Composable
internal fun ViewNewsMetaRow(
    item: NewsItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.important) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(R.string.news_important_badge),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                border = null,
                shape = MaterialTheme.shapes.large,
            )
        }

        val formattedDate =
            remember(item.timestamp) {
                if (item.timestamp == 0L) {
                    ""
                } else {
                    DateTimeFormatter.ofPattern("d MMM yyyy").format(
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(item.timestamp), ZoneId.systemDefault()),
                    )
                }
            }

        AssistChip(
            onClick = {},
            label = {
                Text(
                    text = stringResource(R.string.news_author_on_date, item.author, formattedDate),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            border = null,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
@Composable
internal fun ViewNewsFullImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        val context = LocalContext.current
        val model =
            remember(context, imageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build()
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
