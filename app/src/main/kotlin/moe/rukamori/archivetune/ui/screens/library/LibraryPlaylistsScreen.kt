/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSortDescendingKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PlaylistSortTypeKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val haptics = rememberYumaHaptics()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSortTypeKey,
            PlaylistSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val isDarkTheme = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val filteredPlaylistIds: List<String>? by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsStateWithLifecycle(initialValue = null)

    var showHidden by rememberSaveable { mutableStateOf(false) }

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds, showHidden) {
            val filterIds = filteredPlaylistIds
            playlists.filter { playlist ->
                val name = playlist.playlist.name
                val matchesName = !name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || (filterIds != null && playlist.id in filterIds)
                val matchesVisibility = showHidden || !playlist.playlist.isHidden
                matchesName && matchesTags && matchesVisibility
            }
        }
    val mutablePlaylists = remember { mutableStateListOf<Playlist>() }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    var pendingPlaylistOrderUpdate by remember { mutableStateOf(false) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (from.index in mutablePlaylists.indices && to.index in mutablePlaylists.indices) {
                mutablePlaylists.move(from.index, to.index)
                pendingPlaylistOrderUpdate = true
            }
        }

    LaunchedEffect(visiblePlaylists) {
        mutablePlaylists.clear()
        mutablePlaylists.addAll(visiblePlaylists)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && pendingPlaylistOrderUpdate) {
            viewModel.updateCustomPlaylistOrder(
                mergeVisiblePlaylistOrder(
                    currentOrder = playlists,
                    visibleOrder = mutablePlaylists,
                ),
            )
            pendingPlaylistOrderUpdate = false
        }
    }

    // Dialog launcher
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.sync() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ControlRow(
                sortType = sortType,
                sortDescending = sortDescending,
                isGridView = isGridView,
                locked = locked,
                showHidden = showHidden,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                onLockedChange = { locked = it },
                onGridViewChange = { isGridView = it },
                onShowHiddenChange = { showHidden = it },
                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                filterContent = filterContent,
            )

            // Main Content
            if (isGridView) {
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
                    if (visiblePlaylists.isEmpty()) {
                        if (selectedTagIds.isEmpty() || filteredPlaylistIds != null) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }, key = KEY_EMPTY_PLAYLISTS_GRID) {
                                LibraryEmptyState(
                                    iconRes = R.drawable.queue_music,
                                    titleRes = R.string.no_playlists_yet,
                                    subtitleRes = R.string.library_playlists_subtitle,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                    } else {
                        items(
                            items = visiblePlaylists,
                            key = { playlist -> playlist.id },
                            contentType = { CONTENT_TYPE_PLAYLIST_GRID },
                        ) { playlist ->
                            PlaylistGridCard(
                                playlist = playlist,
                                onClick = {
                                    openPlaylist(navController, playlist)
                                },
                                onPlay = {
                                    playerConnection?.let { conn ->
                                        coroutineScope.launch {
                                            database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                                if (songs.isNotEmpty()) {
                                                    conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptics.longPress()
                                    menuState.show {
                                        triggerPlaylistMenu(playlist, coroutineScope, menuState)
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                val listPlaylists = if (sortType == PlaylistSortType.CUSTOM) mutablePlaylists else visiblePlaylists
                val showDragHandles = sortType == PlaylistSortType.CUSTOM && !locked
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.LibraryItemGap),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                ) {
                    if (listPlaylists.isEmpty()) {
                        if (selectedTagIds.isEmpty() || filteredPlaylistIds != null) {
                            item(key = KEY_EMPTY_PLAYLISTS) {
                                LibraryEmptyState(
                                    iconRes = R.drawable.queue_music,
                                    titleRes = R.string.no_playlists_yet,
                                    subtitleRes = R.string.library_playlists_subtitle,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = listPlaylists,
                            key = { _, playlist -> playlist.id },
                            contentType = { _, _ -> CONTENT_TYPE_PLAYLIST_LIST },
                        ) { index, playlist ->
                            ReorderableItem(
                                state = reorderableState,
                                key = playlist.id,
                                modifier =
                                    Modifier.graphicsLayer {
                                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                    },
                            ) {
                                PlaylistListCard(
                                    playlist = playlist,
                                    position = YumaSegmentPosition.Single,
                                    onClick = {
                                        openPlaylist(navController, playlist)
                                    },
                                    onPlay = {
                                        playerConnection?.let { conn ->
                                            coroutineScope.launch {
                                                database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                                    if (songs.isNotEmpty()) {
                                                        conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onMenuClick = {
                                        menuState.show {
                                            triggerPlaylistMenu(playlist, coroutineScope, menuState)
                                        }
                                    },
                                    showDragHandle = showDragHandles,
                                    dragHandleModifier =
                                        Modifier
                                            .draggableHandle()
                                            .graphicsLayer { alpha = 0.99f },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
