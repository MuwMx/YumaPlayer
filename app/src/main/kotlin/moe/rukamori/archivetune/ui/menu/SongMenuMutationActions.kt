/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.viewmodels.CachePlaylistViewModel

@Composable
internal fun SongMenuMutationHistoryItem(
    event: Event,
    database: MusicDatabase,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
) {
    NewMenuItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.remove_from_history),
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            database.query {
                delete(event)
            }
        },
        index = index,
        count = count,
    )
}

@Composable
internal fun SongMenuMutationPlaylistItem(
    playlistSong: PlaylistSong,
    playlistBrowseId: String?,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    context: Context,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
) {
    NewMenuItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.remove_from_playlist),
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )
        },
        onClick = {
            val map = playlistSong.map
            coroutineScope.launch(Dispatchers.IO) {
                val browseId = playlistBrowseId
                if (browseId != null) {
                    val remoteResult = removeSongFromRemotePlaylist(browseId, map)
                    if (remoteResult.isFailure) {
                        withContext(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.error_unknown),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            onDismiss()
                        }
                        return@launch
                    }
                }
                database.withTransaction {
                    val maxPosition = maxPlaylistSongPosition(map.playlistId) ?: map.position
                    if (map.position < maxPosition) {
                        move(map.playlistId, map.position, maxPosition)
                    }
                    delete(map)
                }
                withContext(Dispatchers.Main) {
                    onDismiss()
                }
            }
        },
        index = index,
        count = count,
    )
}

@Composable
internal fun SongMenuMutationCacheItem(
    song: Song,
    cacheViewModel: CachePlaylistViewModel,
    index: Int,
    count: Int,
    onDismiss: () -> Unit,
) {
    NewMenuItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.remove_from_cache),
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.delete),
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            cacheViewModel.removeSongFromCache(song.id)
        },
        index = index,
        count = count,
    )
}
