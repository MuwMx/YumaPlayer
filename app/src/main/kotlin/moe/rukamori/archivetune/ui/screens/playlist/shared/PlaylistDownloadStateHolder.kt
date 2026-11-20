/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Modifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.utils.DownloadProgressFloatingToolbar
import moe.rukamori.archivetune.ui.utils.DownloadProgressToolbarState
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.hasActiveDownloads
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendPauseDownloads
import moe.rukamori.archivetune.ui.utils.sendResumeDownloads
import androidx.media3.exoplayer.offline.Download
import android.content.Context

/**
 * Immutable snapshot of download progress state for playlist screens that
 * support downloads (Local header, Online, Auto).
 *
 * Part of the UDF pattern: the [PlaylistDownloadStateHolder] owns a
 * [StateFlow<PlaylistDownloadState>] and exposes read-only [state]; mutations
 * only happen via [update].
 */
@Stable
data class PlaylistDownloadState(
    val downloads: Map<String, Download> = emptyMap(),
    val downloadState: HeaderDownloadState = HeaderDownloadState.None,
    val downloadsPaused: Boolean = false,
    val dismissed: Boolean = true,
)

/**
 * UDF-compliant state holder: ONE [StateFlow] source of truth.
 *
 * Replaces the legacy open mutable-bag pattern; screens that previously
 * reached into `var` properties now collect [state] and mutate through
 * [update] (or the convenience helpers [setDismissed], [setPaused]).
 */
@Composable
fun rememberPlaylistDownloadStateHolder(): PlaylistDownloadStateHolder = remember {
    PlaylistDownloadStateHolder()
}

class PlaylistDownloadStateHolder {
    private val _state = MutableStateFlow(PlaylistDownloadState())
    val state: StateFlow<PlaylistDownloadState> = _state.asStateFlow()

    fun update(transform: (PlaylistDownloadState) -> PlaylistDownloadState) {
        _state.update(transform)
    }

    fun setDismissed(dismissed: Boolean) {
        update { it.copy(dismissed = dismissed) }
    }

    fun setPaused(paused: Boolean) {
        update { it.copy(downloadsPaused = paused) }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun PlaylistDownloadSyncEffect(
    songIds: List<String>,
    downloadUtil: DownloadUtil,
    holder: PlaylistDownloadStateHolder,
) {
    LaunchedEffect(songIds) {
        if (songIds.isEmpty()) {
            holder.update {
                PlaylistDownloadState(
                    downloads = emptyMap(),
                    downloadState = HeaderDownloadState.None,
                    downloadsPaused = false,
                )
            }
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            downloadUtil.downloads
                .sample(350L)
                .distinctUntilChanged()
                .collect { currentDownloads ->
                    val headerState = headerDownloadState(songIds, currentDownloads)
                    withContext(Dispatchers.Main) {
                        holder.update {
                            it.copy(
                                downloads = currentDownloads,
                                downloadState = headerState,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
fun PlaylistDownloadPausedEffect(
    holder: PlaylistDownloadStateHolder,
) {
    val downloadState = holder.state.collectAsStateWithLifecycle().value.downloadState
    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            holder.setPaused(false)
        }
    }
}

@Composable
fun PlaylistDownloadProgressToolbar(
    holder: PlaylistDownloadStateHolder,
    songIds: List<String>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val downloadState by holder.state.collectAsStateWithLifecycle()
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
                    holder.setPaused(!downloadState.downloadsPaused)
                },
                onDismiss = {
                    holder.setPaused(false)
                    holder.setDismissed(true)
                },
            )
        }
    }
}
