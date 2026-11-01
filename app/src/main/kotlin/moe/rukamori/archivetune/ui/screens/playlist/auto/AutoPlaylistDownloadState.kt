/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.auto

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads

@Stable
class AutoPlaylistDownloadUiState(
    downloadsState: MutableState<Map<String, Download>>,
    downloadStateState: MutableState<HeaderDownloadState>,
    val globalDownloadState: HeaderDownloadState,
    showRemoveDownloadDialogState: MutableState<Boolean>,
) {
    var downloads by downloadsState
    var downloadState by downloadStateState
    var showRemoveDownloadDialog by showRemoveDownloadDialogState
}

@Composable
fun AutoPlaylistDownloadSyncEffect(
    songs: List<Song>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(songs) {
        val songIds = songs.map { it.song.id }
        if (songIds.isEmpty()) {
            onDownloadsChange(emptyMap())
            onDownloadStateChange(HeaderDownloadState.None)
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            onDownloadsChange(currentDownloads)
            onDownloadStateChange(headerDownloadState(songIds, currentDownloads))
        }
    }
}

@Composable
fun rememberAutoPlaylistDownloadState(
    songs: List<Song>,
    downloadUtil: DownloadUtil = LocalDownloadUtil.current,
): AutoPlaylistDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val showRemoveDialog = remember { mutableStateOf(false) }

    AutoPlaylistDownloadSyncEffect(
        songs = songs,
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloads.value = it },
        onDownloadStateChange = { downloadState.value = it },
    )

    val globalDownloadState =
        remember(downloads.value) {
            val activeDownloads =
                downloads.value.values.filter {
                    it.state == Download.STATE_DOWNLOADING ||
                        it.state == Download.STATE_QUEUED ||
                        it.state == Download.STATE_RESTARTING ||
                        it.state == Download.STATE_STOPPED
                }
            if (activeDownloads.isEmpty()) {
                HeaderDownloadState.None
            } else {
                var progressTotal = 0f
                var hasRunning = false
                var hasPaused = false
                activeDownloads.forEach { download ->
                    val progress = download.percentDownloaded.takeIf { it >= 0f }?.div(100f) ?: 0f
                    progressTotal += progress.coerceIn(0f, 1f)
                    if (download.state == Download.STATE_STOPPED) {
                        hasPaused = hasPaused || download.stopReason == 1
                    } else {
                        hasRunning = true
                    }
                }
                HeaderDownloadState.Partial(
                    progress = progressTotal / activeDownloads.size,
                )
            }
        }

    return remember(globalDownloadState) {
        AutoPlaylistDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            globalDownloadState = globalDownloadState,
            showRemoveDownloadDialogState = showRemoveDialog,
        )
    }
}

@Composable
fun AutoPlaylistRemoveDownloadDialog(
    playlist: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    context: Context = LocalContext.current,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.remove_download_playlist_confirm, playlist),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    sendRemoveDownloads(
                        context = context,
                        songIds = songs.map { it.song.id },
                    )
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
