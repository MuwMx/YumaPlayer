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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.playlist.spotify.isResolvedAs
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLikeButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.screens.playlist.shared.toggleSpotifyTrackLike

/**
 * SPOTIFY origin row (SpotifyTrack → SpotifyTrackListItem). Active state
 * resolves through [isResolvedAs] against the full MediaMetadata — a
 * resolved Spotify placeholder plays as a YouTube id, so a raw id
 * comparison would silently drop the playing-row highlight.
 *
 * PARITY WITH OTHER ORIGINS (user spec 2026-11-15): every unified row
 * offers the global swipe-to-play-next/add-to-queue gesture, a trailing
 * like button left of the 3-dot menu (heart keyed on SPOTIFY-side like
 * state — likedSpotify — not the generic app like), and a per-song menu
 * matching the YouTube menu's shape (header + action grid + library +
 * artist; see SpotifySongMenu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpotifyPlaylistOriginRow(
    track: SpotifyTrack,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    showLikeButton: Boolean = true,
    isLiked: Boolean = false,
    onShowMenu: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val placeholder = remember(track) { track.toPlaceholderMetadata() }

    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.Spotify,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SpotifySwipeToSongBox(
            track = track,
        ) {
            SpotifyTrackListItem(
                track = track,
                isActive = isActive,
                isPlaying = isPlaying,
                trailingContent = {
                    if (showLikeButton && placeholder != null) {
                        PlaylistLikeButton(
                            isLiked = isLiked,
                            onToggle = {
                                toggleSpotifyTrackLike(
                                    placeholder = placeholder,
                                    database = database,
                                    syncUtils = syncUtils,
                                    coroutineScope = coroutineScope,
                                )
                            },
                        )
                    }
                    SpotifyRowMenuButton(
                        onClick = onShowMenu,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onClick),
            )
        }
    }
}
