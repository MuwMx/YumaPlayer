/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.ui.component.ChipsRow

@Composable
fun SearchFilterHeader(
    searchFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top).add(WindowInsets(top = AppBarHeight)))
                .fillMaxWidth(),
    ) {
        ChipsRow(
            containerColor = Color.Transparent,
            chips =
                listOf(
                    null to stringResource(R.string.filter_all),
                    FILTER_SONG to stringResource(R.string.filter_songs),
                    FILTER_VIDEO to stringResource(R.string.filter_videos),
                    FILTER_ALBUM to stringResource(R.string.filter_albums),
                    FILTER_ARTIST to stringResource(R.string.filter_artists),
                    FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                    FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                ),
            currentValue = searchFilter,
            onValueUpdate = onFilterChange,
            icons =
                mapOf(
                    null to R.drawable.ic_search,
                    FILTER_SONG to R.drawable.music_note,
                    FILTER_VIDEO to R.drawable.slow_motion_video,
                    FILTER_ALBUM to R.drawable.album,
                    FILTER_ARTIST to R.drawable.person,
                    FILTER_COMMUNITY_PLAYLIST to R.drawable.queue_music,
                    FILTER_FEATURED_PLAYLIST to R.drawable.playlist_play,
                ),
        )
    }
}

@Composable
fun OnlineSearchHeader(
    searchFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchFilterHeader(
        searchFilter = searchFilter,
        onFilterChange = onFilterChange,
        modifier = modifier,
    )
}
