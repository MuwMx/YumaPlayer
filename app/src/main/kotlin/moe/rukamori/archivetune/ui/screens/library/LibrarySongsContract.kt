/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.db.entities.Song

internal const val CONTENT_TYPE_SONG = "song"

val LibrarySongFilterKey = SongFilterKey
val LibrarySongSortTypeKey = SongSortTypeKey
val LibrarySongSortDescendingKey = SongSortDescendingKey
val LibraryHideExplicitKey = HideExplicitKey

@Immutable
data class LibrarySongsUiState(
    val count: Int = 0,
    val totalDurationText: String = "",
    val filterName: String = "",
    val isRefreshing: Boolean = false,
)

@Immutable
data class LibrarySongsActions(
    val onPlayAll: () -> Unit,
    val onOpen: (id: String) -> Unit,
    val onMenu: (Song) -> Unit,
    val onRetry: () -> Unit,
    val onFilter: (SongFilter) -> Unit,
    val onSortType: (SongSortType) -> Unit,
    val onSortDescending: (Boolean) -> Unit,
)

fun formatTotalDuration(totalDurationSec: Long): String {
    if (totalDurationSec <= 0L) return ""
    val days = totalDurationSec / 86400L
    var remaining = totalDurationSec % 86400L
    val hours = remaining / 3600L
    remaining %= 3600L
    val minutes = remaining / 60L
    val seconds = remaining % 60L
    return when {
        days > 0L -> "${days}d ${hours}h ${minutes}m"
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
