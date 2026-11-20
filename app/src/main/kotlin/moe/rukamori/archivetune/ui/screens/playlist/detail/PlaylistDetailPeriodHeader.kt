/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.ui.component.SortHeader

/**
 * TOP PLAYLIST SLOT — the MyTop period selector, reusing the house
 * SortHeader exactly like TopPlaylistScreen.kt:386-405. A generic-enum
 * SortHeader cannot be driven through PlaylistSortInfo (a different enum
 * space), so the period rides its own pinned slot in the wiring. Pinned so
 * the period selector never silently disappears in a route flip.
 */
@Composable
internal fun PlaylistDetailPeriodHeader(
    period: MyTopFilter,
    onPeriodChange: (MyTopFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp),
    ) {
        SortHeader(
            sortType = period,
            sortDescending = false,
            onSortTypeChange = onPeriodChange,
            onSortDescendingChange = {},
            sortTypeText = { type ->
                when (type) {
                    MyTopFilter.ALL_TIME -> R.string.all_time
                    MyTopFilter.DAY -> R.string.past_24_hours
                    MyTopFilter.WEEK -> R.string.past_week
                    MyTopFilter.MONTH -> R.string.past_month
                    MyTopFilter.YEAR -> R.string.past_year
                }
            },
            modifier = Modifier.weight(1f),
            showDescending = false,
        )
    }
}
