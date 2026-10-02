/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.DefaultDialog

@Composable
internal fun PlaylistMenuRemoveDownloadDialog(
    isVisible: Boolean,
    playlistName: String,
    songs: List<Song>,
    context: Context,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.remove_download_playlist_confirm, playlistName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onDismiss()
                    songs.forEach { song ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun PlaylistMenuHideDialog(
    isVisible: Boolean,
    playlist: Playlist,
    database: MusicDatabase,
    onDismiss: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    if (!isVisible) return
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.hide_playlist_confirm, playlist.playlist.name),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onDismissMenu()
                    database.query {
                        update(playlist.playlist.copy(isHidden = true))
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun PlaylistMenuDeleteDialog(
    isVisible: Boolean,
    playlist: Playlist,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    if (!isVisible) return
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onDismissMenu()
                    database.transaction {
                        if (playlist.playlist.bookmarkedAt != null) {
                            update(playlist.playlist.toggleLike())
                        }
                        delete(playlist.playlist)
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        playlist.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
