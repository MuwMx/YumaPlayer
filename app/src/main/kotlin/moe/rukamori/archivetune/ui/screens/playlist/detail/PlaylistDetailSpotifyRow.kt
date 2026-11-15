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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.screens.playlist.isResolvedAs
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow

/**
 * SPOTIFY origin row (SpotifyTrack → SpotifyTrackListItem, no per-song
 * menu in the legacy screen either). Mirrors SpotifyPlaylistTrackRows.kt
 * (legacy): active state resolves through [isResolvedAs] against the full
 * MediaMetadata — a resolved Spotify placeholder plays as a YouTube id, so a
 * raw id comparison would silently drop the playing-row highlight.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotifyPlaylistOriginRow(
    track: SpotifyTrack,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.Spotify,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SpotifyTrackListItem(
            track = track,
            isActive = isActive,
            isPlaying = isPlaying,
            isLiked = isLiked,
            onToggleLike = onToggleLike,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick),
        )
    }
}
