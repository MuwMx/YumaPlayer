/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDragHandle
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * LOCAL origin row — a faithful port of LocalPlaylistSongItem
 * (LocalPlaylistListSection.kt:131-213): optional ReorderableItem wrapper,
 * PlaylistSongRow swipe-delete wrapper, SongListItem body, trailing SongMenu
 * plus drag handle when reordering is enabled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.LocalPlaylistOriginRow(
    track: PlaylistTrack,
    origin: PlaylistSong,
    index: Int,
    reorderableState: ReorderableLazyListState,
    showDragHandle: Boolean,
    canSwipeDelete: Boolean,
    isScrollInProgress: Boolean,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    viewCount: Int?,
    selected: Boolean,
    selection: Boolean,
    navController: NavController,
    menuState: MenuState,
    playlistBrowseId: String?,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showDragHandle) {
        ReorderableItem(
            state = reorderableState,
            key = playlistDetailTrackKey(track, index),
            modifier =
                modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            LocalPlaylistOriginRowContent(
                origin = origin,
                isScrollInProgress = isScrollInProgress,
                canSwipeDelete = canSwipeDelete,
                mediaMetadataId = mediaMetadataId,
                isPlaying = isPlaying,
                viewCount = viewCount,
                selected = selected,
                selection = selection,
                dragHandleModifier = Modifier.draggableHandle(),
                navController = navController,
                menuState = menuState,
                playlistBrowseId = playlistBrowseId,
                onSwipeDelete = onSwipeDelete,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    } else {
        LocalPlaylistOriginRowContent(
            origin = origin,
            isScrollInProgress = isScrollInProgress,
            canSwipeDelete = canSwipeDelete,
            mediaMetadataId = mediaMetadataId,
            isPlaying = isPlaying,
            viewCount = viewCount,
            selected = selected,
            selection = selection,
            dragHandleModifier = null,
            navController = navController,
            menuState = menuState,
            playlistBrowseId = playlistBrowseId,
            onSwipeDelete = onSwipeDelete,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalPlaylistOriginRowContent(
    origin: PlaylistSong,
    isScrollInProgress: Boolean,
    canSwipeDelete: Boolean,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    viewCount: Int?,
    selected: Boolean,
    selection: Boolean,
    dragHandleModifier: Modifier?,
    navController: NavController,
    menuState: MenuState,
    playlistBrowseId: String?,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities(swipeDelete = canSwipeDelete),
        isScrollInProgress = isScrollInProgress,
        onSwipeDelete = onSwipeDelete,
        modifier = modifier,
    ) {
        SongListItem(
            song = origin.song,
            viewCountText = viewCount?.let { formatCompactCount(it.toLong()) },
            isActive = origin.song.id == mediaMetadataId,
            isPlaying = isPlaying,
            showInLibraryIcon = true,
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = origin.song,
                                playlistSong = if (!selection) origin else null,
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
                if (dragHandleModifier != null) {
                    PlaylistDragHandle(
                        onDragHandleModifier =
                            dragHandleModifier
                                .graphicsLayer { alpha = 0.99f },
                    )
                }
            },
            isSelected = selected,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}
