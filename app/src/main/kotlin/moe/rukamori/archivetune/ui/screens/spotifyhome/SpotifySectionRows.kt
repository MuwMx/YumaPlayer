/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.spotifyhome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.spotify.SpotifyHomeSection
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.theme.yumaClickable

@Composable
internal fun resolveSpotifySectionTitle(section: SpotifyHomeSection): String {
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
internal fun SpotifyTrackSectionRow(
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
internal fun SpotifyArtistSectionRow(
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
internal fun SpotifyAlbumSectionRow(
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
internal fun SpotifyPlaylistSectionRow(
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
