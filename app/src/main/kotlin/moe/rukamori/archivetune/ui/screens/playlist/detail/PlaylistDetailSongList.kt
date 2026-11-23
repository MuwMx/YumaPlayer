/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playlist.source.AutoPlaylistSource
import moe.rukamori.archivetune.playlist.source.CachePlaylistSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistBadgeStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * The list BODY below the hero/period/sort slot dispatch: empty state vs
 * [playlistDetailSongs], the paginated loading-more footer and the local
 * "You might like" suggestions footer — extracted from PlaylistDetailScreen
 * to keep it under the 250-line rule. [emptyTexts] resolves via
 * [rememberPlaylistDetailEmptyTexts] INSIDE the composable Screen, never
 * here (LazyListScope blocks are not composition).
 */
internal fun LazyListScope.playlistDetailListBody(
    viewModel: PlaylistDetailViewModel,
    uiState: PlaylistDetailUiState,
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
    header: moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo?,
    emptyTexts: PlaylistDetailEmptyTexts?,
    visibleTracks: List<PlaylistTrack>,
    selectedTracks: Set<PlaylistTrack>,
    lazyListState: LazyListState,
    reorderableState: ReorderableLazyListState,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    locked: Boolean,
    swipeToSongEnabled: Boolean,
    hideExplicit: Boolean,
    downloadHolder: PlaylistDownloadStateHolder,
    badgeHolder: PlaylistBadgeStateHolder?,
    onShowMenu: (PlaylistTrack) -> Unit,
    haptics: YumaHaptics,
    showLocalSuggestions: Boolean,
) {
    // ONE HEART RULE (user spec 2026-11-15): the trailing like button is on
    // every playlist's rows EXCEPT the Liked Songs auto playlist, where it
    // would duplicate the list itself. SongPlaylistOriginRow also falls back
    // to its subtitle badge when the button is off.
    val showLikeButton =
        viewModel.source !is AutoPlaylistSource || viewModel.source.playlistName != "liked"

    if (visibleTracks.isEmpty() && uiState.error == null) {
        playlistDetailEmptyItem(
            searchState.isSearching,
            emptyTexts?.empty,
            emptyTexts?.searchEmpty,
        )
    } else {
        playlistDetailSongs(
            tracks = visibleTracks,
            selectedTracks = selectedTracks,
            capabilities = uiState.capabilities,
            reorderableState = reorderableState,
            isScrollInProgress = lazyListState.isScrollInProgress,
            mediaMetadataId = mediaMetadata?.id,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            viewCounts = uiState.viewCounts,
            sortType = uiState.sortInfo?.sortType ?: PlaylistSongSortType.CUSTOM,
            selection = searchState.selection,
            isSearching = searchState.isSearching,
            locked = locked,
            swipeToSongEnabled = swipeToSongEnabled,
            showTrackNumbers = uiState.capabilities.showsTrackNumbers,
            downloads = downloadHolder.state.value.downloads,
            badges = badgeHolder,
            showLikeButton = showLikeButton,
            hideExplicit = hideExplicit,
            // LOCAL PLAYLIST SLOT — SongMenu remove-from-playlist
            // + like-source hint key on the remote browseId.
            playlistBrowseId = uiState.browseId,
            onSongClick = { index, track ->
                onPlaylistDetailRowClick(
                    viewModel, searchState, playerConnection, mediaMetadata,
                    uiState.tracks, visibleTracks, header?.title.orEmpty(), track, index,
                )
            },
            onSongLongClick = { _, track ->
                onPlaylistDetailRowLongClick(searchState, track, haptics::longPress)
            },
            onSwipeDelete = viewModel::removeSong,
            onShowMenu = onShowMenu,
        )
    }

    if (uiState.capabilities.paginated && uiState.hasContinuation && uiState.isLoadingMore) {
        playlistDetailLoadingMoreItem()
    }

    // LOCAL PLAYLIST SLOT — "You might like" footer (legacy
    // LocalPlaylistScreen.kt:251-257) — only while no selection/search.
    localPlaylistSuggestionsItem(
        showLocalSuggestions && !searchState.selection && !searchState.isSearching)
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
    val cacheSource = viewModel.source as? CachePlaylistSource
    return cacheSource?.let { source -> { songId -> source.removeSongFromCache(songId) } }
}

/**
 * Long-press behavior: legacy semantics (Local Wiring.kt:565-568, Online
 * Actions.kt:204-211) — enter selection and select exactly the pressed song,
 * clearing any prior selection.
 */
internal fun onPlaylistDetailRowLongClick(
    searchState: PlaylistSearchStateHolder<PlaylistTrack>,
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
