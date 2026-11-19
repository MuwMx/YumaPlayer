/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo
import moe.rukamori.archivetune.playlist.source.PlaylistSortInfo
import moe.rukamori.archivetune.playlist.source.PlaylistTrack

internal const val PLAYLIST_DETAIL_KEY_HEADER = "header"
internal const val PLAYLIST_DETAIL_KEY_SORT_HEADER = "sort_header"
internal const val PLAYLIST_DETAIL_KEY_EMPTY = "empty"
internal const val PLAYLIST_DETAIL_KEY_SUGGESTIONS = "suggestions"
internal const val PLAYLIST_DETAIL_KEY_LOADING_MORE = "loading_more"
internal const val PLAYLIST_DETAIL_KEY_ERROR = "error"
internal const val PLAYLIST_DETAIL_KEY_SHIMMER = "shimmer"

internal const val CONTENT_TYPE_PLAYLIST_DETAIL_HEADER = "header"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_SORT_HEADER = "sort_header"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_SONG = "playlist_detail_song"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_EMPTY = "empty"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_SUGGESTIONS = "suggestions"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_LOADING_MORE = "loading_more"
internal const val CONTENT_TYPE_PLAYLIST_DETAIL_ERROR = "error"

/**
 * One UiState for every playlist source, consolidating LocalPlaylistUiState
 * and OnlinePlaylistUiState. Display fields come from the
 * PlaylistTracksSource layer ([PlaylistTrack] keeps its origin typed item in
 * [PlaylistTrack.origin]); this contract deliberately holds only UI-facing
 * state.
 */
@Immutable
data class PlaylistDetailUiState(
    val capabilities: PlaylistCapabilities = PlaylistCapabilities.ReadOnly,
    val header: PlaylistHeaderInfo? = null,
    val tracks: List<PlaylistTrack> = emptyList(),
    val sortInfo: PlaylistSortInfo? = null,
    val viewCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasContinuation: Boolean = false,
    /**
     * LOCAL PLAYLIST SLOT — live per-playlist flags the local UI keys on
     * (hero edit/delete vs like, top-bar edit vs sync, swipe-delete rights,
     * SongMenu playlistBrowseId, suggestion-seeding). Null on other routes.
     */
    val editable: Boolean? = null,
    val browseId: String? = null,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && !isLoading && error == null
}

/**
 * Consolidated actions (house pattern: @Immutable data class of callbacks,
 * see YUMA_RULES action-encapsulation section). Indices refer to positions in
 * [PlaylistDetailUiState.tracks] except where a track is passed directly.
 *
 * UNSUPPORTED actions default to `{}` and are additionally gated by
 * [PlaylistCapabilities] at the source level — a source that cannot perform
 * an operation fails its [moe.rukamori.archivetune.playlist.source.PlaylistTracksSource]
 * call instead of crashing the UI.
 */
@Immutable
data class PlaylistDetailActions(
    val onPlay: (startIndex: Int) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRadio: () -> Unit = {},
    val onMix: () -> Unit = {},
    val onSongClick: (index: Int, track: PlaylistTrack) -> Unit = { _, _ -> },
    val onSongLongClick: (index: Int, track: PlaylistTrack) -> Unit = { _, _ -> },
    val onSongMenu: (track: PlaylistTrack) -> Unit = {},
    val onSort: (PlaylistSongSortType, Boolean) -> Unit = { _, _ -> },
    val onRefresh: () -> Unit = {},
    val onReorderCommit: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onSongDelete: (index: Int) -> Unit = {},
    val onDownload: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDeletePlaylist: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onPickCover: () -> Unit = {},
    val onLockToggle: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onMenu: () -> Unit = {},
)
