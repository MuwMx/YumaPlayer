/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.online

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadPausedEffect
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadProgressToolbar
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistDownloadStateHolder

/**
 * Online playlist download state, now an alias over the shared
 * [PlaylistDownloadStateHolder]. Online defaults the toolbar to dismissed and
 * re-shows it only when the user starts a download.
 */
typealias OnlinePlaylistDownloadUiState = PlaylistDownloadStateHolder

@Composable
fun rememberOnlinePlaylistDownloadUiState(): OnlinePlaylistDownloadUiState =
    rememberPlaylistDownloadStateHolder(initialDismissed = true)

@Composable
fun OnlinePlaylistDownloadPausedEffect(
    downloadState: moe.rukamori.archivetune.ui.utils.HeaderDownloadState,
    onResetPaused: () -> Unit,
) = PlaylistDownloadPausedEffect(downloadState, onResetPaused)

@Composable
fun OnlinePlaylistDownloadProgressToolbar(
    downloadUiState: OnlinePlaylistDownloadUiState,
    songs: List<SongItem>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val songIds = androidx.compose.runtime.remember(songs) { songs.map { it.id } }
    PlaylistDownloadProgressToolbar(
        downloadState = downloadUiState,
        songIds = songIds,
        context = context,
        modifier = modifier,
    )
}
