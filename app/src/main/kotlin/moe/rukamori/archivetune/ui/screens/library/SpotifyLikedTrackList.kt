/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.spotifyLikedTrackList(
    isLoading: Boolean,
    tracksIsEmpty: Boolean,
    error: String?,
    filteredTracks: List<SpotifyTrack>,
    isQueryBlank: Boolean,
    mediaMetadata: MediaMetadata?,
    resolvingTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (track: SpotifyTrack, index: Int, isResolved: Boolean) -> Unit,
) {
    if (isLoading && tracksIsEmpty) {
        item(key = "loading") {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator()
            }
        }
    }

    error?.let { errorMessage ->
        item(key = "error") {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    if (!isLoading && error == null && filteredTracks.isEmpty()) {
        item(key = "empty") {
            LibraryEmptyState(
                iconRes = R.drawable.favorite,
                title =
                    stringResource(
                        if (isQueryBlank) {
                            R.string.spotify_no_tracks
                        } else {
                            R.string.ai_model_no_results
                        },
                    ),
                modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 24.dp),
            )
        }
    }

    itemsIndexed(
        items = filteredTracks,
        key = { index, track -> "spotify_track_${track.id}_$index" },
        contentType = { _, _ -> "spotify_track" },
    ) { index, track ->
        val trackIsActive =
            remember(track, mediaMetadata) {
                track.isResolvedAs(mediaMetadata)
            }
        val trackIsResolving = resolvingTrackId == track.id

        SpotifyTrackListItem(
            track = track,
            isActive = trackIsActive || trackIsResolving,
            isPlaying = isPlaying && !trackIsResolving,
            trailingContent = {
                if (trackIsResolving) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = resolvingTrackId == null || trackIsActive) {
                        onTrackClick(track, index, trackIsActive)
                    },
        )
    }
}

private fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
            mediaMetadata.duration <= 0 ||
            abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
            mediaMetadata.artists.isEmpty() ||
            artists.any { spotifyArtist ->
                mediaMetadata.artists.any { artist ->
                    spotifyArtist.name.equals(artist.name, ignoreCase = true)
                }
            }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}
