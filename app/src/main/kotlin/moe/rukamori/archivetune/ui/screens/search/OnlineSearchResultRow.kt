/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.*
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.*
import moe.rukamori.archivetune.viewmodels.OnlineSearchSuggestionViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.OnlineSearchResultRow(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    menuState: MenuState,
    navController: NavController,
    coroutineScope: CoroutineScope,
    haptics: YumaHaptics,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YouTubeListItem(
        item = item,
        isActive =
            when (item) {
                is SongItem -> mediaMetadata?.id == item.id
                is AlbumItem -> mediaMetadata?.album?.id == item.id
                else -> false
            },
        isPlaying = isPlaying,
        trailingContent = {
            IconButton(
                onClick = {
                    menuState.show {
                        when (item) {
                            is SongItem -> {
                                YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            }

                            is AlbumItem -> {
                                YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            }

                            is ArtistItem -> {
                                YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            }

                            is PlaylistItem -> {
                                YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = coroutineScope,
                                    onDismiss = {
                                        menuState.dismiss()
                                        onDismiss()
                                    },
                                )
                            }
                        }
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
            modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue.radio(item.toMediaMetadata()),
                                    )
                                    onDismiss()
                                }
                            }

                            is AlbumItem -> {
                                navController.navigate("album/${item.id}")
                                onDismiss()
                            }

                            is ArtistItem -> {
                                navController.navigate("artist/${item.id}")
                                onDismiss()
                            }

                            is PlaylistItem -> {
                                navController.navigate("online_playlist/${item.id}")
                                onDismiss()
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
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        },
                                    )
                                }

                                is AlbumItem -> {
                                    YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        },
                                    )
                                }

                                is ArtistItem -> {
                                    YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        },
                                    )
                                }

                                is PlaylistItem -> {
                                    YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = {
                                            menuState.dismiss()
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    },
                ).animateItem(),
    )
}
