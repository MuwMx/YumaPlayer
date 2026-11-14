/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

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
import android.content.Context
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads

/**
 * Shared download progress state for playlist screens that support downloads
 * (Local header, Online, Auto).
 *
 * Mirrors the pre-existing per-screen holders; the `dismissed` default is a
 * constructor parameter because Online and Local genuinely differ today
 * (both start dismissed; Local flips it to false when a download starts).
 * Open so the Local screen's holder can extend it with its reorder-specific
 * `mutableSongs` list.
 */
@Stable
open class PlaylistDownloadStateHolder(
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
fun rememberPlaylistDownloadStateHolder(
    initialDismissed: Boolean = true,
): PlaylistDownloadStateHolder {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(initialDismissed) }
    return remember {
        PlaylistDownloadStateHolder(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun PlaylistDownloadSyncEffect(
    songIds: List<String>,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
    onGlobalStateChange: ((state: HeaderDownloadState, paused: Boolean) -> Unit)? = null,
) {
    LaunchedEffect(songIds) {
        if (songIds.isEmpty()) {
            onDownloadsChange(emptyMap())
            onDownloadStateChange(HeaderDownloadState.None)
            onGlobalStateChange?.invoke(HeaderDownloadState.None, false)
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            downloadUtil.downloads
                .sample(350L)
                .distinctUntilChanged()
                .collect { currentDownloads ->
                    val calculatedHeaderState = headerDownloadState(songIds, currentDownloads)
                    if (onGlobalStateChange != null) {
                        val activeDownloads =
                            currentDownloads.values.filter {
                                it.state == Download.STATE_DOWNLOADING ||
                                    it.state == Download.STATE_QUEUED ||
                                    it.state == Download.STATE_RESTARTING ||
                                    it.state == Download.STATE_STOPPED
                            }
                        val (globalState, paused) =
                            if (activeDownloads.isEmpty()) {
                                HeaderDownloadState.None to false
                            } else {
                                var progressTotal = 0f
                                var hasRunning = false
                                var hasPaused = false
                                activeDownloads.forEach { download ->
                                    val progress =
                                        download.percentDownloaded
                                            .takeIf { it >= 0f }?.div(100f) ?: 0f
                                    progressTotal += progress.coerceIn(0f, 1f)
                                    if (download.state == Download.STATE_STOPPED) {
                                        hasPaused = hasPaused || download.stopReason == 1
                                    } else {
                                        hasRunning = true
                                    }
                                }
                                HeaderDownloadState.Partial(progress = progressTotal / activeDownloads.size) to
                                    (hasPaused && !hasRunning)
                            }
                        withContext(Dispatchers.Main) {
                            onDownloadsChange(currentDownloads)
                            onDownloadStateChange(calculatedHeaderState)
                            onGlobalStateChange(globalState, paused)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onDownloadsChange(currentDownloads)
                            onDownloadStateChange(calculatedHeaderState)
                        }
                    }
                }
        }
    }
}

@Composable
fun PlaylistDownloadPausedEffect(
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
fun PlaylistDownloadProgressToolbar(
    downloadState: PlaylistDownloadStateHolder,
    songIds: List<String>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val currentDownloadState = downloadState.downloadState
    val showDownloadProgressToolbar =
        currentDownloadState is HeaderDownloadState.Partial &&
            songIds.isNotEmpty() &&
            !downloadState.dismissed
    AnimatedVisibility(
        visible = showDownloadProgressToolbar,
        modifier = modifier,
    ) {
        if (currentDownloadState is HeaderDownloadState.Partial && songIds.isNotEmpty()) {
            val currentSongIds = remember(songIds) { songIds }
            DownloadProgressFloatingToolbar(
                state =
                    DownloadProgressToolbarState(
                        progress = currentDownloadState.progress,
                        paused = downloadState.downloadsPaused,
                        canPause = hasActiveDownloads(currentSongIds, downloadState.downloads),
                    ),
                onPauseResume = {
                    if (downloadState.downloadsPaused) {
                        sendResumeDownloads(context, currentSongIds)
                    } else {
                        sendPauseDownloads(context, currentSongIds)
                    }
                    downloadState.downloadsPaused = !downloadState.downloadsPaused
                },
                onDismiss = {
                    downloadState.downloadsPaused = false
                    downloadState.dismissed = true
                },
            )
        }
    }
}
