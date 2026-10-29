/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.spotifyAwareQueue
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.SpotifyLibraryPlaylistListItem
import moe.rukamori.archivetune.ui.component.SpotifyLikedSongsListCard
import moe.rukamori.archivetune.ui.screens.settings.SpotifyLoginFallback
import moe.rukamori.archivetune.ui.screens.settings.SpotifyLoginSheet
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.utils.reportException

@Composable
fun LibrarySpotifyPlaylistsScreen(
    navController: NavController,
    viewModel: SpotifyLibraryViewModel = hiltViewModel(),
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val playlists by viewModel.sortedPlaylists.collectAsStateWithLifecycle()
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val likedSongsTotal by viewModel.likedSongsTotal.collectAsStateWithLifecycle()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    var showSpotifyLogin by remember { mutableStateOf(false) }

    val playLikedSongs: () -> Unit = {
        playerConnection?.let { conn ->
            coroutineScope.launch {
                val preloadTrack = Spotify.likedSongs(limit = 1, offset = 0).getOrNull()?.items?.firstOrNull()?.track
                val preloadItem = preloadTrack?.let { SpotifyPlaybackResolver.resolveToMetadata(it) }
                conn.playQueue(
                    SpotifyLikedSongsQueue(
                        title = context.getString(R.string.spotify_liked_songs),
                        preloadItem = preloadItem,
                    ),
                )
            }
        }
    }

    val playPlaylist: (SpotifyPlaylist) -> Unit = { playlist ->
        playerConnection?.let { conn ->
            coroutineScope.launch {
                // Same as the playlist screen: the whole playlist is queued in order and
                // each track is matched to YouTube just before it plays.
                val items =
                    try {
                        viewModel.playlistTracks(playlist.id).mapNotNull { it.toPlaceholderMetadata() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        return@launch
                    }
                if (items.isNotEmpty()) conn.playQueue(spotifyAwareQueue(playlist.name, items))
            }
        }
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
                showSpotifyLogin = false
                viewModel.refreshPlaylists()
            },
        )
    }

    if (!spotifyState.isAuthenticated) {
        SpotifyLoginFallback(
            onLoginClick = { showSpotifyLogin = true },
            modifier = Modifier.padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
        return
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshPlaylists,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SpotifyLibrarySortControls(
                sortType = sortType,
                sortDescending = sortDescending,
                isGridView = isGridView,
                onSortTypeChange = viewModel::setSortType,
                onSortDescendingChange = viewModel::setSortDescending,
                onGridViewChange = viewModel::setGridView,
            )

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "spotify_liked_songs", contentType = "spotify_liked_songs") {
                        SpotifyLikedSongsListCard(
                            likedSongsTotal = likedSongsTotal,
                            position = YumaSegmentPosition.Single,
                            onClick = { navController.navigate("spotify_liked_songs") },
                            onPlay = playLikedSongs,
                        )
                    }

                    if (playlists.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "spotify_empty", contentType = "spotify_empty") {
                            LibraryEmptyState(
                                iconRes = R.drawable.queue_music,
                                titleRes = R.string.no_playlists_yet,
                                subtitleRes = R.string.spotify_no_sources,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        items(
                            items = playlists,
                            key = { playlist -> playlist.id },
                            contentType = { "spotify_playlist_grid" },
                        ) { playlist ->
                            SpotifyLibraryPlaylistGridCard(
                                playlist = playlist,
                                onClick = { navController.navigate("spotify_playlist/${playlist.id}") },
                                onPlay = { playPlaylist(playlist) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.LibraryItemGap),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    item(key = "spotify_liked_songs", contentType = "spotify_liked_songs") {
                        SpotifyLikedSongsListCard(
                            likedSongsTotal = likedSongsTotal,
                            position = YumaSegmentPosition.Single,
                            onClick = { navController.navigate("spotify_liked_songs") },
                            onPlay = playLikedSongs,
                        )
                    }

                    if (playlists.isEmpty()) {
                        item(key = "spotify_empty", contentType = "spotify_empty") {
                            LibraryEmptyState(
                                iconRes = R.drawable.queue_music,
                                titleRes = R.string.no_playlists_yet,
                                subtitleRes = R.string.spotify_no_sources,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = playlists,
                            key = { _, playlist -> playlist.id },
                            contentType = { _, _ -> "spotify_playlist" },
                        ) { _, playlist ->
                            SpotifyLibraryPlaylistListItem(
                                playlist = playlist,
                                position = YumaSegmentPosition.Single,
                                navController = navController,
                                onPlay = { playPlaylist(playlist) },
                            )
                        }
                    }
                }
            }
        }
    }
}
