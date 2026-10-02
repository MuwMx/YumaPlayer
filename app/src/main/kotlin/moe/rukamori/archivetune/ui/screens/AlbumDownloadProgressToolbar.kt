/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads

@Composable
fun AlbumDownloadProgressToolbar(
    downloadUiState: AlbumDownloadUiState,
    albumWithSongs: AlbumWithSongs?,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val currentAlbumWithSongs = albumWithSongs
    val currentDownloadState = downloadUiState.downloadState
    val showDownloadProgressToolbar =
        currentAlbumWithSongs != null &&
            currentDownloadState is HeaderDownloadState.Partial &&
            !downloadUiState.dismissed
    AnimatedVisibility(
        visible = showDownloadProgressToolbar,
        modifier = modifier,
    ) {
        if (currentAlbumWithSongs != null && currentDownloadState is HeaderDownloadState.Partial) {
            val songIds =
                remember(currentAlbumWithSongs) {
                    currentAlbumWithSongs.songs.map { it.id }
                }
            DownloadProgressFloatingToolbar(
                state =
                    DownloadProgressToolbarState(
                        progress = currentDownloadState.progress,
                        paused = downloadUiState.downloadsPaused,
                        canPause = hasActiveDownloads(songIds, downloadUiState.downloads),
                    ),
                onPauseResume = {
                    if (downloadUiState.downloadsPaused) {
                        sendResumeDownloads(context, songIds)
                    } else {
                        sendPauseDownloads(context, songIds)
                    }
                    downloadUiState.downloadsPaused = !downloadUiState.downloadsPaused
                },
                onDismiss = {
                    downloadUiState.downloadsPaused = false
                    downloadUiState.dismissed = true
                },
            )
        }
    }
}
