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
 * SPOTIFY origin row (SpotifyTrack → SpotifyTrackListItem). Active state
 * resolves through [isResolvedAs] against the full MediaMetadata — a
 * resolved Spotify placeholder plays as a YouTube id, so a raw id
 * comparison would silently drop the playing-row highlight.
 *
 * PARITY WITH OTHER ORIGINS (swipe + per-song menu): every other unified
 * row offers the global swipe-to-play-next/add-to-queue gesture and a
 * 3-dot song menu; the Spotify row was the only one without both (the
 * legacy SpotifyPlaylistScreen had neither). The queue actions wrap the
 * row in [spotifyAwareQueue] so a swiped track resolves lazily exactly
 * like a tapped one, and the menu reuses the metadata-menu machinery
 * (like / add-to-playlist / play-next / add-to-queue) other rows show
 * for placeholder songs.
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
    onShowMenu: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.Spotify,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SpotifySwipeToSongBox(track = track) {
            SpotifyTrackListItem(
                track = track,
                isActive = isActive,
                isPlaying = isPlaying,
                isLiked = isLiked,
                onToggleLike = onToggleLike,
                trailingContent = {
                    SpotifyRowMenuButton(onClick = { onShowMenu(track) })
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onClick),
            )
        }
    }
}
