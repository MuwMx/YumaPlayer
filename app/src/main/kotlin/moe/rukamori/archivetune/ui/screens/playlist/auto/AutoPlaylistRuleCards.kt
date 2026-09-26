/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.auto

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.screens.playlist.CONTENT_TYPE_HEADER

@Composable
internal fun AutoPlaylistSortHeader(
    sortType: AutoPlaylistSongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (AutoPlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp),
    ) {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { sortType ->
                when (sortType) {
                    AutoPlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                    AutoPlaylistSongSortType.NAME -> R.string.sort_by_name
                    AutoPlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                    AutoPlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun LazyListScope.autoPlaylistRuleCards(
    sortType: AutoPlaylistSongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (AutoPlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
) {
    item(
        key = "sortHeader",
        contentType = CONTENT_TYPE_HEADER,
    ) {
        AutoPlaylistSortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
        )
    }
}
