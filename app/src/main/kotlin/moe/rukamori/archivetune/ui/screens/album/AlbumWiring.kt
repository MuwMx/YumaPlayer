/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.album

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.screens.album.AlbumUiState

@Immutable
data class AlbumScreenUiState(
    val albumWithSongs: AlbumWithSongs? = null,
    val uiState: AlbumUiState = AlbumUiState.Loading,
    val otherVersions: List<AlbumItem> = emptyList(),
)
@Composable
fun rememberAlbumUiState(
    albumWithSongs: AlbumWithSongs?,
    uiState: AlbumUiState,
    otherVersions: List<AlbumItem>,
): AlbumScreenUiState {
    return remember(albumWithSongs, uiState, otherVersions) {
        AlbumScreenUiState(
            albumWithSongs = albumWithSongs,
            uiState = uiState,
            otherVersions = otherVersions,
        )
    }
}
@Stable
class AlbumSelectionState(
    selectionState: MutableState<Boolean>,
    val wrappedSongs: SnapshotStateList<WrappedSong>,
) {
    var selection by selectionState

    val selectedCount: Int
        get() = wrappedSongs.count { it.isSelected }

    fun toggleSelectAll() {
        val count = selectedCount
        if (count == wrappedSongs.size) {
            wrappedSongs.forEach { it.isSelected = false }
        } else {
            wrappedSongs.forEach { it.isSelected = true }
        }
    }

    fun clearSelection() {
        selection = false
    }
}
@Composable
fun rememberAlbumSelectionState(
    albumWithSongs: AlbumWithSongs?,
    hideExplicit: Boolean,
): AlbumSelectionState {
    val wrappedSongs =
        remember(albumWithSongs, hideExplicit) {
            val filteredSongs =
                if (hideExplicit) {
                    albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
                } else {
                    albumWithSongs?.songs ?: emptyList()
                }
            filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    val selectionState = remember { mutableStateOf(false) }

    if (selectionState.value) {
        BackHandler {
            selectionState.value = false
        }
    }

    return remember(wrappedSongs) {
        AlbumSelectionState(
            selectionState = selectionState,
            wrappedSongs = wrappedSongs,
        )
    }
}
@Stable
class AlbumDownloadUiState(
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
fun rememberAlbumDownloadUiState(): AlbumDownloadUiState {
    val downloads = remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    val downloadState = remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    val downloadsPaused = remember { mutableStateOf(false) }
    val dismissed = remember { mutableStateOf(true) }
    return remember {
        AlbumDownloadUiState(
            downloadsState = downloads,
            downloadStateState = downloadState,
            downloadsPausedState = downloadsPaused,
            dismissedState = dismissed,
        )
    }
}
@Composable
fun AlbumDownloadSyncEffect(
    albumWithSongs: AlbumWithSongs?,
    downloadUtil: DownloadUtil,
    onDownloadsChange: (Map<String, Download>) -> Unit,
    onDownloadStateChange: (HeaderDownloadState) -> Unit,
) {
    LaunchedEffect(albumWithSongs) {
        val songIds = albumWithSongs?.songs?.map { it.id }.orEmpty()
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
fun AlbumDownloadPausedEffect(
    downloadState: HeaderDownloadState,
    onResetPaused: () -> Unit,
) {
    LaunchedEffect(downloadState) {
        if (downloadState !is HeaderDownloadState.Partial) {
            onResetPaused()
        }
    }
}
