/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist.spotify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.screens.playlist.isResolvedAs

@Composable
internal fun SpotifyPlaylistTrackRow(
    track: SpotifyTrack,
    isActive: Boolean,
    isResolving: Boolean,
    isPlaying: Boolean,
    canClick: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpotifyTrackListItem(
        track = track,
        isActive = isActive || isResolving,
        isPlaying = isPlaying && !isResolving,
        trailingContent = {
            if (isResolving) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = canClick) {
                    onClick()
                },
    )
}

internal fun LazyListScope.spotifyPlaylistTrackRows(
    filteredTracks: List<SpotifyTrack>,
    mediaMetadata: MediaMetadata?,
    resolvingTrackId: String?,
    isPlaying: Boolean,
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
        val trackIsResolving = resolvingTrackId == track.id

        SpotifyPlaylistTrackRow(
            track = track,
            isActive = trackIsActive,
            isResolving = trackIsResolving,
            isPlaying = isPlaying,
            canClick = resolvingTrackId == null || trackIsActive,
            onClick = {
                onTrackClick(track, index, trackIsActive)
            },
        )
    }
}
