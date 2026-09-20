/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumFilterKey
import moe.rukamori.archivetune.constants.AlbumSortDescendingKey
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.constants.AlbumSortTypeKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryAlbumsViewModel

@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    LocalPlayerConnection.current ?: return

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            AlbumSortTypeKey,
            AlbumSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    var isGridView by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val featuredAlbum = albums.firstOrNull()

    val filteredAlbums =
        if (hideExplicit) {
            albums.filter { !it.album.explicit }
        } else {
            albums
        }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.sync() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlbumsControlsRow(
                filter = filter,
                sortType = sortType,
                sortDescending = sortDescending,
                isGridView = isGridView,
                onFilterChange = { filter = it },
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                onViewModeChange = { isGridView = it },
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    item(span = { GridItemSpan(4) }, key = KEY_FEATURED_ALBUM_CARD) {
                        featuredAlbum?.let { album ->
                            FeaturedAlbumCard(
                                album = album,
                                navController = navController,
                            )
                        }
                    }

                    if (filteredAlbums.isEmpty()) {
                        item(span = { GridItemSpan(4) }, key = KEY_EMPTY_ALBUMS_GRID) {
                            AlbumsEmptySection()
                        }
                    } else {
                        items(
                            items = filteredAlbums,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM_GRID_ITEM },
                        ) { album ->
                            AlbumGridItem(
                                album = album,
                                navController = navController,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.LibraryItemGap),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    if (filteredAlbums.isEmpty()) {
                        item(key = KEY_EMPTY_ALBUMS_LIST) {
                            AlbumsEmptySection()
                        }
                    } else {
                        itemsIndexed(
                            items = filteredAlbums,
                            key = { _, it -> it.id },
                            contentType = { _, _ -> CONTENT_TYPE_ALBUM_LIST_ITEM },
                        ) { _, album ->
                            AlbumListRow(
                                album = album,
                                navController = navController,
                            )
                        }
                    }
                }
            }
        }
    }
}
