/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SuggestedSongsSection(
    songs: List<SongItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val sectionTitle = stringResource(R.string.stats_unique_songs)
    SectionContainer(
        title = sectionTitle,
        modifier = modifier,
    ) {
        val visibleSongs = remember(songs) { songs.take(6) }

        Column(
            verticalArrangement = Arrangement.spacedBy(SuggestedSongGroupItemSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SuggestedSongGroupHorizontalPadding,
                        vertical = SuggestedSongGroupVerticalPadding,
                    ),
        ) {
            visibleSongs.forEachIndexed { index, song ->
                Card(
                    shape = segmentedSuggestedSongShape(index = index, count = visibleSongs.size),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                            ),
                ) {
                    YouTubeListItem(
                        item = song,
                        albumIndex = index + 1,
                        viewCountText = song.viewCountText,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        trailingContent = {
                            YouTubeSongMenuButton(song = song, navController = navController)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrendingAlbumsSection(
    albums: List<AlbumItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    NavigationTitle(
        title = stringResource(R.string.top_albums),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = albums,
            key = { album -> album.id },
            contentType = { "trending_album" },
        ) { album ->
            YouTubeGridItem(
                item = album,
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
                                    YouTubeAlbumMenu(
                                        albumItem = album,
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SuggestedArtistsSection(
    artists: List<ArtistItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return

    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()

    NavigationTitle(
        title = stringResource(R.string.stats_unique_artists),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = artists,
            key = { artist -> artist.id },
            contentType = { "trending_artist" },
        ) { artist ->
            YouTubeGridItem(
                item = artist,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("artist/${artist.id}")
                            },
                            onLongClick = {
                                haptics.longPress()
                                menuState.show {
                                    YouTubeArtistMenu(
                                        artist = artist,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }
    }
}
@Composable
private fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationTitle(
        title = title,
        modifier = modifier,
    )
    content()
}
