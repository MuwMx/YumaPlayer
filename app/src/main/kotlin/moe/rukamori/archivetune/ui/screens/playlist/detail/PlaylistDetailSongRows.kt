/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.playlist.CachedSong
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistRowCapabilities
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSongRow

/**
 * SONG origin row (Room Song → SongListItem + SongMenu) for auto and top
 * playlist routes. Mirrors AutoPlaylistTrackRow (AutoPlaylistTrackRows.kt:
 * 58-93) minus download badge plumbing (the wiring layer owns that via
 * PlaylistDownloadStateHolder).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongPlaylistOriginRow(
    song: Song,
    navController: NavController,
    menuState: MenuState,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.ReadOnly,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SongListItem(
            song = song,
            isActive = song.song.id == mediaMetadataId,
            isPlaying = isPlaying,
            showInLibraryIcon = true,
            isSelected = selected,
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = song,
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
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}

/**
 * CACHE origin row (CachedSong → SongListItem with cache-status subtitle +
 * SongMenu fromCache). Mirrors CachePlaylistScreen.kt:351-416.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CachePlaylistOriginRow(
    cachedSong: CachedSong,
    navController: NavController,
    menuState: MenuState,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partiallyLabel = stringResource(R.string.partially_cached)
    val statusText =
        if (cachedSong.isFullyCached) {
            "${cachedSong.source} • ${cachedSong.formattedSize}"
        } else {
            "${cachedSong.source} • $partiallyLabel • ${cachedSong.formattedSize}"
        }
    PlaylistSongRow(
        capabilities = PlaylistRowCapabilities.ReadOnly,
        isScrollInProgress = false,
        modifier = modifier,
    ) {
        SongListItem(
            song = cachedSong.song,
            isActive = cachedSong.id == mediaMetadataId,
            isPlaying = isPlaying,
            showInLibraryIcon = true,
            isDownloaded = cachedSong.isFullyCached,
            viewCountText = statusText,
            isSelected = selected,
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = cachedSong.song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                                isFromCache = true,
                            )
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}
