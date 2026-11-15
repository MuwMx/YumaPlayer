/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.menu.spotify.SpotifySongMenuContent
import moe.rukamori.archivetune.ui.menu.spotify.SpotifySongMenuHeader

/**
 * SPOTIFY SONG MENU — the per-song 3-dot menu for Spotify playlist rows,
 * section-for-section parity with [YouTubeSongMenu]: header (artwork,
 * title/artist and a trailing like heart pinned to
 * [LikeSource.SPOTIFY]), action grid (start radio / play next / add to
 * queue / add to playlist), library section, and artist section.
 *
 * A SpotifyTrack plays as a PLACEHOLDER id ("spotify:<id>") the playback
 * layer resolves to a YouTube stream lazily
 * (MusicServicePlayback.resolvePlaybackDataSpec). Menu consequences:
 *  - radio / add-to-library resolve the placeholder FIRST and use the
 *    resolved YouTube id (the operations take real video ids);
 *  - the download section is dropped — DownloadUtil has no placeholder
 *    resolution, so a download entry would only ever fail;
 *  - share links the Spotify web track page (open.spotify.com), the
 *    resource the id actually names.
 *
 * [SpotifyPlaceholders.register] seeds the resolution map here because the
 * queue-time resolver (SpotifyPlaceholders.resolveUncached) only knows a
 * placeholder by that map or a pre-existing database row — a Spotify
 * playlist track the user never tapped has neither.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpotifySongMenu(
    track: SpotifyTrack,
    navController: NavController,
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

    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val librarySong by database.song(song.id).collectAsState(initial = null)

    SpotifySongMenuHeader(
        song = song,
        librarySong = librarySong,
    )

    Spacer(modifier = Modifier.height(16.dp))

    SpotifySongMenuContent(
        song = song,
        librarySong = librarySong,
        navController = navController,
        onDismiss = onDismiss,
    )
}
