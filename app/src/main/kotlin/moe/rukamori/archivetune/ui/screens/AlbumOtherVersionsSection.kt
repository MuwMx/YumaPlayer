/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.viewmodels.AlbumUiState
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.albumOtherVersionsSection(
    otherVersions: List<AlbumItem>,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onAlbumClick: (String) -> Unit,
    onAlbumLongClick: (AlbumItem) -> Unit,
) {
    if (otherVersions.isNotEmpty()) {
        item(key = ALBUM_KEY_OTHER_VERSIONS_HEADER, contentType = CONTENT_TYPE_ALBUM_OTHER_VERSIONS_HEADER) {
            NavigationTitle(
                title = stringResource(R.string.other_versions),
            )
        }
        item(key = ALBUM_KEY_OTHER_VERSIONS_LIST, contentType = CONTENT_TYPE_ALBUM_OTHER_VERSIONS_LIST) {
            LazyRow {
                items(
                    items = otherVersions.distinctBy { it.id },
                    key = { it.id },
                ) { item ->
                    val haptic = LocalHapticFeedback.current
                    YouTubeGridItem(
                        item = item,
                        isActive = activeAlbumId == item.id,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = { onAlbumClick(item.id) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAlbumLongClick(item)
                                    },
                                ).animateItem(),
                    )
                }
            }
        }
    }
}

fun LazyListScope.albumStatePlaceholders(
    uiState: AlbumUiState,
    topPadding: Dp,
    onRetry: () -> Unit,
) {
    when (uiState) {
        AlbumUiState.Loading,
        AlbumUiState.Content,
        -> albumShimmerPlaceholder(topPadding)

        AlbumUiState.Empty -> albumEmptyPlaceholder(topPadding)

        is AlbumUiState.Error -> albumErrorPlaceholder(uiState, topPadding, onRetry)
    }
}

fun LazyListScope.albumShimmerPlaceholder(
    topPadding: Dp,
) {
    item(key = ALBUM_KEY_SHIMMER, contentType = CONTENT_TYPE_ALBUM_SHIMMER) {
        AlbumShimmerContent(topPadding = topPadding)
    }
}

fun LazyListScope.albumEmptyPlaceholder(
    topPadding: Dp,
) {
    item(key = ALBUM_KEY_EMPTY, contentType = CONTENT_TYPE_ALBUM_EMPTY) {
        AlbumEmptyContent(topPadding = topPadding)
    }
}

fun LazyListScope.albumErrorPlaceholder(
    state: AlbumUiState.Error,
    topPadding: Dp,
    onRetry: () -> Unit,
) {
    item(key = ALBUM_KEY_ERROR, contentType = CONTENT_TYPE_ALBUM_ERROR) {
        AlbumErrorContent(
            isNotFound = state.isNotFound,
            topPadding = topPadding,
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun AlbumShimmerContent(
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    ShimmerHost {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(top = topPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 8.dp, bottom = 20.dp)
                        .size(240.dp)
                        .shimmer()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface),
            )

            TextPlaceholder(
                height = 28.dp,
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextPlaceholder(
                height = 20.dp,
                modifier = Modifier.fillMaxWidth(0.4f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    TextPlaceholder(
                        height = 32.dp,
                        modifier = Modifier.width(70.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .shimmer()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                )
                ButtonPlaceholder(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                )
                ButtonPlaceholder(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .shimmer()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        repeat(6) {
            ListItemPlaceHolder()
        }
    }
}

@Composable
internal fun AlbumEmptyContent(
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_album),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_album_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AlbumErrorContent(
    isNotFound: Boolean,
    topPadding: Dp,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (isNotFound) {
                    stringResource(R.string.album_not_found)
                } else {
                    stringResource(R.string.error_unknown)
                },
            style = MaterialTheme.typography.titleLarge,
            color = if (isNotFound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                if (isNotFound) {
                    stringResource(R.string.album_not_found_desc)
                } else {
                    stringResource(R.string.error_unknown)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.retry))
        }
    }
}
