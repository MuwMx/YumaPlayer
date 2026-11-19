/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import moe.rukamori.archivetune.ui.component.SwipeToSongBox
import moe.rukamori.archivetune.extensions.toMediaItem

/**
 * SPOTIFY row parity: the swipe-to-play-next / add-to-queue gesture other
 * rows get from SongListItem / YouTubeListItem's built-in SwipeToSongBox.
 * SpotifyTrackListItem has no such box, so the unified row wraps here.
 *
 * [SpotifyPlaceholders.register] seeds the resolution map BEFORE the item
 * can play: the queue-time resolver (MusicServicePlayback) falls back to
 * a database row, but a never-played Spotify track has neither — without
 * registering here a swiped track would die with "No metadata for
 * placeholder" when it reaches the front of the queue. This mirrors what
 * SpotifyPlaceholderQueue.init does for tapped playback.
 */
@Composable
internal fun SpotifySwipeToSongBox(
    track: SpotifyTrack,
    content: @Composable () -> Unit,
) {
    val placeholderMetadata = remember(track) { track.toPlaceholderMetadata() }
    if (placeholderMetadata == null) {
        content()
        return
    }
    // Idempotent map-put; mirrors SpotifyPlaceholderQueue.init.
    SpotifyPlaceholders.register(listOf(placeholderMetadata))
    SwipeToSongBox(
        mediaItem = placeholderMetadata.toMediaItem(),
        contentBackgroundColor = MaterialTheme.colorScheme.surface,
    ) {
        content()
    }
}

/**
 * The 3-dot trailing button on Spotify rows — parity with the other
 * origins' more_vert buttons; opens [SpotifySongMenu] via menuState.
 */
@Composable
internal fun SpotifyRowMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    moe.rukamori.archivetune.ui.component.IconButton(
        onClick = onClick,
        onLongClick = {},
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
