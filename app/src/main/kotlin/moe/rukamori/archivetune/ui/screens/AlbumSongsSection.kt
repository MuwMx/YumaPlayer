/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.albumSongsSection(
    wrappedSongs: List<WrappedSong>,
    selection: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    activeMediaId: String?,
    isPlaying: Boolean,
    onSongClick: (startIndex: Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSongMenu: (Song) -> Unit,
) {
    item(key = ALBUM_KEY_SONGS_HEADER, contentType = CONTENT_TYPE_ALBUM_SONGS_HEADER) {
        NavigationTitle(
            title = stringResource(R.string.songs),
        )
    }

    itemsIndexed(
        items = wrappedSongs,
        key = { index, song -> "${song.item.id}_$index" },
        contentType = { _, _ -> CONTENT_TYPE_ALBUM_SONG },
    ) { index, songWrapper ->
        AlbumSongRow(
            songWrapper = songWrapper,
            index = index,
            selection = selection,
            isActive = songWrapper.item.id == activeMediaId,
            isPlaying = isPlaying,
            onSongClick = onSongClick,
            onTogglePlayPause = onTogglePlayPause,
            onSelectionChange = onSelectionChange,
            onSongMenu = onSongMenu,
            allWrappedSongs = wrappedSongs,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumSongRow(
    songWrapper: WrappedSong,
    index: Int,
    selection: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onSongClick: (startIndex: Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    onSongMenu: (Song) -> Unit,
    allWrappedSongs: List<WrappedSong>,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    SongListItem(
        song = songWrapper.item,
        albumIndex = index + 1,
        isActive = isActive,
        isPlaying = isPlaying,
        showInLibraryIcon = true,
        trailingContent = {
            IconButton(
                onClick = { onSongMenu(songWrapper.item) },
                onLongClick = {},
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                )
            }
        },
        isSelected = songWrapper.isSelected && selection,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (!selection) {
                            if (isActive) {
                                onTogglePlayPause()
                            } else {
                                onSongClick(index)
                            }
                        } else {
                            songWrapper.isSelected = !songWrapper.isSelected
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!selection) {
                            onSelectionChange(true)
                        }
                        allWrappedSongs.forEach { it.isSelected = false }
                        songWrapper.isSelected = true
                    },
                ),
    )
}
