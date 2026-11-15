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
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
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
 *  - selecting: the wrapped (unfiltered) list (selection mutates wrappers),
 *  - searching: the FILTERED list (legacy Online/Top/Cache render their
 *    filtered view while searching — the shipped online flip regressed this;
 *    now fixed).
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
        when {
            searchState.isSearching -> searchState.filteredItems.map { it.item }
            searchState.selection -> searchState.wrappedItems.map { it.item }
            else -> mutableTracks.map { it.item }
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
 * The hero the screen renders: hidden while searching (legacy Top/Auto/Cache
 * gate their heroes on !isSearching), and the Top source's localized
 * "My Top N" title resolved here (sources hold no string resources).
 */
@Composable
internal fun rememberPlaylistDetailHeader(
    uiState: moe.rukamori.archivetune.ui.screens.playlist.detail.PlaylistDetailUiState,
    viewModel: PlaylistDetailViewModel,
    isSearching: Boolean,
): moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo? {
    // Legacy gates the hero on !isSearching (Top line 279, Online line 147,
    // Auto line ~200): searching collapses the list to rows only.
    if (isSearching) return null
    val header = uiState.header ?: return null
    val topSource = viewModel.source as? moe.rukamori.archivetune.playlist.source.TopPlaylistSource
    if (topSource != null) {
        // TOP HERO TITLE — stringResource(R.string.my_top) + " N", exactly the
        // legacy TopPlaylistScreen.kt:194 shape. Pinned: do not lose this in a
        // later refactor.
        val myTop = androidx.compose.ui.res.stringResource(moe.rukamori.archivetune.R.string.my_top)
        return header.copy(title = "$myTop ${topSource.maxSize}")
    }
    val autoSource = viewModel.source as? moe.rukamori.archivetune.playlist.source.AutoPlaylistSource
    if (autoSource != null) {
        // AUTO HERO TITLE — "Liked" / "Offline", the localized labels the
        // legacy screen picks (AutoPlaylistScreen.kt:91-92). Pinned.
        val label =
            androidx.compose.ui.res.stringResource(
                if (autoSource.playlistName == "liked") {
                    moe.rukamori.archivetune.R.string.liked
                } else {
                    moe.rukamori.archivetune.R.string.offline
                },
            )
        return header.copy(title = label)
    }
    if (viewModel.source is moe.rukamori.archivetune.playlist.source.CachePlaylistSource) {
        // CACHE HERO TITLE — the localized "Cached playlist" label
        // (CachePlaylistScreen.kt:290, 486, 657). Pinned.
        val label = androidx.compose.ui.res.stringResource(moe.rukamori.archivetune.R.string.cached_playlist)
        return header.copy(title = label)
    }
    return header
}

/**
 * CACHE PLAYLIST SLOT — per-song cache removal for the unified screen's
 * top-bar delete and selection-menu branches (null on other routes). The
 * cache source owns the removal (removeSongResources) exactly like legacy
 * CachePlaylistViewModel.removeSongFromCache.
 */
internal fun onPlaylistDetailRemoveSongFromCache(
    viewModel: PlaylistDetailViewModel,
): ((songId: String) -> Unit)? {
    val cacheSource = viewModel.source as? moe.rukamori.archivetune.playlist.source.CachePlaylistSource
    return cacheSource?.let { source -> { songId -> source.removeSongFromCache(songId) } }
}

/**
 * Long-press behavior: legacy semantics (Local Wiring.kt:565-568, Online
 * Actions.kt:204-211) — enter selection and select exactly the pressed song,
 * clearing any prior selection.
 */
internal fun onPlaylistDetailRowLongClick(
    searchState: moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder<PlaylistTrack>,
    track: PlaylistTrack,
    onHaptic: () -> Unit,
) {
    onHaptic()
    searchState.wrappedItems.forEach { it.isSelected = false }
    searchState.filteredItems
        .firstOrNull { it.item === track }
        ?.let { it.isSelected = true }
    searchState.selection = true
}

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
