/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.screens.playlist.isResolvedAs
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.SpotifyAlbumUiState
import moe.rukamori.archivetune.viewmodels.SpotifyAlbumViewModel

@Composable
fun SpotifyAlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyAlbumViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }
    val successState = uiState as? SpotifyAlbumUiState.Success

    fun playTracks(tracks: List<SpotifyTrack>, startIndex: Int = 0, shuffled: Boolean = false) {
        val album = successState?.album ?: return
        val queueTracks = if (shuffled) tracks.shuffled() else tracks
        if (queueTracks.isEmpty() || resolvingTrackId != null) return
        val boundedIndex = startIndex.coerceIn(queueTracks.indices)
        val preloadTrack = queueTracks[boundedIndex]

        coroutineScope.launch {
            resolvingTrackId = preloadTrack.id
            try {
                val preloadItem = SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
                playerConnection.playQueue(
                    SpotifyTracksQueue(
                        title = album.name,
                        allTracks = queueTracks,
                        startIndex = boundedIndex,
                        preloadItem = preloadItem,
                        totalCount = queueTracks.size,
                        hasCustomOrder = true,
                    ),
                )
            } finally {
                resolvingTrackId = null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
        ExpressivePullToRefreshBox(
            isRefreshing = uiState is SpotifyAlbumUiState.Loading,
            onRefresh = viewModel::loadAlbum,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = uiState) {
                    SpotifyAlbumUiState.Loading -> {
                        item(key = "loading") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .padding(top = systemBarsTopPadding + AppBarHeight),
                                contentAlignment = Alignment.Center,
                            ) { CircularWavyProgressIndicator() }
                        }
                    }
                    is SpotifyAlbumUiState.Error -> {
                        item(key = "error") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight + 32.dp, start = 24.dp, end = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(state.messageResId),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    is SpotifyAlbumUiState.Success -> {
                        item(key = "album_header") {
                            SpotifyAlbumHeader(
                                album = state.album,
                                tracksCount = state.tracks.size,
                                topPadding = systemBarsTopPadding + AppBarHeight,
                                onPlayAll = { playTracks(state.tracks, startIndex = 0, shuffled = false) },
                                onShuffle = { playTracks(state.tracks, startIndex = 0, shuffled = true) },
                            )
                        }
                        itemsIndexed(
                            items = state.tracks,
                            key = { index, track -> "spotify_album_track_${track.id}_$index" },
                            contentType = { _, _ -> "spotify_album_track" },
                        ) { index, track ->
                            val isActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
                            val isResolving = resolvingTrackId == track.id
                            SpotifyTrackListItem(
                                track = track,
                                albumIndex = track.trackNumber ?: (index + 1),
                                isActive = isActive || isResolving,
                                isPlaying = isPlaying && !isResolving,
                                trailingContent = {
                                    if (isResolving) {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = resolvingTrackId == null || isActive) {
                                            if (isActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playTracks(state.tracks, startIndex = index, shuffled = false)
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            title = {
                if (showTopBarTitle && successState != null) {
                    Text(
                        text = successState.album.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}
