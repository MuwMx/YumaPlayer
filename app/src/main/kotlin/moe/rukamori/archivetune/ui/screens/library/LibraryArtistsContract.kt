/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.ArtistFilter
import moe.rukamori.archivetune.constants.ArtistSortType
import moe.rukamori.archivetune.db.entities.Artist

internal const val KEY_SPOTLIGHT_ROW = "spotlight_row"
internal const val KEY_SUB_HEADER = "sub_header"
internal const val KEY_EMPTY_ARTISTS = "empty_artists"
internal const val KEY_RECENT_ARTISTS_HEADER = "recent_artists_header"
internal const val KEY_RECENT_ARTISTS_ROW = "recent_artists_row"

internal const val CONTENT_TYPE_ARTIST_ITEM = "artist_item"

@Immutable
data class LibraryArtistsParams(
    val onDeselect: () -> Unit = {},
)

@Immutable
data class LibraryArtistsUiState(
    val list: List<Artist> = emptyList(),
    val isRefreshing: Boolean = false,
    val sortType: ArtistSortType = ArtistSortType.CREATE_DATE,
    val sortDescending: Boolean = true,
    val filter: ArtistFilter = ArtistFilter.LIKED,
)

@Immutable
data class LibraryArtistsEvents(
    val onRefresh: () -> Unit,
    val onSort: (ArtistSortType) -> Unit,
    val onSortDescendingChange: (Boolean) -> Unit,
    val onFilterToggle: () -> Unit = {},
    val onPlay: (Artist) -> Unit,
    val onOpen: (Artist) -> Unit,
    val onMenu: (Artist) -> Unit,
)
