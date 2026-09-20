/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumFilterKey
import moe.rukamori.archivetune.constants.AlbumSortDescendingKey
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.constants.AlbumSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.entities.Album

internal const val KEY_FEATURED_ALBUM_CARD = "featured_album_card"
internal const val KEY_EMPTY_ALBUMS_GRID = "empty_albums_grid"
internal const val KEY_EMPTY_ALBUMS_LIST = "empty_albums_list"

internal const val CONTENT_TYPE_ALBUM_GRID_ITEM = "album_grid_item"
internal const val CONTENT_TYPE_ALBUM_LIST_ITEM = "album_list_item"

val LibraryAlbumFilterKey = AlbumFilterKey
val LibraryAlbumSortTypeKey = AlbumSortTypeKey
val LibraryAlbumSortDescendingKey = AlbumSortDescendingKey
val LibraryAlbumHideExplicitKey = HideExplicitKey
val LibraryAlbumYtmSyncKey = YtmSyncKey

@Immutable
data class LibraryAlbumsUiState(
    val albums: List<Album> = emptyList(),
    val isRefreshing: Boolean = false,
    val filter: AlbumFilter = AlbumFilter.LIKED,
    val sortType: AlbumSortType = AlbumSortType.CREATE_DATE,
    val sortDescending: Boolean = true,
    val isGridView: Boolean = true,
)

@Immutable
data class LibraryAlbumsActions(
    val onRefresh: () -> Unit = {},
    val onSortTypeChange: (AlbumSortType) -> Unit = {},
    val onSortDescendingChange: (Boolean) -> Unit = {},
    val onFilterChange: (AlbumFilter) -> Unit = {},
    val onViewModeChange: (Boolean) -> Unit = {},
    val onAlbumClick: (Album) -> Unit = {},
    val onAlbumLongClick: (Album) -> Unit = {},
    val onPlayClick: (Album) -> Unit = {},
)
