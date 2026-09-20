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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptics = rememberYumaHaptics()
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    val lazyListState = rememberLazyListState()

    LaunchedEffect(filter) {
        if (!isRefreshing) {
            viewModel.refresh(filter)
        }
    }

    val hideExplicit by rememberPreference(HideExplicitKey, defaultValue = false)
    val displaySongs = remember(songs, hideExplicit) {
        if (hideExplicit) songs.filter { !it.song.explicit } else songs
    }

    val totalDurationText = remember(displaySongs) {
        formatTotalDuration(displaySongs.sumOf { it.song.duration.toLong() })
    }

    val uiState = LibrarySongsUiState(
        count = displaySongs.size,
        totalDurationText = totalDurationText,
        isRefreshing = isRefreshing,
    )

    val actions = remember(displaySongs, mediaMetadata, playerConnection, context, navController, menuState, filter, sortType, sortDescending) {
        LibrarySongsActions(
            onPlayAll = {
                if (displaySongs.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = context.getString(R.string.queue_all_songs),
                            items = displaySongs.map { it.toMediaItem() },
                        ),
                    )
                }
            },
            onOpen = { songId ->
                val index = displaySongs.indexOfFirst { it.id == songId }
                if (index != -1) {
                    if (songId == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.queue_all_songs),
                                items = displaySongs.map { it.toMediaItem() },
                                startIndex = index,
                            ),
                        )
                    }
                }
            },
            onMenu = { song ->
                menuState.show {
                    SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                }
            },
            onRetry = { viewModel.refresh(filter) },
            onFilter = { filter = it },
            onSortType = { type ->
                onSortTypeChange(type)
                if (type == SongSortType.NAME) onSortDescendingChange(false)
            },
            onSortDescending = { onSortDescendingChange(it) },
        )
    }

    ExpressivePullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = actions.onRetry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SongsFilterRow(
                filter = filter,
                sortType = sortType,
                sortDescending = sortDescending,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "collection_spotlight") {
                    SongsSpotlightCard(uiState = uiState, onPlayClick = actions.onPlayAll)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (displaySongs.isEmpty()) {
                    item(key = "empty_state") {
                        SongsEmptySection(filter = filter)
                    }
                } else {
                    itemsIndexed(
                        items = displaySongs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { _, song ->
                        val isActive = song.id == mediaMetadata?.id
                        SongRow(
                            song = song,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            onClick = { actions.onOpen(song.id) },
                            onLongClick = {
                                haptics.longPress()
                                actions.onMenu(song)
                            },
                            onMenuClick = { actions.onMenu(song) },
                        )
                    }
                }
            }
        }
    }
}

