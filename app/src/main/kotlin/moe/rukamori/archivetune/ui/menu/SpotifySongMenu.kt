/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.Timeline
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata

/**
 * SPOTIFY SONG MENU — the per-song 3-dot menu for Spotify playlist rows,
 * parity with SongMenu / YouTubeSongMenu on the other unified rows. Built
 * as a single-track wrapper over [SelectionMediaMetadataMenu].
 *
 * A SpotifyTrack plays as a PLACEHOLDER id ("spotify:track:<id>") that the
 * playback layer resolves to a YouTube stream lazily
 * (MusicServicePlayback.resolvePlaybackDataSpec). One menu consequence:
 * the download section is dropped — the download pipeline has no
 * placeholder resolution path (DownloadUtil.resolveDownloadDataSpec would
 * ask InnerTube for a "spotify:track:…" video id and fail), so a download
 * entry would only ever show an error. Everything else is placeholder-safe:
 * add-to-playlist inserts the metadata row, like pins to
 * [moe.rukamori.archivetune.constants.LikeSource.SPOTIFY] (the same pinned
 * hint the unified local playlist's selection menu uses for
 * "spotify:"-linked playlists), and add-to-queue resolves at play time.
 *
 * [SpotifyPlaceholders.register] seeds the resolution map here because the
 * queue-time resolver (SpotifyPlaceholders.resolveUncached) only knows a
 * placeholder by that map or a pre-existing database row — a Spotify
 * playlist track the user never tapped has neither.
 */
@Composable
fun SpotifySongMenu(
    track: SpotifyTrack,
    currentItems: List<Timeline.Window> = emptyList(),
    onDismiss: () -> Unit,
) {
    val song = remember(track) { track.toPlaceholderMetadata() }
    if (song == null) {
        onDismiss()
        return
    }
    // Idempotent map-put; seeds the queue-time resolver (resolveUncached
    // only knows placeholders by this map or an existing database row).
    SpotifyPlaceholders.register(listOf(song))
    SelectionMediaMetadataMenu(
        songSelection = listOf(song),
        currentItems = currentItems,
        onDismiss = onDismiss,
        clearAction = onDismiss,
        likeSourceHint = LikeSource.SPOTIFY,
    )
}
