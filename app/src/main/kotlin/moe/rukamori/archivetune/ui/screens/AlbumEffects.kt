/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads

@Composable
fun AlbumEffects(
    albumWithSongs: AlbumWithSongs?,
    downloadUtil: DownloadUtil,
    downloadUiState: AlbumDownloadUiState,
) {
    AlbumDownloadSyncEffect(
        albumWithSongs = albumWithSongs,
        downloadUtil = downloadUtil,
        onDownloadsChange = { downloadUiState.downloads = it },
        onDownloadStateChange = { downloadUiState.downloadState = it },
    )
    AlbumDownloadPausedEffect(
        downloadState = downloadUiState.downloadState,
        onResetPaused = { downloadUiState.downloadsPaused = false },
    )
}
@Composable
fun rememberAlbumActions(
    albumWithSongs: AlbumWithSongs?,
    downloadUiState: AlbumDownloadUiState,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    menuState: MenuState,
    navController: NavController,
    context: Context,
): AlbumActions {
    val downloadState = downloadUiState.downloadState
    val downloads = downloadUiState.downloads
    return remember(
        albumWithSongs,
        downloadState,
        downloads,
        context,
        playerConnection,
        database,
        menuState,
        navController,
    ) {
        val currentAlbumWithSongs = albumWithSongs
        if (currentAlbumWithSongs != null) {
            AlbumActions(
                onPlay = {
                    playerConnection.playQueue(
                        LocalAlbumRadio(currentAlbumWithSongs),
                    )
                },
                onShuffle = {
                    playerConnection.playQueue(
                        LocalAlbumRadio(currentAlbumWithSongs.copy(songs = currentAlbumWithSongs.songs.shuffled())),
                    )
                },
                onSongClick = { index ->
                    playerConnection.playQueue(
                        LocalAlbumRadio(currentAlbumWithSongs, startIndex = index),
                    )
                },
                onLike = {
                    database.query {
                        update(currentAlbumWithSongs.album.toggleLike())
                    }
                },
                onMenu = {
                    menuState.show {
                        AlbumMenu(
                            originalAlbum =
                                Album(
                                    currentAlbumWithSongs.album,
                                    currentAlbumWithSongs.artists,
                                ),
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                onDownload = {
                    when (downloadState) {
                        HeaderDownloadState.Completed -> {
                            sendRemoveDownloads(
                                context = context,
                                songIds = currentAlbumWithSongs.songs.map { it.id },
                            )
                        }

                        else -> {
                            downloadUiState.dismissed = false
                            sendAddMissingDownloads(
                                context = context,
                                songs =
                                    currentAlbumWithSongs.songs.map {
                                        HeaderDownloadItem(
                                            id = it.id,
                                            title = it.song.title,
                                        )
                                    },
                                downloads = downloads,
                            )
                        }
                    }
                },
            )
        } else {
            AlbumActions()
        }
    }
}
@Composable
fun AlbumTopBarTitle(
    selection: Boolean,
    selectedCount: Int,
    title: String,
    showTitle: Boolean,
) {
    if (selection) {
        Text(
            text = pluralStringResource(R.plurals.n_song, selectedCount, selectedCount),
            style = MaterialTheme.typography.titleLarge,
        )
    } else if (showTitle) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
fun AlbumTopBarNavigationIcon(
    selectionState: AlbumSelectionState,
    navController: NavController,
) {
    IconButton(
        onClick = {
            if (selectionState.selection) {
                selectionState.clearSelection()
            } else {
                navController.navigateUp()
            }
        },
        onLongClick = {
            if (!selectionState.selection) {
                navController.backToMain()
            }
        },
    ) {
        Icon(
            painter =
                painterResource(
                    if (selectionState.selection) R.drawable.close else R.drawable.arrow_back,
                ),
            contentDescription = null,
        )
    }
}
@Composable
fun AlbumSelectionTopBarActions(
    selectionState: AlbumSelectionState,
    menuState: MenuState,
) {
    val count = selectionState.selectedCount
    IconButton(
        onClick = selectionState::toggleSelectAll,
        onLongClick = {},
    ) {
        Icon(
            painter =
                painterResource(
                    if (count == selectionState.wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                ),
            contentDescription = null,
        )
    }

    IconButton(
        onClick = {
            menuState.show {
                SelectionSongMenu(
                    songSelection =
                        selectionState.wrappedSongs
                            .filter { it.isSelected }
                            .map { it.item },
                    onDismiss = menuState::dismiss,
                    clearAction = selectionState::clearSelection,
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
}
