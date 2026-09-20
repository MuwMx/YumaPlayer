/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSortDescendingKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PlaylistSortTypeKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.db.entities.Playlist

internal const val KEY_EMPTY_PLAYLISTS_GRID = "empty_playlists_grid"
internal const val KEY_EMPTY_PLAYLISTS = "empty_playlists"

internal const val CONTENT_TYPE_PLAYLIST_GRID = "playlist_grid"
internal const val CONTENT_TYPE_PLAYLIST_LIST = "playlist_list"

val LibraryPlaylistSortTypeKey = PlaylistSortTypeKey
val LibraryPlaylistSortDescendingKey = PlaylistSortDescendingKey
val LibraryPlaylistEditLockKey = PlaylistEditLockKey
val LibraryPlaylistPureBlackKey = PureBlackKey

@Immutable
data class LibraryPlaylistsParams(
    val filterContent: (@Composable () -> Unit)? = null,
    val selectedTagIds: Set<String> = emptySet(),
)

@Immutable
data class LibraryPlaylistsUiState(
    val list: List<Playlist> = emptyList(),
    val isRefreshing: Boolean = false,
    val sortType: PlaylistSortType = PlaylistSortType.CUSTOM,
    val sortDescending: Boolean = true,
) {
    val playlists: List<Playlist> get() = list
}

@Immutable
data class LibraryPlaylistsEvents(
    val onRefresh: () -> Unit = {},
    val onSort: (PlaylistSortType) -> Unit = {},
    val onSortDescendingChange: (Boolean) -> Unit = {},
    val onPlay: (Playlist) -> Unit = {},
    val onOpen: (Playlist) -> Unit = {},
    val onMenu: (Playlist) -> Unit = {},
    val onReorderCommit: (List<Playlist>) -> Unit = {},
)

typealias LibraryPlaylistsActions = LibraryPlaylistsEvents
