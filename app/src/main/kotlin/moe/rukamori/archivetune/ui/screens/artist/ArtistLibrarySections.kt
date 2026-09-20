/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.AlbumGridItem
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SongMenu

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.artistLibrarySections(
    artistId: String,
    libraryArtist: Artist?,
    librarySongs: List<Song>,
    libraryAlbums: List<Album>,
    hideExplicit: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    menuState: MenuState,
    haptics: YumaHaptics,
    coroutineScope: CoroutineScope,
) {
    if (librarySongs.isNotEmpty()) {
        item {
            NavigationTitle(
                title = stringResource(R.string.songs),
                onClick = {
                    navController.navigate("artist/$artistId/songs")
                },
            )
        }

        val filteredLibrarySongs =
            if (hideExplicit) {
                librarySongs.filter { !it.song.explicit }
            } else {
                librarySongs
            }

        itemsIndexed(
            items = filteredLibrarySongs.take(5),
            key = { index, item -> "local_song_${item.id}_$index" },
            contentType = { _, _ -> CONTENT_TYPE_SONG },
        ) { index, song ->
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
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                            items = librarySongs.map { it.toMediaItem() },
                                            startIndex = index,
                                        ),
                                    )
                                }
                            },
                            onLongClick = {
                                haptics.longPress()
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }

        // Show "View All" if more songs available
        if (filteredLibrarySongs.size > 5) {
            item {
                Surface(
                    onClick = {
                        navController.navigate("artist/$artistId/songs")
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (libraryAlbums.isNotEmpty()) {
        item {
            NavigationTitle(
                title = stringResource(R.string.albums),
                onClick = {
                    navController.navigate("artist/$artistId/albums")
                },
            )
        }

        item {
            val filteredLibraryAlbums =
                if (hideExplicit) {
                    libraryAlbums.filter { !it.album.explicit }
                } else {
                    libraryAlbums
                }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = filteredLibraryAlbums,
                    key = { index, album -> "local_album_${album.id}_$index" },
                    contentType = { _, _ -> CONTENT_TYPE_ALBUM },
                ) { index, album ->
                    AlbumGridItem(
                        album = album,
                        isActive = mediaMetadata?.album?.id == album.id,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${album.id}")
                                    },
                                    onLongClick = {
                                        haptics.longPress()
                                        menuState.show {
                                            AlbumMenu(
                                                originalAlbum = album,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ).animateItem(),
                    )
                }
            }
        }
    }
}
