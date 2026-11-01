/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads

@Stable
class OnlinePlaylistDownloadUiState(
    downloadsState: MutableState<Map<String, Download>>,
    downloadStateState: MutableState<HeaderDownloadState>,
    downloadsPausedState: MutableState<Boolean>,
    dismissedState: MutableState<Boolean>,
) {
    var downloads by downloadsState
    var downloadState by downloadStateState
    var downloadsPaused by downloadsPausedState
    var dismissed by dismissedState
}

@Composable
fun rememberOnlinePlaylistDownloadUiState(): OnlinePlaylistDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(true) }
    return remember {
        OnlinePlaylistDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
        )
    }
}

@Composable
fun OnlinePlaylistDownloadSyncEffect(
    songs: List<SongItem>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
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
fun OnlinePlaylistDownloadPausedEffect(
    downloadState: HeaderDownloadState,
    onResetPaused: () -> Unit,
) {
    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            onResetPaused()
        }
    }
}

@Composable
fun OnlinePlaylistDownloadProgressToolbar(
    downloadUiState: OnlinePlaylistDownloadUiState,
    songs: List<SongItem>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val currentDownloadState = downloadUiState.downloadState
    val showDownloadProgressToolbar =
        currentDownloadState is HeaderDownloadState.Partial &&
            songs.isNotEmpty() &&
            !downloadUiState.dismissed
    AnimatedVisibility(
        visible = showDownloadProgressToolbar,
        modifier = modifier,
    ) {
        if (currentDownloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
            val songIds = remember(songs) { songs.map { it.id } }
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
