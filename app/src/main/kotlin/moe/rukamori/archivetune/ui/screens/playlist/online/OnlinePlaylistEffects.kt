/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.online

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadPausedEffect
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadSyncEffect
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLoadMoreEffect

@Composable
fun OnlinePlaylistEffects(
    searchState: OnlinePlaylistSearchState,
    downloadUiState: OnlinePlaylistDownloadUiState,
    songs: List<SongItem>,
    downloadUtil: DownloadUtil,
    lazyListState: LazyListState,
    onLoadMore: () -> Unit,
) {
    OnlinePlaylistSearchFocusEffect(
        isSearching = searchState.isSearching,
        focusRequester = searchState.focusRequester,
    )
    PlaylistDownloadSyncEffect(
        songIds = songs.map { it.id },
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadUiState.downloads = it },
        onDownloadStateChange = { downloadUiState.downloadState = it },
    )
    PlaylistDownloadPausedEffect(
        downloadState = downloadUiState.downloadState,
        onResetPaused = { downloadUiState.downloadsPaused = false },
    )
    PlaylistLoadMoreEffect(
        lazyListState = lazyListState,
        itemsCount = songs.size,
        onLoadMore = onLoadMore,
    )
}
