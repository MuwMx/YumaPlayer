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
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.main.LocalDownloadUtil
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadSyncEffect
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads

@Stable
class AutoPlaylistDownloadUiState(
    downloadsState: MutableState<Map<String, Download>>,
    downloadStateState: MutableState<HeaderDownloadState>,
    globalDownloadStateState: MutableState<HeaderDownloadState>,
    globalPausedState: MutableState<Boolean>,
    showRemoveDownloadDialogState: MutableState<Boolean>,
) {
    var downloads by downloadsState
    var downloadState by downloadStateState
    var globalDownloadState by globalDownloadStateState
    var globalPaused by globalPausedState
    var showRemoveDownloadDialog by showRemoveDownloadDialogState
}

@Composable
fun rememberAutoPlaylistDownloadState(
    songs: List<Song>,
    downloadUtil: DownloadUtil = LocalDownloadUtil.current,
): AutoPlaylistDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val globalDownloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val globalPaused = remember { mutableStateOf(false) }
    val showRemoveDialog = remember { mutableStateOf(false) }

    // Shared sync effect (was the per-screen AutoPlaylistDownloadSyncEffect);
    // Auto rows key downloads by Song.song.id and also track the header's
    // global paused state (upstream f96ca418 sampling now lives in the shared effect).
    PlaylistDownloadSyncEffect(
        songIds = remember(songs) { songs.map { it.song.id } },
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloads.value = it },
        onDownloadStateChange = { downloadState.value = it },
        onGlobalStateChange = { state, paused ->
            globalDownloadState.value = state
            globalPaused.value = paused
        },
    )

    return remember {
        AutoPlaylistDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            globalDownloadStateState = globalDownloadState,
            globalPausedState = globalPaused,
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
                Text(text = stringResource(R.string.remove_download))
            }
        },
    )
}
