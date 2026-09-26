/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SectionType
import moe.rukamori.archivetune.spotify.SpotifyHomeAction
import moe.rukamori.archivetune.spotify.SpotifyHomeNavigationEvent
import moe.rukamori.archivetune.spotify.SpotifyHomeScreenState
import moe.rukamori.archivetune.spotify.SpotifyHomeViewModel
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.screens.spotifyhome.HomeStatePane
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyAlbumSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyArtistSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyPlaylistSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyRecentPanel
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyTrackSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.resolveSpotifySectionTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun SpotifyHomeScreen(
    navController: NavController,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: SpotifyHomeViewModel = hiltViewModel(),
    onSwitchToYoutube: () -> Unit = {}
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is SpotifyHomeNavigationEvent.OpenAlbum -> navController.navigate("album/${event.browseId}")
                is SpotifyHomeNavigationEvent.OpenArtist -> navController.navigate("artist/${event.id}")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (headerScrollConnection != null) {
                    Modifier.nestedScroll(headerScrollConnection)
                } else {
                    Modifier
                }
            )
    ) {
        when (val state = screenState) {
            SpotifyHomeScreenState.Loading -> {
                HomeStatePane(
                    iconResId = null,
                    messageResId = null,
                    showLoadingIndicator = true,
                )
            }
            SpotifyHomeScreenState.Empty -> {
                HomeStatePane(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.no_results_found,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                )
            }
            is SpotifyHomeScreenState.Error -> {
                if (state.notAuthenticated == true) {
                    HomeStatePane(
                        iconResId = R.drawable.ic_about,
                        messageResId = R.string.spotify_not_connected,
                        actionResId = R.string.home_switch_to_yt,
                        onAction = onSwitchToYoutube,
                    )
                } else {
                    HomeStatePane(
                        iconResId = R.drawable.ic_about,
                        messageResId = state.messageResId,
                        actionResId = R.string.retry,
                        onAction = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                    )
                }
            }
            is SpotifyHomeScreenState.Success -> {
                ExpressivePullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.onAction(SpotifyHomeAction.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "spotify_recent_panel", contentType = "recent_panel") {
                            SpotifyRecentPanel(
                                recentItems = state.recentItems,
                                frequentArtists = state.frequentArtists,
                                onPlaylistClick = { playlist -> navController.navigate("spotify_playlist/${playlist.id}") },
                                onAlbumClick = { album -> 
                                    viewModel.onAction(SpotifyHomeAction.AlbumClick(
                                        moe.rukamori.archivetune.spotify.models.SpotifyAlbum(
                                            id = album.id,
                                            name = album.title,
                                            artists = album.artists,
                                            images = listOfNotNull(album.thumbnailUrl?.let { moe.rukamori.archivetune.spotify.models.SpotifyImage(it, null, null) })
                                        )
                                    )) 
                                },
                                onArtistClick = { artist -> viewModel.onAction(SpotifyHomeAction.ArtistClick(artist)) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        state.sections.forEachIndexed { index, section ->
                            item(
                                key = "spotify_section_title_${section.title}_$index",
                                contentType = "section_header"
                            ) {
                                HomeSectionHeader(
                                    title = resolveSpotifySectionTitle(section),
                                    modifier = Modifier.animateItem()
                                )
                            }

                            item(
                                key = "spotify_section_content_${section.title}_$index",
                                contentType = "section_content"
                            ) {
                                when (section.type) {
                                    SectionType.TRACKS -> {
                                        SpotifyTrackSectionRow(
                                            tracks = section.tracks,
                                            horizontalItemWidth = 240.dp,
                                            onTrackClick = { track ->
                                                playerConnection.playQueue(
                                                    SpotifyTracksQueue(
                                                        title = section.title,
                                                        initialTracks = section.tracks,
                                                        startIndex = section.tracks.indexOf(track),
                                                        totalCount = section.tracks.size,
                                                        hasCustomOrder = true,
                                                    )
                                                )
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    SectionType.ARTISTS -> {
                                        SpotifyArtistSectionRow(
                                            artists = section.artists,
                                            onArtistClick = { artist -> viewModel.onAction(SpotifyHomeAction.ArtistClick(artist)) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    SectionType.ALBUMS -> {
                                        SpotifyAlbumSectionRow(
                                            albums = section.albums,
                                            onAlbumClick = { album -> viewModel.onAction(SpotifyHomeAction.AlbumClick(album)) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    SectionType.PLAYLISTS -> {
                                        SpotifyPlaylistSectionRow(
                                            playlists = section.playlists,
                                            onPlaylistClick = { playlist ->
                                                navController.navigate("spotify_playlist/${playlist.id}")
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
