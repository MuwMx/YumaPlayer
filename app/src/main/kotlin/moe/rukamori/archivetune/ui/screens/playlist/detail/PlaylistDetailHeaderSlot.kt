/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.playlist.source.TopPlaylistSource

/**
 * Header slot between the hero and the song list:
 *
 *  - TOP PLAYLIST SLOT (pinned): the MyTop period chip — the top source's
 *    only control. Do not delete this branch when refactoring.
 *  - Sortable sources: the unified sort header.
 *  - Read-only sources: nothing.
 *
 * AUTO PLAYLIST SLOT (rule cards) and SPOTIFY SLOT (sync card) join here
 * when their routes flip in Phase E — keep this signature stable.
 */
internal fun LazyListScope.renderPlaylistDetailHeaderSlot(
    uiState: PlaylistDetailUiState,
    viewModel: PlaylistDetailViewModel,
) {
    val topSource = viewModel.source as? TopPlaylistSource
    if (topSource != null) {
        // TOP PLAYLIST SLOT — pinned: the MyTop period chip must survive
        // unification; do not delete this branch in later phases.
        item(key = PLAYLIST_DETAIL_KEY_SORT_HEADER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_SORT_HEADER) {
            val topPeriod by topSource.topPeriod.collectAsStateWithLifecycle()
            PlaylistDetailPeriodHeader(
                period = topPeriod,
                onPeriodChange = viewModel::setTopPeriod,
            )
        }
        return
    }
    if (!uiState.capabilities.sortable) return
    val sortInfo = uiState.sortInfo ?: return
    item(key = PLAYLIST_DETAIL_KEY_SORT_HEADER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_SORT_HEADER) {
        PlaylistDetailSortHeader(
            sortInfo = sortInfo,
            editable = uiState.capabilities.editable,
            locked = false,
            onSortTypeChange = { type -> viewModel.updateSortPreference(type, sortInfo.descending) },
            onSortDescendingChange = { descending ->
                viewModel.updateSortPreference(sortInfo.sortType, descending)
            },
            onToggleLock = {},
        )
    }
}

/**
 * How many LazyColumn items the header slot renders for [uiState] — keeps
 * the scrollbar/drag header offset in sync with the slot's own conditions
 * (legacy screens counted hero + slot: Local/Auto/Top = 2, Spotify = 1).
 */
internal fun playlistDetailHeaderSlotCount(
    uiState: PlaylistDetailUiState,
    viewModel: PlaylistDetailViewModel,
): Int {
    if (viewModel.source is TopPlaylistSource) return 1
    if (uiState.sortInfo != null && uiState.capabilities.sortable) return 1
    return 0
}
