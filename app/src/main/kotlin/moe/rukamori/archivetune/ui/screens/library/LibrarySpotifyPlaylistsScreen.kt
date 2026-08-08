/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.SpotifyLibraryPlaylistListItem
import moe.rukamori.archivetune.ui.component.SpotifyLikedSongsListCard

@Composable
fun LibrarySpotifyPlaylistsScreen(
    navController: NavController,
    viewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshPlaylists,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding =
                PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = playerAwareBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "spotify_liked_songs", contentType = "spotify_liked_songs") {
                val likedSongsTotal by viewModel.likedSongsTotal.collectAsStateWithLifecycle()
                SpotifyLikedSongsListCard(
                    likedSongsTotal = likedSongsTotal,
                    onClick = { navController.navigate("spotify_liked_songs") },
                    onPlay = {
                        playerConnection?.let { conn ->
                            coroutineScope.launch {
                                val preloadTrack = Spotify.likedSongs(limit = 1, offset = 0).getOrNull()?.items?.firstOrNull()?.track
                                val preloadItem = preloadTrack?.let { SpotifyPlaybackResolver.resolveToMetadata(it) }
                                conn.playQueue(
                                    SpotifyLikedSongsQueue(
                                        title = context.getString(R.string.spotify_liked_songs),
                                        preloadItem = preloadItem,
                                    )
                                )
                            }
                        }
                    },
                )
            }

            if (playlists.isEmpty()) {
                item(key = "spotify_empty", contentType = "spotify_empty") {
                    Text(
                        text = stringResource(R.string.spotify_no_sources),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(
                items = playlists,
                key = { playlist -> playlist.id },
                contentType = { "spotify_playlist" },
            ) { playlist ->
                SpotifyLibraryPlaylistListItem(
                    playlist = playlist,
                    navController = navController,
                    onPlay = {
                        playerConnection?.let { conn ->
                            coroutineScope.launch {
                                val preloadTrack = Spotify.playlistTracks(playlistId = playlist.id, limit = 1, offset = 0).getOrNull()?.items?.firstOrNull()?.track
                                val preloadItem = preloadTrack?.let { SpotifyPlaybackResolver.resolveToMetadata(it) }
                                conn.playQueue(
                                    SpotifyPlaylistQueue(
                                        playlistId = playlist.id,
                                        title = playlist.name,
                                        preloadItem = preloadItem,
                                    )
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
