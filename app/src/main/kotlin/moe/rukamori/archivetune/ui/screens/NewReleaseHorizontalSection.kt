/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.viewmodels.NewReleaseContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewReleaseHorizontalSection(
    releases: List<AlbumItem>,
    contentType: String,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onReleaseClick: (AlbumItem) -> Unit,
    onReleaseLongClick: (AlbumItem) -> Unit,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(216.dp),
    ) {
        items(
            items = releases,
            key = { it.id },
            contentType = { contentType },
        ) { album ->
            YouTubeGridItem(
                item = album,
                isActive = activeAlbumId == album.id,
                isPlaying = isPlaying,
                fillMaxWidth = false,
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
@Composable
internal fun NewReleaseSummaryCard(
    content: NewReleaseContent,
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
) {
    val summaryShape = remember { RoundedCornerShape(28.dp) }

    Surface(
        shape = summaryShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        tonalElevation = 3.dp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.total_releases),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = content.totalReleases.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            NewReleaseTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }
    }
}
@Composable
private fun NewReleaseTabs(
    selectedTab: NewReleaseTab,
    onTabSelected: (NewReleaseTab) -> Unit,
) {
    val tabs = remember { NewReleaseTab.entries.toList() }
    val selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val tabShape = remember { RoundedCornerShape(28.dp) }
    val selectedContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
    val unselectedContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
    val selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorColor = MaterialTheme.colorScheme.primary

    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        contentColor = selectedContentColor,
        divider = {},
        indicator = { tabPositions ->
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier =
                    Modifier
                        .tabIndicatorOffset(tabPositions[selectedTabIndex])
                        .fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(76.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(indicatorColor),
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(66.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            val title = stringResource(tab.titleRes)

            Tab(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selectedContentColor = selectedContentColor,
                unselectedContentColor = unselectedContentColor,
                modifier =
                    Modifier
                        .padding(horizontal = 3.dp, vertical = 6.dp)
                        .height(56.dp)
                        .clip(tabShape)
                        .background(if (selected) selectedContainer else unselectedContainer),
            )
        }
    }
}
@Composable
internal fun NewReleaseCategoryEmptyState(onRefresh: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 56.dp),
    ) {
        Text(
            text = stringResource(R.string.no_releases_found),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRefresh,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.refresh))
        }
    }
}
internal fun NewReleaseContent.releasesFor(tab: NewReleaseTab): List<AlbumItem> =
    when (tab) {
        NewReleaseTab.All -> emptyList()
        NewReleaseTab.Albums -> albums
        NewReleaseTab.Singles -> singles
        NewReleaseTab.Ep -> eps
    }
