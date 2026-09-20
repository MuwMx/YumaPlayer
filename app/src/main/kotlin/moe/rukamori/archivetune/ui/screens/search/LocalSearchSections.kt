/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.AlbumListItem
import moe.rukamori.archivetune.ui.component.ArtistListItem
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.PlaylistListItem
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.viewmodels.LocalFilter

internal fun LazyListScope.localSearchSectionHeader(
    filter: LocalFilter,
    pureBlack: Boolean,
    onClick: () -> Unit,
) {
    item(key = filter) {
        val filterIcon = localFilterIcon(filter)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusable()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            color =
                                if (pureBlack) {
                                    Color.White.copy(
                                        alpha = 0.08f,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                },
                            shape = RoundedCornerShape(10.dp),
                        ),
            ) {
                Icon(
                    painter = painterResource(filterIcon),
                    contentDescription = null,
                    tint = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = stringResource(localFilterTitle(filter)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint =
                    if (pureBlack) {
                        Color.White.copy(
                            alpha = 0.3f,
                        )
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.localSearchItems(
    items: List<LocalItem>,
    allSongs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    onDismiss: () -> Unit,
    isFromCache: Boolean,
    context: Context,
) {
    items(
        items = items.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_LOCAL_SEARCH_ITEM },
    ) { item ->
        when (item) {
            is Song -> {
                LocalSearchSongItem(
                    song = item,
                    allSongs = allSongs,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    playerConnection = playerConnection,
                    navController = navController,
                    menuState = menuState,
                    onDismiss = onDismiss,
                    isFromCache = isFromCache,
                    context = context,
                )
            }

            is Album -> {
                LocalSearchAlbumItem(
                    album = item,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    onDismiss = onDismiss,
                )
            }

            is Artist -> {
                LocalSearchArtistItem(
                    artist = item,
                    navController = navController,
                    onDismiss = onDismiss,
                )
            }

            is Playlist -> {
                LocalSearchPlaylistItem(
                    playlist = item,
                    navController = navController,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.localSearchSongBranch(
    songs: List<Song>,
    allSongs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    onDismiss: () -> Unit,
    isFromCache: Boolean,
    context: Context,
) {
    items(
        items = songs.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_LOCAL_SEARCH_ITEM },
    ) { song ->
        LocalSearchSongItem(
            song = song,
            allSongs = allSongs,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            playerConnection = playerConnection,
            navController = navController,
            menuState = menuState,
            onDismiss = onDismiss,
            isFromCache = isFromCache,
            context = context,
        )
    }
}

internal fun LazyListScope.localSearchAlbumBranch(
    albums: List<Album>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    items(
        items = albums.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_LOCAL_SEARCH_ITEM },
    ) { album ->
        LocalSearchAlbumItem(
            album = album,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            navController = navController,
            onDismiss = onDismiss,
        )
    }
}

internal fun LazyListScope.localSearchArtistBranch(
    artists: List<Artist>,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    items(
        items = artists.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_LOCAL_SEARCH_ITEM },
    ) { artist ->
        LocalSearchArtistItem(
            artist = artist,
            navController = navController,
            onDismiss = onDismiss,
        )
    }
}

internal fun LazyListScope.localSearchPlaylistBranch(
    playlists: List<Playlist>,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    items(
        items = playlists.distinctBy { it.id },
        key = { it.id },
        contentType = { CONTENT_TYPE_LOCAL_SEARCH_ITEM },
    ) { playlist ->
        LocalSearchPlaylistItem(
            playlist = playlist,
            navController = navController,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.LocalSearchSongItem(
    song: Song,
    allSongs: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    onDismiss: () -> Unit,
    isFromCache: Boolean,
    context: Context,
) {
    SongListItem(
        song = song,
        showInLibraryIcon = true,
        isActive = song.id == mediaMetadata?.id,
        isPlaying = isPlaying,
        trailingContent = {
            IconButton(
                onClick = {
                    menuState.show {
                        SongMenu(
                            originalSong = song,
                            navController = navController,
                            onDismiss = {
                                onDismiss()
                                menuState.dismiss()
                            },
                            isFromCache = isFromCache,
                        )
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                )
            }
        },
        modifier =
            Modifier
                .combinedClickable(
                    onClick = {
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            val songs = allSongs.map { it.toMediaItem() }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_searched_songs),
                                    items = songs,
                                    startIndex = songs.indexOfFirst { it.mediaId == song.id },
                                ),
                            )
                        }
                    },
                    onLongClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = {
                                    onDismiss()
                                    menuState.dismiss()
                                },
                                isFromCache = isFromCache,
                            )
                        }
                    },
                ).animateItem(),
    )
}

@Composable
internal fun LazyItemScope.LocalSearchAlbumItem(
    album: Album,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    AlbumListItem(
        album = album,
        isActive = album.id == mediaMetadata?.album?.id,
        isPlaying = isPlaying,
        modifier =
            Modifier
                .clickable {
                    onDismiss()
                    navController.navigate("album/${album.id}")
                }.animateItem(),
    )
}

@Composable
internal fun LazyItemScope.LocalSearchArtistItem(
    artist: Artist,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    ArtistListItem(
        artist = artist,
        modifier =
            Modifier
                .clickable {
                    onDismiss()
                    navController.navigate("artist/${artist.id}")
                }.animateItem(),
    )
}

@Composable
internal fun LazyItemScope.LocalSearchPlaylistItem(
    playlist: Playlist,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    PlaylistListItem(
        playlist = playlist,
        modifier =
            Modifier
                .clickable {
                    onDismiss()
                    navController.navigate("local_playlist/${playlist.id}")
                }.animateItem(),
    )
}

internal fun LazyListScope.localSearchEmptyItem() {
    item(key = KEY_LOCAL_SEARCH_EMPTY) {
        EmptyPlaceholder(
            icon = R.drawable.ic_search,
            text = stringResource(R.string.no_results_found),
        )
    }
}
