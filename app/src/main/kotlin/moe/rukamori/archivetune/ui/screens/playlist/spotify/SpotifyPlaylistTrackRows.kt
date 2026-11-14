/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.spotify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.isTrackLiked
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.screens.playlist.isResolvedAs

@Composable
internal fun SpotifyPlaylistTrackRow(
    track: SpotifyTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpotifyTrackListItem(
        track = track,
        isActive = isActive,
        isPlaying = isPlaying,
        isLiked = isLiked,
        onToggleLike = onToggleLike,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    )
}

internal fun LazyListScope.spotifyPlaylistTrackRows(
    filteredTracks: List<SpotifyTrack>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    likedKeys: Set<String> = emptySet(),
    onToggleLike: ((SpotifyTrack) -> Unit)? = null,
    onTrackClick: (track: SpotifyTrack, index: Int, isActive: Boolean) -> Unit,
) {
    itemsIndexed(
        items = filteredTracks,
        key = { index, track -> "spotify_track_${track.id}_$index" },
        contentType = { _, _ -> "spotify_track" },
    ) { index, track ->
        val trackIsActive =
            remember(track, mediaMetadata) {
                track.isResolvedAs(mediaMetadata)
            }
        val isLiked = remember(track, likedKeys) { likedKeys.isTrackLiked(track) }

        SpotifyPlaylistTrackRow(
            track = track,
            isActive = trackIsActive,
            isPlaying = isPlaying,
            isLiked = isLiked,
            onToggleLike = onToggleLike?.let { toggle -> { toggle(track) } },
            onClick = {
                onTrackClick(track, index, trackIsActive)
            },
        )
    }
}
