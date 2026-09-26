/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableSpotifyKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryMixViewModel
import moe.rukamori.archivetune.viewmodels.MostPlayedAlbumUiState

@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    onTabSelected: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
    spotifyLibraryViewModel: SpotifyLibraryViewModel = hiltViewModel(),
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val likedSongsCount by database.likedSongsCount(LikeSource.YTM).collectAsState(initial = 0)
    val recentSongs by database.recentSongs(15).collectAsState(initial = emptyList())

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mostPlayedAlbumUiState by viewModel.mostPlayedAlbumUiState.collectAsStateWithLifecycle()
    val topMixesUiState by viewModel.topMixesUiState.collectAsStateWithLifecycle()
    val spotifyPlaylists by spotifyLibraryViewModel.playlists.collectAsStateWithLifecycle()
    val spotifyAccountState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    val likedSongsTotal by spotifyLibraryViewModel.likedSongsTotal.collectAsStateWithLifecycle()
    val (enableSpotify) = rememberPreference(EnableSpotifyKey, true)
    val spDc by rememberPreference(SpotifySpDcKey, defaultValue = "")
    val isSpotifyActive = enableSpotify && (spotifyAccountState.isAuthenticated || spDc.isNotBlank())

    val filteredPlaylistIds by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsState(initial = emptyList())

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds) {
            playlists.filter { playlist ->
                val name = playlist.playlist.name
                val matchesName = !name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
                matchesName && matchesTags
            }
        }
    val visibleSpotifyPlaylists =
        remember(isSpotifyActive, spotifyPlaylists) {
            if (isSpotifyActive) {
                spotifyPlaylists
            } else {
                emptyList()
            }
        }
    val mostPlayedAlbum = (mostPlayedAlbumUiState as? MostPlayedAlbumUiState.Success)?.album
    val playSpotlightAlbum: () -> Unit =
        remember(mostPlayedAlbum?.tracks, playerConnection) {
            {
                mostPlayedAlbum?.let { album ->
                    playerConnection.playQueue(
                        ListQueue(items = album.tracks.map { it.toMediaItem() }),
                    )
                } ?: Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_SHORT).show()
            }
        }
    val shuffleSpotlightAlbum: () -> Unit =
        remember(mostPlayedAlbum?.tracks, playerConnection) {
            {
                mostPlayedAlbum?.let { album ->
                    playerConnection.playQueue(
                        ListQueue(items = album.tracks.shuffled().map { it.toMediaItem() }),
                    )
                }
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.topMixEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.syncAllLibrary() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            spotlightSection(
                albumUiState = mostPlayedAlbumUiState,
                onOpenAlbum = {
                    mostPlayedAlbum?.let { album ->
                        navController.navigate("album/${album.id}")
                    }
                },
                onPlayAll = playSpotlightAlbum,
                onShuffle = shuffleSpotlightAlbum,
            )

            shortcutsSection(
                likedSongsCount = likedSongsCount,
                onOpenLiked = { navController.navigate("auto_playlist/liked") },
                onOpenDownloads = { navController.navigate("auto_playlist/downloaded") },
                onOpenCache = { navController.navigate("cache_playlist/cached") },
                onOpenLocal = { navController.navigate("local_songs") },
            )

            recentlyPlayedSection(
                recentSongs = recentSongs,
                onPlayRecent = { song ->
                    playerConnection.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                },
            )

            topMixesSection(
                uiState = topMixesUiState,
                onRefreshTopMixes = viewModel::refreshTopMixes,
                onConfigureAi = { navController.navigate("settings/ai_integration") },
                onPlayMix = { mix ->
                    playerConnection.playQueue(
                        ListQueue(
                            items = mix.tracks.map { it.toMediaItem() },
                        ),
                    )
                },
            )

            val playlistTagFilterContent = filterContent
            if (playlistTagFilterContent != null) {
                item(key = "playlist_tag_filters") {
                    playlistTagFilterContent()
                }
            }

            // Playlists Row
            playlistsRowSection(
                visiblePlaylists = visiblePlaylists,
                visibleSpotifyPlaylists = visibleSpotifyPlaylists,
                isSpotifyActive = isSpotifyActive,
                likedSongsTotal = likedSongsTotal,
                onOpenPlaylist = { playlist ->
                    if (!playlist.playlist.isEditable && playlist.playlist.browseId?.startsWith("VL") == true &&
                        (playlist.songCount == 0 || playlist.playlist.remoteSongCount == 0)
                    ) {
                        navController.navigate("online_playlist/${playlist.playlist.browseId}")
                    } else {
                        navController.navigate("local_playlist/${playlist.id}")
                    }
                },
                onPlayPlaylist = { playlist ->
                    playerConnection.let { conn ->
                        coroutineScope.launch {
                            database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                if (songs.isNotEmpty()) {
                                    conn.playQueue(
                                        ListQueue(items = songs.map { it.song.toMediaItem() }),
                                    )
                                }
                            }
                        }
                    }
                },
                onPlaySpotifyLiked = {
                    playerConnection.let { conn ->
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
                },
                onOpenSpotifyPlaylist = { playlist ->
                    navController.navigate("spotify_playlist/${playlist.id}")
                },
                onSeeAll = { onTabSelected(LibraryFilter.PLAYLISTS) },
                onOpenSpotifyLiked = { navController.navigate("spotify_liked_songs") },
            )

            artistsRowSection(
                artists = artists,
                onOpenArtist = { artist ->
                    navController.navigate("artist/${artist.id}")
                },
                onSeeAllArtists = {
                    onTabSelected(LibraryFilter.ARTISTS)
                },
            )
        }
    }
}
