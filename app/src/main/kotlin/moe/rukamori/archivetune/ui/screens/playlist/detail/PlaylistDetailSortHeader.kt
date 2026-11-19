/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.playlist.source.PlaylistSortInfo
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.SortHeader

/**
 * The unified sort header. Rendered only when the source is sortable
 * ([sortInfo] non-null); the local lock toggle (reorder lock) rides along
 * exactly like LocalPlaylistSortHeader (LocalPlaylistListSection.kt:53-96).
 */
@Composable
internal fun PlaylistDetailSortHeader(
    sortInfo: PlaylistSortInfo,
    editable: Boolean,
    locked: Boolean,
    onSortTypeChange: (PlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp),
    ) {
        SortHeader(
            sortType = sortInfo.sortType,
            sortDescending = sortInfo.descending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { type ->
                when (type) {
                    PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                    PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                    PlaylistSongSortType.NAME -> R.string.sort_by_name
                    PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                    PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (editable && sortInfo.sortType == PlaylistSongSortType.CUSTOM) {
            IconButton(
                onClick = onToggleLock,
                onLongClick = {},
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Icon(
                    painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                    contentDescription = null,
                )
            }
        }
    }
}
