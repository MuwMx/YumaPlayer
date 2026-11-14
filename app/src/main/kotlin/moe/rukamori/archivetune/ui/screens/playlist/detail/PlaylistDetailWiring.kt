/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.screens.playlist.toPlaylistPlaybackEndpoint
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Reorderable state for the unified list — a faithful generalization of
 * rememberLocalPlaylistReorderableState (LocalPlaylistWiring.kt:771-792):
 * header offsets are subtracted so the callback receives track indexes.
 */
@Composable
internal fun rememberPlaylistDetailReorderableState(
    lazyListState: LazyListState,
    headerItems: Int,
    mutableTracks: SnapshotStateList<ItemWrapper<PlaylistTrack>>,
    dragInfo: Pair<Int, Int>?,
    onDragInfoChange: (Pair<Int, Int>?) -> Unit,
): ReorderableLazyListState =
    rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            // Legacy shape (LocalPlaylistWiring.kt:783-788): keep the FIRST from
            // index across the whole drag so the commit is the full drag path,
            // not a chain of one-slot moves.
            val newDragInfo =
                if (dragInfo == null) {
                    (from.index - headerItems) to (to.index - headerItems)
                } else {
                    dragInfo.first to (to.index - headerItems)
                }
            onDragInfoChange(newDragInfo)
            mutableTracks.move(from.index - headerItems, to.index - headerItems)
        }
    }

/**
 * Commit effect for drags — generalization of LocalPlaylistEffects'
 * drag-commit branch: when a drag ends (isDragging false) and dragInfo is
 * set, invoke [onReorderCommit] once with the recorded from/to indexes and
 * clear the drag info.
 */
@Composable
internal fun PlaylistDetailReorderCommitEffect(
    isDragging: Boolean,
    dragInfo: Pair<Int, Int>?,
    onReorderCommit: (from: Int, to: Int) -> Unit,
    onResetDragInfo: () -> Unit,
) {
    LaunchedEffect(isDragging) {
        if (!isDragging && dragInfo != null) {
            onReorderCommit(dragInfo.first, dragInfo.second)
            onResetDragInfo()
        }
    }
}

/**
 * Wraps [tracks] in a live snapshot list that stays in sync with source
 * emissions while still allowing reorder-time optimistic mutation (the
 * Local screen's mutableSongs pattern generalized over PlaylistTrack).
 */
@Composable
internal fun rememberPlaylistDetailTracks(tracks: List<PlaylistTrack>): SnapshotStateList<ItemWrapper<PlaylistTrack>> {
    val mutableTracks = remember { mutableStateListOf<ItemWrapper<PlaylistTrack>>() }
    LaunchedEffect(tracks) {
        mutableTracks.apply {
            clear()
            tracks.forEach { add(ItemWrapper(it)) }
        }
    }
    return mutableTracks
}

/**
 * What the list renders and which rows are selected:
 *  - idle: the optimistic mutable list (drag moves are visible),
 *  - selecting/searching: the wrapped (unfiltered) list.
 * Selection is identity-based (wrapper.isSelected), never positional against
 * a filtered view — the failure mode the legacy Online screen avoided by
 * passing wrappers everywhere.
 */
data class PlaylistDetailRenderState(
    val visibleTracks: List<PlaylistTrack>,
    val selectedTracks: Set<PlaylistTrack>,
)

@Composable
internal fun rememberPlaylistDetailRenderState(
    tracks: List<PlaylistTrack>,
    mutableTracks: SnapshotStateList<ItemWrapper<PlaylistTrack>>,
    searchState: moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder<PlaylistTrack>,
): PlaylistDetailRenderState {
    val visible =
        if (searchState.selection || searchState.isSearching) {
            searchState.wrappedItems.map { it.item }
        } else {
            mutableTracks.map { it.item }
        }
    val selected =
        searchState.wrappedItems
            .asSequence()
            .filter { it.isSelected }
            .map { it.item }
            .toSet()
    return PlaylistDetailRenderState(visible, selected)
}

/**
 * Emits true while the user is actively dragging (distinct), used to freeze
 * swipe-dismiss during drags on reorderable sources.
 */
@Composable
internal fun rememberIsAnyDragging(state: ReorderableLazyListState): Boolean =
    state.isAnyItemDragging

/**
 * The screen's side-effect block, extracted from PlaylistDetailScreen to
 * keep it under the 250-line rule: download sync, paginated load-more and
 * the local reorder commit.
 */
@Composable
internal fun PlaylistDetailEffects(
    downloadHolder: moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder,
    downloadUtil: moe.rukamori.archivetune.download.DownloadUtil,
    tracks: List<PlaylistTrack>,
    capabilities: moe.rukamori.archivetune.playlist.source.PlaylistCapabilities,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    reorderableState: ReorderableLazyListState,
    dragInfo: Pair<Int, Int>?,
    onResetDragInfo: () -> Unit,
    viewModel: PlaylistDetailViewModel,
) {
    moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadSyncEffect(
        songIds = tracks.map { it.id },
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadHolder.downloads = it },
        onDownloadStateChange = { downloadHolder.downloadState = it },
    )
    if (capabilities.paginated) {
        moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLoadMoreEffect(
            lazyListState = lazyListState,
            itemsCount = tracks.size,
            onLoadMore = viewModel::loadMore,
        )
    }
    PlaylistDetailReorderCommitEffect(
        isDragging = reorderableState.isAnyItemDragging,
        dragInfo = dragInfo,
        onReorderCommit = viewModel::reorder,
        onResetDragInfo = onResetDragInfo,
    )
}
