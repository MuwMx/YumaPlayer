/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

@Composable
fun LocalPlaylistSortHeader(
    sortType: PlaylistSongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (PlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    editable: Boolean,
    locked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp),
    ) {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { type ->
                when (type) {
                    PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                    PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                    PlaylistSongSortType.NAME -> R.string.sort_by_name
                    PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                    PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (editable && sortType == PlaylistSongSortType.CUSTOM) {
            IconButton(
                onClick = onToggleLock,
                onLongClick = {},
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Icon(
                    painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                    contentDescription = null,
                )
            }
        }
    }
}

fun LazyListScope.localPlaylistSortHeaderItem(
    sortType: PlaylistSongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (PlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    editable: Boolean,
    locked: Boolean,
    onToggleLock: () -> Unit,
) {
    item(key = LOCAL_PLAYLIST_KEY_SORT_HEADER, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_SORT_HEADER) {
        LocalPlaylistSortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            editable = editable,
            locked = locked,
            onToggleLock = onToggleLock,
        )
    }
}

fun LazyListScope.localPlaylistEmptyItem() {
    item(key = LOCAL_PLAYLIST_KEY_EMPTY, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_EMPTY) {
        EmptyPlaceholder(
            icon = R.drawable.music_note,
            text = stringResource(R.string.playlist_is_empty),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LazyItemScope.LocalPlaylistSongItem(
    song: PlaylistSong,
    index: Int,
    reorderableState: ReorderableLazyListState,
    isScrollInProgress: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    viewCountText: String?,
    isSelected: Boolean,
    selection: Boolean,
    showDragHandle: Boolean,
    canSwipeDismiss: Boolean,
    navController: NavController,
    menuState: MenuState,
    playlistBrowseId: String?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ReorderableItem(
        state = reorderableState,
        key = "${song.map.id}_$index",
        modifier =
            modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val currentOnDelete by rememberUpdatedState(onDelete)
        val currentIsScrollInProgress by rememberUpdatedState(isScrollInProgress)

        val dismissBoxState =
            rememberSwipeToDismissBoxState(
                positionalThreshold = { totalDistance -> totalDistance },
                confirmValueChange = { targetValue ->
                    targetValue == SwipeToDismissBoxValue.Settled || !currentIsScrollInProgress
                },
            )
        var processedDismiss by remember { mutableStateOf(false) }
        LaunchedEffect(dismissBoxState.currentValue) {
            val dv = dismissBoxState.currentValue
            if (!processedDismiss && (
                    dv == SwipeToDismissBoxValue.StartToEnd ||
                        dv == SwipeToDismissBoxValue.EndToStart
                )
            ) {
                processedDismiss = true
                currentOnDelete()
            }
            if (dv == SwipeToDismissBoxValue.Settled) {
                processedDismiss = false
            }
        }

        val content: @Composable () -> Unit = {
            SongListItem(
                song = song.song,
                viewCountText = viewCountText,
                isActive = isActive,
                isPlaying = isPlaying,
                showInLibraryIcon = true,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song.song,
                                    playlistSong = if (!selection) song else null,
                                    playlistBrowseId = playlistBrowseId,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }

                    if (showDragHandle) {
                        IconButton(
                            onClick = { },
                            onLongClick = {},
                            modifier =
                                Modifier
                                    .draggableHandle()
                                    .graphicsLayer { alpha = 0.99f },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                            )
                        }
                    }
                },
                isSelected = isSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        ),
            )
        }

        if (!canSwipeDismiss) {
            content()
        } else {
            SwipeToDismissBox(
                state = dismissBoxState,
                backgroundContent = {},
            ) {
                content()
            }
        }
    }
}

fun LazyListScope.localPlaylistSongs(
    songs: List<PlaylistSong>,
    reorderableState: ReorderableLazyListState,
    lazyListState: LazyListState,
    selection: Boolean,
    selectedSongMapIds: Set<Int>,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    viewCounts: Map<String, Int>,
    sortType: PlaylistSongSortType,
    locked: Boolean,
    isSearching: Boolean,
    editable: Boolean,
    swipeToSongEnabled: Boolean,
    navController: NavController,
    menuState: MenuState,
    playlistBrowseId: String?,
    onDeleteSong: (PlaylistSong) -> Unit,
    onSongClick: (index: Int, song: PlaylistSong) -> Unit,
    onSongLongClick: (PlaylistSong) -> Unit,
) {
    itemsIndexed(
        items = songs,
        key = { index, song -> "${song.map.id}_$index" },
        contentType = { _, _ -> CONTENT_TYPE_LOCAL_PLAYLIST_SONG },
    ) { index, song ->
        LocalPlaylistSongItem(
            song = song,
            index = index,
            reorderableState = reorderableState,
            isScrollInProgress = lazyListState.isScrollInProgress,
            isActive = song.song.id == mediaMetadataId,
            isPlaying = isPlaying,
            viewCountText = viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
            isSelected = if (selection) song.map.id in selectedSongMapIds else false,
            selection = selection,
            showDragHandle = sortType == PlaylistSongSortType.CUSTOM && !locked && !selection && !isSearching && editable,
            canSwipeDismiss = if (!selection) !(locked || selection || swipeToSongEnabled) else !(locked || !editable || swipeToSongEnabled),
            navController = navController,
            menuState = menuState,
            playlistBrowseId = playlistBrowseId,
            onDelete = { onDeleteSong(song) },
            onClick = { onSongClick(index, song) },
            onLongClick = { onSongLongClick(song) },
        )
    }
}
