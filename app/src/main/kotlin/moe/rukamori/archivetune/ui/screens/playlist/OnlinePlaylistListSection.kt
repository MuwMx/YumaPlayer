/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import com.valentinilk.shimmer.shimmer
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.formatCompactCount

fun filterPlaylistSongs(
    songs: List<SongItem>,
    query: String,
): List<Pair<Int, SongItem>> {
    if (query.isEmpty()) {
        return songs.mapIndexed { index, song -> index to song }
    }
    return songs
        .mapIndexed { index, song -> index to song }
        .filter { (_, song) ->
            song.title.contains(query, ignoreCase = true) ||
                song.artists.fastAny { it.name.contains(query, ignoreCase = true) }
        }
}

fun LazyListScope.onlinePlaylistShimmerItem(systemBarsTopPadding: Dp) {
    item(key = ONLINE_PLAYLIST_KEY_SHIMMER, contentType = CONTENT_TYPE_ONLINE_PLAYLIST_SHIMMER) {
        ShimmerHost {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = systemBarsTopPadding + AppBarHeight),
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
                        Modifier.fillMaxWidth(0.6f).padding(horizontal = 32.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextPlaceholder(
                    height = 20.dp,
                    modifier = Modifier.fillMaxWidth(0.4f),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(2) {
                        TextPlaceholder(
                            height = 32.dp,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .shimmer()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface),
                    )
                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
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

            repeat(6) { ListItemPlaceHolder() }
        }
    }
}

fun LazyListScope.onlinePlaylistHeaderItem(
    playlist: PlaylistItem,
    isBookmarked: Boolean,
    downloadState: HeaderDownloadState,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    actions: OnlinePlaylistActions,
) {
    item(key = ONLINE_PLAYLIST_KEY_HEADER, contentType = CONTENT_TYPE_ONLINE_PLAYLIST_HEADER) {
        OnlinePlaylistHeroSection(
            playlist = playlist,
            isBookmarked = isBookmarked,
            downloadState = downloadState,
            gradientColors = gradientColors,
            systemBarsTopPadding = systemBarsTopPadding,
            actions = actions,
            modifier = Modifier.animateItem(),
        )
    }
}

fun LazyListScope.onlinePlaylistEmptyItem() {
    item(key = ONLINE_PLAYLIST_KEY_EMPTY, contentType = CONTENT_TYPE_ONLINE_PLAYLIST_EMPTY) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.empty_playlist),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_playlist_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.onlinePlaylistSongItems(
    wrappedSongs: List<WrappedSongItem>,
    viewCounts: Map<String, Int>,
    activeMediaId: String?,
    isPlaying: Boolean,
    selection: Boolean,
    hideExplicit: Boolean,
    actions: OnlinePlaylistActions,
) {
    items(
        items = wrappedSongs,
        key = { onlinePlaylistSongKey(it) },
        contentType = { CONTENT_TYPE_ONLINE_PLAYLIST_SONG },
    ) { song ->
        OnlinePlaylistSongRow(
            song = song,
            viewCounts = viewCounts,
            activeMediaId = activeMediaId,
            isPlaying = isPlaying,
            selection = selection,
            hideExplicit = hideExplicit,
            actions = actions,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.OnlinePlaylistSongRow(
    song: WrappedSongItem,
    viewCounts: Map<String, Int>,
    activeMediaId: String?,
    isPlaying: Boolean,
    selection: Boolean,
    hideExplicit: Boolean,
    actions: OnlinePlaylistActions,
    modifier: Modifier = Modifier,
) {
    YouTubeListItem(
        item = song.item.second,
        viewCountText =
            viewCounts[song.item.second.id]?.let { count ->
                formatCompactCount(count.toLong())
            },
        isActive = activeMediaId == song.item.second.id,
        isPlaying = isPlaying,
        isSelected = song.isSelected && selection,
        trailingContent = {
            IconButton(
                onClick = { actions.onSongMenu(song.item.second) },
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                )
            }
        },
        modifier =
            modifier
                .combinedClickable(
                    enabled = !hideExplicit || !song.item.second.explicit,
                    onClick = { actions.onSongClick(song) },
                    onLongClick = { actions.onSongLongClick(song) },
                ).animateItem(),
    )
}

fun LazyListScope.onlinePlaylistLoadingMoreItem() {
    item(key = ONLINE_PLAYLIST_KEY_LOADING_MORE, contentType = CONTENT_TYPE_ONLINE_PLAYLIST_LOADING_MORE) {
        ShimmerHost { repeat(2) { ListItemPlaceHolder() } }
    }
}

fun LazyListScope.onlinePlaylistErrorItem(
    error: String?,
    onRetry: () -> Unit,
) {
    val isPrivatePlaylist = error?.contains("PLAYLIST_PRIVATE") == true
    item(key = ONLINE_PLAYLIST_KEY_ERROR, contentType = CONTENT_TYPE_ONLINE_PLAYLIST_ERROR) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isPrivatePlaylist) {
                Image(
                    painter = painterResource(R.drawable.anime_blank),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.playlist_private_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.playlist_private_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text =
                        if (error != null) {
                            stringResource(R.string.error_unknown)
                        } else {
                            stringResource(R.string.playlist_not_found)
                        },
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        if (error != null) {
                            error
                        } else {
                            stringResource(R.string.playlist_not_found_desc)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
fun OnlinePlaylistScrollbar(
    scrollState: LazyListState,
    headerItems: Int,
    modifier: Modifier = Modifier,
) {
    DraggableScrollbar(
        modifier = modifier,
        scrollState = scrollState,
        headerItems = headerItems,
    )
}
