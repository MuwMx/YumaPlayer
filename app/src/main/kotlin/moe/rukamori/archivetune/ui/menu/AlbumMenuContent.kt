/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.PlayerConnection

@Composable
internal fun AlbumMenuContent(
    album: Album,
    songs: List<Song>,
    isLocalAlbum: Boolean,
    isInSpeedDial: Boolean,
    downloadState: Int,
    rotationAnimation: Float,
    playerConnection: PlayerConnection,
    context: Context,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSpeedDial: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownload: () -> Unit,
    onViewArtist: () -> Unit,
    onRefetch: () -> Unit,
) {
    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            AlbumMenuActionGrid(
                album = album,
                songs = songs,
                isLocalAlbum = isLocalAlbum,
                playerConnection = playerConnection,
                context = context,
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            AlbumMenuPrimaryActions(
                isInSpeedDial = isInSpeedDial,
                onPlayNext = {
                    onDismiss()
                    playerConnection.playNext(songs.map { it.toMediaItem() })
                },
                onAddToQueue = {
                    onDismiss()
                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                },
                onAddToPlaylist = onAddToPlaylist,
                onToggleSpeedDial = onToggleSpeedDial,
            )
        }

        if (!isLocalAlbum) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                AlbumMenuDownloadSection(
                    downloadState = downloadState,
                    onRemoveDownload = onRemoveDownload,
                    onDownload = onDownload,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            AlbumMenuArtistAndSyncSection(
                isLocalAlbum = isLocalAlbum,
                rotationAnimation = rotationAnimation,
                onViewArtist = onViewArtist,
                onRefetch = onRefetch,
            )
        }
    }
}
