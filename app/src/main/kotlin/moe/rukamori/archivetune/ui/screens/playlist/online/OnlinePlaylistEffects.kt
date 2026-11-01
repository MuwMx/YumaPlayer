/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.download.DownloadUtil

@Composable
fun OnlinePlaylistContinuationEffect(
    lazyListState: LazyListState,
    songsCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songsCount >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songsCount - 5
            ) {
                onLoadMore()
            }
        }
    }
}

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
    OnlinePlaylistDownloadSyncEffect(
        songs = songs,
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadUiState.downloads = it },
        onDownloadStateChange = { downloadUiState.downloadState = it },
    )
    OnlinePlaylistDownloadPausedEffect(
        downloadState = downloadUiState.downloadState,
        onResetPaused = { downloadUiState.downloadsPaused = false },
    )
    OnlinePlaylistContinuationEffect(
        lazyListState = lazyListState,
        songsCount = songs.size,
        onLoadMore = onLoadMore,
    )
}
