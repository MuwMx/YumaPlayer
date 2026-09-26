/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist.auto

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoPlaylistSongSortType
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.playlist.CONTENT_TYPE_EMPTY
import moe.rukamori.archivetune.ui.screens.playlist.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.ui.screens.playlist.CONTENT_TYPE_SONG_ACTIVE
import moe.rukamori.archivetune.ui.screens.playlist.CONTENT_TYPE_SONG_SELECTED
import moe.rukamori.archivetune.ui.utils.ItemWrapper

@Composable
internal fun AutoPlaylistTrackRow(
    songWrapper: ItemWrapper<Song>,
    isActive: Boolean,
    isPlaying: Boolean,
    isSelected: Boolean,
    downloadState: Int?,
    downloadProgress: Float,
    trailingContent: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SongListItem(
        song = songWrapper.item,
        isActive = isActive,
        isPlaying = isPlaying,
        showInLibraryIcon = true,
        downloadState = downloadState,
        downloadProgress = downloadProgress,
        trailingContent = trailingContent,
        isSelected = isSelected,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    )
}

internal fun LazyListScope.autoPlaylistEmptyState() {
    item(
        key = "empty",
        contentType = CONTENT_TYPE_EMPTY,
    ) {
        EmptyPlaceholder(
            icon = R.drawable.music_note,
            text = stringResource(R.string.playlist_is_empty),
        )
    }
}

internal fun LazyListScope.autoPlaylistTrackRows(
    filteredSongs: List<ItemWrapper<Song>>,
    wrappedSongs: List<ItemWrapper<Song>>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    selection: Boolean,
    selectedCount: Int,
    onSelectionChange: (Boolean) -> Unit,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    haptics: YumaHaptics,
    downloads: Map<String, Download> = emptyMap(),
    onSongClick: (String) -> Unit = {},
) {
    items(
        items = filteredSongs,
        key = { songWrapper -> songWrapper.item.id },
        contentType = { songWrapper ->
            val isActive = songWrapper.item.song.id == mediaMetadata?.id
            val isSelected = songWrapper.isSelected && selection
            when {
                isActive -> CONTENT_TYPE_SONG_ACTIVE
                isSelected -> CONTENT_TYPE_SONG_SELECTED
                else -> CONTENT_TYPE_SONG
            }
        },
    ) { songWrapper ->
        val download = downloads[songWrapper.item.id]
        val trailingContent: @Composable RowScope.() -> Unit =
            remember(songWrapper.item, navController, menuState) {
                {
                    IconButton(
                        onClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = songWrapper.item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                }
            }

        AutoPlaylistTrackRow(
            songWrapper = songWrapper,
            isActive = songWrapper.item.song.id == mediaMetadata?.id,
            isPlaying = isPlaying,
            isSelected = songWrapper.isSelected && selection,
            downloadState = download?.state,
            downloadProgress = download?.percentDownloaded ?: -1f,
            trailingContent = trailingContent,
            onClick = {
                if (!selection || selectedCount == 0) {
                    if (songWrapper.item.song.id == mediaMetadata?.id) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        onSongClick(songWrapper.item.song.id)
                    }
                } else {
                    songWrapper.isSelected = !songWrapper.isSelected
                }
            },
            onLongClick = {
                haptics.longPress()
                if (!selection) {
                    onSelectionChange(true)
                    wrappedSongs.forEach { it.isSelected = false }
                    songWrapper.isSelected = true
                } else {
                    songWrapper.isSelected = !songWrapper.isSelected
                }
            },
        )
    }
}

internal fun LazyListScope.songListSection(
    songs: List<Song>,
    filteredSongs: List<ItemWrapper<Song>>,
    wrappedSongs: List<ItemWrapper<Song>>,
    sortType: AutoPlaylistSongSortType,
    sortDescending: Boolean,
    onSortTypeChange: (AutoPlaylistSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    selection: Boolean,
    selectedCount: Int,
    onSelectionChange: (Boolean) -> Unit,
    playlist: String,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    haptics: YumaHaptics,
    downloads: Map<String, Download> = emptyMap(),
    onSongClick: (String) -> Unit = {},
) {
    if (songs.isEmpty()) {
        autoPlaylistEmptyState()
    } else {
        autoPlaylistRuleCards(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
        )
        autoPlaylistTrackRows(
            filteredSongs = filteredSongs,
            wrappedSongs = wrappedSongs,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            selection = selection,
            selectedCount = selectedCount,
            onSelectionChange = onSelectionChange,
            playerConnection = playerConnection,
            navController = navController,
            menuState = menuState,
            haptics = haptics,
            downloads = downloads,
            onSongClick = onSongClick,
        )
    }
}

@Composable
internal fun BoxScope.playlistScrollbar(
    scrollState: LazyListState,
    headerItems: Int,
    modifier: Modifier = Modifier,
) {
    DraggableScrollbar(
        modifier =
            modifier
                .padding(
                    LocalPlayerAwareWindowInsets.current
                        .union(WindowInsets.ime)
                        .asPaddingValues(),
                ).align(Alignment.CenterEnd),
        scrollState = scrollState,
        headerItems = headerItems,
    )
}
