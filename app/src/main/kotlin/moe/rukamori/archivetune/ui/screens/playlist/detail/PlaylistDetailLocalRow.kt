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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDragHandle
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistLikeButton
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow
import moe.rukamori.archivetune.ui.screens.playlist.shared.togglePlaylistSongLike
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.utils.LikeSourceResolver
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

/**
 * LOCAL origin row — a faithful port of LocalPlaylistSongItem
 * (LocalPlaylistListSection.kt:131-213): optional ReorderableItem wrapper,
 * PlaylistSongRow swipe-delete wrapper, SongListItem body, trailing SongMenu
 * plus drag handle when reordering is enabled — plus the trailing like
 * button left of the menu (user spec 2026-11-15). The like source resolves
 * exactly like SongMenu (:159-169): a "spotify:"-linked playlist pins
 * SPOTIFY, otherwise the id resolver decides.
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
    showLikeButton: Boolean,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowContent: @Composable (Modifier?, Modifier?) -> Unit = { dragHandleModifier, rowModifier ->
        LocalPlaylistOriginRowContent(
            origin = origin,
            isScrollInProgress = isScrollInProgress,
            canSwipeDelete = canSwipeDelete,
            mediaMetadataId = mediaMetadataId,
            isPlaying = isPlaying,
            viewCount = viewCount,
            selected = selected,
            selection = selection,
            playlistBrowseId = playlistBrowseId,
            showLikeButton = showLikeButton,
            dragHandleModifier = dragHandleModifier,
            navController = navController,
            menuState = menuState,
            onSwipeDelete = onSwipeDelete,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = rowModifier ?: Modifier,
        )
    }
    if (showDragHandle) {
        ReorderableItem(
            state = reorderableState,
            key = playlistDetailTrackKey(track, index),
            modifier =
                modifier.graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            rowContent(Modifier.draggableHandle(), null)
        }
    } else {
        rowContent(null, modifier)
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
    playlistBrowseId: String?,
    showLikeButton: Boolean,
    dragHandleModifier: Modifier?,
    navController: NavController,
    menuState: MenuState,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val likeSource =
        if (playlistBrowseId?.startsWith("spotify:") == true) {
            LikeSource.SPOTIFY
        } else {
            LikeSourceResolver.resolve(mediaId = origin.song.song.id, isLocal = origin.song.song.isLocal)
        }

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
            // One heart per row: the trailing button replaces the subtitle
            // badge wherever it shows.
            showLikedIcon = !showLikeButton,
            trailingContent = {
                if (showLikeButton) {
                    PlaylistLikeButton(
                        isLiked = origin.song.song.liked,
                        onToggle = {
                            togglePlaylistSongLike(
                                seed = origin.song.song,
                                source = likeSource,
                                database = database,
                                syncUtils = syncUtils,
                                coroutineScope = coroutineScope,
                            )
                        },
                    )
                }
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
