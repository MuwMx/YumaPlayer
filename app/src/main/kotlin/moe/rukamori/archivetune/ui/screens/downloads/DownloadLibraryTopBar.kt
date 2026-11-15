/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton as YumaIconButton
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySort
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortField
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortOrder
import moe.rukamori.archivetune.viewmodels.DownloadLibraryTab

@Composable
internal fun DownloadLibraryTopBar(
    isSearchActive: Boolean,
    query: String,
    sort: DownloadLibrarySort,
    tabs: List<DownloadLibraryTab>,
    pagerState: PagerState,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onActivateSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSubmitSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectSortField: (DownloadLibrarySortField) -> Unit,
    onSelectSortOrder: (DownloadLibrarySortOrder) -> Unit,
    onSelectTab: (Int) -> Unit,
) {
    AnimatedContent(
        targetState = isSearchActive,
        transitionSpec = {
            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
        },
        label = "downloadLibraryTopBar",
    ) { searching ->
        if (searching) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = { onSubmitSearch() },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.search_downloads)) },
                        leadingIcon = {
                            IconButton(onClick = onCloseSearch) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = null,
                                )
                            }
                        },
                        trailingIcon =
                            if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = onClearQuery) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.close),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 4.dp),
            ) {}
        } else {
            Column {
                MediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.downloads),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        YumaIconButton(
                            onClick = onBack,
                            onLongClick = onBackToMain,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        DownloadSortMenu(
                            sortField = sort.field,
                            sortOrder = sort.order,
                            onSortFieldSelected = onSelectSortField,
                            onSortOrderSelected = onSelectSortOrder,
                        )
                        IconButton(onClick = onActivateSearch) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        ),
                    scrollBehavior = scrollBehavior,
                )
                DownloadTabs(
                    tabs = tabs,
                    pagerState = pagerState,
                    onSelect = onSelectTab,
                )
            }
        }
    }
}
