/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.RecognitionHistoryItemUiModel

@Composable
internal fun RecognitionHistoryListItem(
    item: RecognitionHistoryItemUiModel,
    index: Int,
    count: Int,
    onSearch: (String) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    val searchAction = remember(item.searchQuery, onSearch) { { onSearch(item.searchQuery) } }
    val shazamUrl = item.shazamUrl
    val openShazamAction: () -> Unit =
        remember(shazamUrl, onOpenUri) {
            { shazamUrl?.let(onOpenUri) }
        }
    SegmentedListItem(
        onClick = searchAction,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            CoverArt(
                artworkUrl = item.artworkUrl,
                displaySize = 64.dp,
            )
        },
        overlineContent = {
            Text(
                text =
                    stringResource(
                        R.string.music_recognition_history_recognized_at,
                        item.recognizedAt,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = item.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.metadata.isNotEmpty()) {
                    Text(
                        text = item.metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shazamUrl != null) {
                    IconButton(onClick = openShazamAction) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.music_recognition_open_shazam),
                        )
                    }
                }
                IconButton(onClick = searchAction) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search),
                    )
                }
            }
        },
        content = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
@Composable
internal fun RecognitionHistoryEmptyState(
    iconRes: Int,
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
@Composable
internal fun CoverArt(
    artworkUrl: String?,
    displaySize: Dp,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(displaySize, density) { with(density) { displaySize.roundToPx() } }
    val imageRequest =
        remember(context, artworkUrl, sizePx) {
            artworkUrl?.let {
                ImageRequest
                    .Builder(context)
                    .data(it)
                    .size(sizePx, sizePx)
                    .allowHardware(true)
                    .build()
            }
        }

    Surface(
        modifier = Modifier.size(displaySize),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(displaySize / 3),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
internal const val RecognitionHistoryItemContentType = "recognition_history_item"
internal const val MusicRecognitionStateItemContentType = "music_recognition_state"
internal const val MusicRecognitionStateItemKey = "music_recognition_state"
internal const val StateTransitionInitialScale = 0.96f
internal const val StateTransitionTargetScale = 0.98f
