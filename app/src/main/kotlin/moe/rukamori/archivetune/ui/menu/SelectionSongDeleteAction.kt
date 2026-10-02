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
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun SelectionSongPlaylistDeleteMenuItem(
    positions: List<PlaylistSongMap>,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    NewMenuItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.delete),
                color = MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        onClick = {
            coroutineScope.launch(Dispatchers.IO) {
                if (positions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onDismiss()
                        clearAction()
                    }
                    return@launch
                }

                val browseIdByPlaylistId = HashMap<String, String?>()
                for (playlistId in positions.asSequence().map { it.playlistId }.distinct()) {
                    browseIdByPlaylistId[playlistId] = database.getPlaylistById(playlistId)?.playlist?.browseId
                }

                val failed = LinkedHashSet<PlaylistSongMap>()
                val succeeded = ArrayList<PlaylistSongMap>(positions.size)

                for (cur in positions) {
                    val browseId = browseIdByPlaylistId[cur.playlistId]
                    if (browseId != null) {
                        val remoteResult = removeSongFromRemotePlaylist(browseId, cur)
                        if (remoteResult.isFailure) {
                            failed += cur
                        } else {
                            succeeded += cur
                        }
                    } else {
                        succeeded += cur
                    }
                }

                if (succeeded.isNotEmpty()) {
                    database.withTransaction {
                        val offsetByPlaylistId = HashMap<String, Int>()
                        succeeded
                            .sortedWith(compareBy<PlaylistSongMap> { it.playlistId }.thenBy { it.position })
                            .forEach { cur ->
                                val offset = offsetByPlaylistId.getOrPut(cur.playlistId) { 0 }
                                move(cur.playlistId, cur.position - offset, Int.MAX_VALUE)
                                delete(cur.copy(position = Int.MAX_VALUE))
                                offsetByPlaylistId[cur.playlistId] = offset + 1
                            }
                    }
                }

                withContext(Dispatchers.Main) {
                    onDismiss()
                    clearAction()
                    if (failed.isNotEmpty()) {
                        Toast
                            .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        },
        index = 0,
        count = 1,
    )
}
