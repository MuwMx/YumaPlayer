/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
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
