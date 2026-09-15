package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.utils.SnapLayoutInfoProvider

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.chartsSongSections(
    sections: List<ChartsPage.ChartSection>,
    activeSongId: String?,
    isPlaying: Boolean,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onMoreClick: (SongItem) -> Unit,
) {
    sections.forEach { section ->
        item(contentType = CONTENT_TYPE_CHARTS_HEADER) {
            NavigationTitle(
                title =
                    when (section.title) {
                        TRENDING_SECTION_TITLE -> stringResource(R.string.trending)
                        else -> section.title.ifEmpty { stringResource(R.string.charts) }
                    },
            )
        }
        item(contentType = CONTENT_TYPE_CHARTS_GRID) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val horizontalLazyGridItemWidthFactor = chartsGridItemWidthFactor(maxWidth)
                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                val lazyGridState = rememberLazyGridState()
                val snapLayoutInfoProvider =
                    remember(lazyGridState) {
                        SnapLayoutInfoProvider(
                            lazyGridState = lazyGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                            },
                        )
                    }

                LazyHorizontalGrid(
                    state = lazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4),
                ) {
                    items(
                        items = section.items.filterIsInstance<SongItem>().distinctBy { it.id },
                        key = { it.id },
                        contentType = { CONTENT_TYPE_CHARTS_SONG },
                    ) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = song.id == activeSongId,
                            isPlaying = isPlaying,
                            isSwipeable = false,
                            trailingContent = {
                                IconButton(
                                    onClick = { onMoreClick(song) },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .combinedClickable(
                                        onClick = { onSongClick(song) },
                                        onLongClick = { onSongLongClick(song) },
                                    ),
                        )
                    }
                }
            }
        }
    }
}
