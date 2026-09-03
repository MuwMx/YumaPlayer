package moe.rukamori.archivetune.ui.screens
import moe.rukamori.archivetune.ui.screens.HomeSectionHeader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.spotify.SectionType
import moe.rukamori.archivetune.spotify.SpotifyHomeAction
import moe.rukamori.archivetune.spotify.SpotifyHomeNavigationEvent
import moe.rukamori.archivetune.spotify.SpotifyHomeSection
import moe.rukamori.archivetune.spotify.SpotifyHomeScreenState
import moe.rukamori.archivetune.spotify.SpotifyHomeViewModel
import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.theme.yumaClickable

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
                    isRefreshing = false,
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
                                                        startIndex = section.tracks.indexOf(track)
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

@Composable
private fun resolveSpotifySectionTitle(section: SpotifyHomeSection): String {
    val title = section.title
    return when {
        title.startsWith("spotify_because_you_like:") -> {
            val artistName = title.removePrefix("spotify_because_you_like:")
            stringResource(R.string.spotify_because_you_like, artistName)
        }
        title == "spotify_top_tracks" -> stringResource(R.string.spotify_top_tracks)
        title == "spotify_top_artists" -> stringResource(R.string.spotify_top_artists)
        title == "spotify_made_for_you" -> stringResource(R.string.spotify_made_for_you)
        title == "spotify_discover" -> stringResource(R.string.spotify_discover)
        title == "spotify_your_playlists" -> stringResource(R.string.spotify_your_playlists)
        title == "spotify_new_releases" -> stringResource(R.string.spotify_new_releases)
        else -> title
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyTrackSectionRow(
    tracks: List<SpotifyTrack>,
    horizontalItemWidth: Dp,
    onTrackClick: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp * 4)
    ) {
        items(
            items = tracks,
            key = { "spotify_track_${it.id}" },
            contentType = { "spotify_track" }
        ) { track ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(horizontalItemWidth),
            ) {
                SpotifyTrackListItem(
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .yumaClickable(onClick = { onTrackClick(track) }),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyArtistSectionRow(
    artists: List<SpotifyArtist>,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = artists,
            key = { "spotify_artist_${it.id}" },
            contentType = { "spotify_artist" }
        ) { artist ->
            val thumbnail = remember(artist.id) {
                artist.images.maxByOrNull { it.width ?: 0 }?.url
                    ?: artist.images.firstOrNull()?.url
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(140.dp)
                    .yumaClickable(onClick = { onArtistClick(artist) }),
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape),
                )
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyAlbumSectionRow(
    albums: List<SpotifyAlbum>,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = albums,
            key = { "spotify_album_${it.id}" },
            contentType = { "spotify_album" }
        ) { album ->
            val albumItem = remember(album.id) {
                AlbumItem(
                    browseId = album.id,
                    playlistId = album.id,
                    title = album.name,
                    artists = album.artists.map { Artist(it.name, it.id) },
                    thumbnail = album.images.maxByOrNull { it.width ?: 0 }?.url ?: album.images.firstOrNull()?.url ?: "",
                )
            }
            YouTubeGridItem(
                item = albumItem,
                isActive = false,
                isPlaying = false,
                fillMaxWidth = true,
                modifier = Modifier
                    .width(150.dp)
                    .yumaClickable(onClick = { onAlbumClick(album) }),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyPlaylistSectionRow(
    playlists: List<SpotifyPlaylist>,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = playlists,
            key = { "spotify_playlist_${it.id}" },
            contentType = { "spotify_playlist" }
        ) { playlist ->
            val playlistItem = remember(playlist.id) {
                PlaylistItem(
                    id = playlist.id,
                    title = playlist.name,
                    author = playlist.owner?.displayName?.let { Artist(it, null) },
                    songCountText = playlist.tracks?.total?.toString(),
                    thumbnail = playlist.images.maxByOrNull { it.width ?: 0 }?.url ?: playlist.images.firstOrNull()?.url ?: "",
                    playEndpoint = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null,
                )
            }
            YouTubeGridItem(
                item = playlistItem,
                isActive = false,
                isPlaying = false,
                fillMaxWidth = true,
                modifier = Modifier
                    .width(150.dp)
                    .yumaClickable(onClick = { onPlaylistClick(playlist) }),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatePane(
    iconResId: Int?,
    messageResId: Int?,
    modifier: Modifier = Modifier,
    actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                androidx.compose.material3.LoadingIndicator()
            } else {
                iconResId?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                messageResId?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.material3.FilledTonalButton(onClick = onAction) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyRecentPanel(
    recentItems: List<SpotifyRecentItem>,
    frequentArtists: List<SpotifyArtist>,
    onPlaylistClick: (SpotifyRecentItem.Playlist) -> Unit,
    onAlbumClick: (SpotifyRecentItem.Album) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (recentItems.isNotEmpty()) {
            HomeSectionHeader(
                title = stringResource(R.string.spotify_recently_played),
            )
            SpotifyQuickGrid(
                items = recentItems,
                maxItems = 8,
                columns = 2
            ) { item ->
                when (item) {
                    is SpotifyRecentItem.Playlist -> {
                        SpotifyQuickGridCell(
                            title = item.title,
                            imageUrl = item.thumbnailUrl,
                            onClick = { onPlaylistClick(item) },
                            isArtist = false
                        )
                    }
                    is SpotifyRecentItem.Album -> {
                        SpotifyQuickGridCell(
                            title = item.title,
                            imageUrl = item.thumbnailUrl,
                            onClick = { onAlbumClick(item) },
                            isArtist = false
                        )
                    }
                }
            }
        }

        if (frequentArtists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HomeSectionHeader(
                title = stringResource(R.string.spotify_frequently_listened),
            )
            SpotifyQuickGrid(
                items = frequentArtists,
                maxItems = 8,
                columns = 2
            ) { artist ->
                val thumbnail = remember(artist.id) {
                    artist.images.maxByOrNull { it.width ?: 0 }?.url
                        ?: artist.images.firstOrNull()?.url
                }
                SpotifyQuickGridCell(
                    title = artist.name,
                    imageUrl = thumbnail,
                    onClick = { onArtistClick(artist) },
                    isArtist = true
                )
            }
        }
    }
}

@Composable
private fun <T> SpotifyQuickGrid(
    items: List<T>,
    maxItems: Int = 8,
    columns: Int = 2,
    itemContent: @Composable (T) -> Unit
) {
    val displayItems = items.take(maxItems)
    if (displayItems.isEmpty()) return
    
    val rows = displayItems.chunked(columns)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        itemContent(item)
                    }
                }
                val emptyCells = columns - rowItems.size
                repeat(emptyCells) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SpotifyQuickGridCell(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    isArtist: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .yumaClickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                // Если артист - круг, если альбом - скругляем только левые углы под форму плашки
                .clip(if (isArtist) CircleShape else RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
