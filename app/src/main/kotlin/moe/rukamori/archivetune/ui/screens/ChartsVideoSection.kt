package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.chartsTopVideosSection(
    section: ChartsPage.ChartSection,
    activeSongId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onVideoClick: (SongItem) -> Unit,
    onVideoLongClick: (SongItem) -> Unit,
) {
    item(contentType = CONTENT_TYPE_CHARTS_HEADER) {
        NavigationTitle(
            title = stringResource(R.string.top_music_videos),
        )
    }
    item(contentType = CONTENT_TYPE_CHARTS_ROW) {
        LazyRow(
            contentPadding =
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
        ) {
            items(
                items = section.items.filterIsInstance<SongItem>().distinctBy { it.id },
                key = { it.id },
                contentType = { CONTENT_TYPE_CHARTS_VIDEO },
            ) { video ->
                YouTubeGridItem(
                    item = video,
                    isActive = video.id == activeSongId,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongClick(video) },
                            ),
                )
            }
        }
    }
}
