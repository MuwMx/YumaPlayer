/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistFilter
import moe.rukamori.archivetune.constants.ArtistFilterKey
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.constants.ArtistSortDescendingKey
import moe.rukamori.archivetune.constants.ArtistSortType
import moe.rukamori.archivetune.constants.ArtistSortTypeKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryArtistsViewModel

@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val params = remember(onDeselect) { LibraryArtistsParams(onDeselect = onDeselect) }
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            ArtistSortTypeKey,
            ArtistSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val topArtist = artists.firstOrNull()

    val uiState =
        LibraryArtistsUiState(
            list = artists,
            isRefreshing = isRefreshing,
            sortType = sortType,
            sortDescending = sortDescending,
            filter = filter,
        )

    val events =
        remember(
            coroutineScope,
            database,
            playerConnection,
            menuState,
            navController,
            viewModel,
            onSortTypeChange,
            onSortDescendingChange,
        ) {
            LibraryArtistsEvents(
                onRefresh = { viewModel.sync() },
                onSort = { type ->
                    onSortTypeChange(type)
                    if (type == ArtistSortType.NAME) onSortDescendingChange(false)
                },
                onSortDescendingChange = onSortDescendingChange,
                onFilterToggle = {
                    filter = if (filter == ArtistFilter.LIKED) ArtistFilter.LIBRARY else ArtistFilter.LIKED
                },
                onPlay = { artistWrapper ->
                    coroutineScope.launch {
                        val songs =
                            database
                                .artistSongs(
                                    artistWrapper.id,
                                    ArtistSongSortType.CREATE_DATE,
                                    true,
                                ).first()
                                .map { it.toMediaItem() }
                        if (songs.isNotEmpty()) {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = artistWrapper.artist.name,
                                    items = songs,
                                ),
                            )
                        }
                    }
                },
                onOpen = { artistWrapper ->
                    navController.navigate("artist/${artistWrapper.artist.id}")
                },
                onMenu = { artistWrapper ->
                    menuState.show {
                        ArtistMenu(
                            originalArtist = artistWrapper,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = events.onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        ) {
            // Featured Spotlight Row
            item(span = { GridItemSpan(2) }, key = KEY_SPOTLIGHT_ROW) {
                SpotlightSection(
                    topArtist = topArtist,
                    totalArtistsCount = uiState.list.size,
                    onOpenTopArtist = { topArtist?.let { events.onOpen(it) } },
                    onPlayTopArtist = { topArtist?.let { events.onPlay(it) } },
                    onMenuTopArtist = { topArtist?.let { events.onMenu(it) } },
                    onFilterToggle = events.onFilterToggle,
                )
            }

            // Sub-Header Controls (Sort dropdown, genres, view list/grid, etc)
            item(span = { GridItemSpan(2) }, key = KEY_SUB_HEADER) {
                SortFilterBar(
                    sortType = uiState.sortType,
                    sortDescending = uiState.sortDescending,
                    filter = uiState.filter,
                    onSortTypeChange = events.onSort,
                    onSortDescendingChange = events.onSortDescendingChange,
                )
            }

            // Artists Grid: 2-column capsule artist cards
            if (uiState.list.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = KEY_EMPTY_ARTISTS) {
                    LibraryEmptyState(
                        iconRes = R.drawable.person,
                        titleRes = R.string.no_results_found,
                        subtitleRes = R.string.library_artists_subtitle,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.list,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> CONTENT_TYPE_ARTIST_ITEM },
                ) { _, artistWrapper ->
                    ArtistGridCard(
                        artistWrapper = artistWrapper,
                        onClick = { events.onOpen(artistWrapper) },
                        onLongClick = {
                            haptics.longPress()
                            events.onMenu(artistWrapper)
                        },
                        onPlayClick = { events.onPlay(artistWrapper) },
                    )
                }
            }

            // Recently Played Artists Section
            if (uiState.list.size > 2) {
                item(span = { GridItemSpan(2) }, key = KEY_RECENT_ARTISTS_HEADER) {
                    Text(
                        text = stringResource(R.string.recently_played_artists),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                item(span = { GridItemSpan(2) }, key = KEY_RECENT_ARTISTS_ROW) {
                    RecentArtistsRow(
                        artists = uiState.list,
                        onArtistClick = { events.onOpen(it) },
                    )
                }
            }
        }
    }
}
