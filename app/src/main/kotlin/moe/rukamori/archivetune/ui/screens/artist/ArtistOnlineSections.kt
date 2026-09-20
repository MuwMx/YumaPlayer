/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ARTIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_PLAYLIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.artistOnlineSections(
    artistPage: ArtistPage?,
    artistId: String,
    navController: NavController,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    menuState: MenuState,
    haptics: YumaHaptics,
    coroutineScope: CoroutineScope,
) {
    artistPage?.sections?.fastForEach { section ->
        if (section.items.isNotEmpty()) {
            item(
                key = "youtube_section_header_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                NavigationTitle(
                    title = section.title,
                    onClick =
                        section.moreEndpoint?.let {
                            {
                                navController.navigate(buildArtistItemsRoute(artistId, it))
                            }
                        },
                )
            }
        }

        if (section.layout == ArtistSectionLayout.LIST && section.items.all { it is SongItem }) {
            itemsIndexed(
                items = section.items.distinctBy { it.id },
                key = { index, song -> "youtube_song_${song.id}_$index" },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, song ->
                YouTubeListItem(
                    item = song as SongItem,
                    isActive = mediaMetadata?.id == song.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    YouTubeSongMenu(
                                        song = song,
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
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = song.id),
                                                song.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptics.longPress()
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ).animateItem(),
                )
            }
        } else {
            item(
                key = "youtube_section_grid_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                contentType = CONTENT_TYPE_LIST,
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = section.items.distinctBy { it.id },
                        key = {
                            val type =
                                when (it) {
                                    is SongItem -> "song"
                                    is AlbumItem -> "album"
                                    is ArtistItem -> "artist"
                                    is PlaylistItem -> "playlist"
                                    else -> "item"
                                }
                            "youtube_${type}_${it.id}"
                        },
                        contentType = {
                            when (it) {
                                is SongItem -> CONTENT_TYPE_SONG
                                is AlbumItem -> CONTENT_TYPE_ALBUM
                                is ArtistItem -> CONTENT_TYPE_ARTIST
                                is PlaylistItem -> CONTENT_TYPE_PLAYLIST
                                else -> CONTENT_TYPE_LIST
                            }
                        },
                    ) { item ->
                        YouTubeGridItem(
                            item = item,
                            isActive =
                                when (item) {
                                    is SongItem -> mediaMetadata?.id == item.id
                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                    else -> false
                                },
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            when (item) {
                                                is SongItem -> {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue(
                                                            WatchEndpoint(videoId = item.id),
                                                            item.toMediaMetadata(),
                                                        ),
                                                    )
                                                }

                                                is AlbumItem -> {
                                                    navController.navigate("album/${item.id}")
                                                }

                                                is ArtistItem -> {
                                                    navController.navigate("artist/${item.id}")
                                                }

                                                is PlaylistItem -> {
                                                    navController.navigate("online_playlist/${item.id}")
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            haptics.longPress()
                                            menuState.show {
                                                when (item) {
                                                    is SongItem -> {
                                                        YouTubeSongMenu(
                                                            song = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }

                                                    is AlbumItem -> {
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }

                                                    is ArtistItem -> {
                                                        YouTubeArtistMenu(
                                                            artist = item,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }

                                                    is PlaylistItem -> {
                                                        YouTubePlaylistMenu(
                                                            playlist = item,
                                                            coroutineScope = coroutineScope,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                    ).animateItem(),
                        )
                    }
                }
            }
        }
    }
}

fun buildArtistItemsRoute(
    artistId: String,
    endpoint: BrowseEndpoint,
): String {
    val encodedArtistId = Uri.encode(artistId)
    val encodedBrowseId = Uri.encode(endpoint.browseId)
    val encodedParams =
        endpoint.params
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.encode(it) }

    return buildString {
        append("artist/")
        append(encodedArtistId)
        append("/items?browseId=")
        append(encodedBrowseId)
        if (encodedParams != null) {
            append("&params=")
            append(encodedParams)
        }
    }
}
