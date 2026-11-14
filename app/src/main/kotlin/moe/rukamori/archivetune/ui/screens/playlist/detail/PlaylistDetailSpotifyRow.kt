/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow

/**
 * SPOTIFY origin row (SpotifyTrack → SpotifyTrackListItem, no per-song
 * menu in the legacy screen either). Mirrors SpotifyPlaylistTrackRow
 * (SpotifyPlaylistTrackRows.kt:24-46).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotifyPlaylistOriginRow(
    track: SpotifyTrack,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.Spotify,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SpotifyTrackListItem(
            track = track,
            isActive = track.id == mediaMetadataId,
            isPlaying = isPlaying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick),
        )
    }
}
