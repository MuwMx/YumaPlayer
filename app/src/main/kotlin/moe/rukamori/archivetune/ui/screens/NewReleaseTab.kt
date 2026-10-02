/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.viewmodels.NewReleaseContent

@Immutable
internal enum class NewReleaseTab(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val contentType: String,
) {
    All(
        titleRes = R.string.filter_all,
        icon = Icons.Outlined.LibraryMusic,
        contentType = "new_release_all_grid_item",
    ),
    Albums(
        titleRes = R.string.albums,
        icon = Icons.Outlined.Album,
        contentType = "new_release_album_grid_item",
    ),
    Singles(
        titleRes = R.string.singles,
        icon = Icons.Outlined.MusicNote,
        contentType = "new_release_single_grid_item",
    ),
    Ep(
        titleRes = R.string.ep,
        icon = Icons.Outlined.QueueMusic,
        contentType = "new_release_ep_grid_item",
    ),
}
@Immutable
internal data class NewReleaseSection(
    val tab: NewReleaseTab,
    val releases: List<AlbumItem>,
)
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewReleaseGridContent(
    content: NewReleaseContent,
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
    paddingValues: PaddingValues,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onReleaseClick: (AlbumItem) -> Unit,
    onReleaseLongClick: (AlbumItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val allSections =
        remember(content) {
            content.releaseSections()
        }
    val releases =
        remember(content, selectedTab) {
            if (selectedTab == NewReleaseTab.All) emptyList() else content.releasesFor(selectedTab)
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = paddingValues,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(
            key = "new_release_summary",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "new_release_summary",
        ) {
            NewReleaseSummaryCard(
                content = content,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }

        if (selectedTab == NewReleaseTab.All) {
            allSections.forEach { section ->
                item(
                    key = "new_release_section_header_${section.tab.name}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "new_release_section_header",
                ) {
                    NewReleaseSectionHeader(
                        title = stringResource(section.tab.titleRes),
                        count = section.releases.size,
                    )
                }

                item(
                    key = "new_release_section_${section.tab.name}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "new_release_horizontal_section",
                ) {
                    NewReleaseHorizontalSection(
                        releases = section.releases,
                        contentType = section.tab.contentType,
                        activeAlbumId = activeAlbumId,
                        isPlaying = isPlaying,
                        coroutineScope = coroutineScope,
                        onReleaseClick = onReleaseClick,
                        onReleaseLongClick = onReleaseLongClick,
                    )
                }
            }
        } else if (releases.isEmpty()) {
            item(
                key = "new_release_empty_${selectedTab.name}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "new_release_empty",
            ) {
                NewReleaseCategoryEmptyState(onRefresh = onRefresh)
            }
        } else {
            items(
                items = releases,
                key = { it.id },
                contentType = { selectedTab.contentType },
            ) { album ->
                YouTubeGridItem(
                    item = album,
                    isActive = activeAlbumId == album.id,
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    coroutineScope = coroutineScope,
                    modifier =
                        Modifier
                            .animateItem()
                            .combinedClickable(
                                onClick = { onReleaseClick(album) },
                                onLongClick = { onReleaseLongClick(album) },
                            ),
                )
            }
        }
    }
}
@Composable
private fun NewReleaseSectionHeader(
    title: String,
    count: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
