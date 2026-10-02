/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.ui.component.DefaultDialog
import java.time.LocalDateTime

@Composable
internal fun ImportPlaylistDuplicateDialog(
    existingPlaylistId: String,
    currentPlaylistName: String,
    songIds: List<String>?,
    isProcessingDuplicate: Boolean,
    onProcessingChange: (Boolean) -> Unit,
    onGetSong: suspend () -> List<String>,
    onReset: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    DefaultDialog(
        onDismiss = {
            if (!isProcessingDuplicate) {
                onReset()
            }
        },
        title = { Text(text = stringResource(R.string.import_playlist)) },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.already_in_playlist))
                if (isProcessingDuplicate) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularWavyProgressIndicator()
                }
            }
        },
        buttons = {
            TextButton(
                enabled = !isProcessingDuplicate,
                onClick = {
                    onReset()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(text = stringResource(android.R.string.cancel)) }

            TextButton(
                enabled = !isProcessingDuplicate,
                onClick = {
                    onProcessingChange(true)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val ids = songIds ?: onGetSong()
                            if (ids.isEmpty()) {
                                onMessage(context.getString(R.string.import_failed))
                                withContext(Dispatchers.Main) {
                                    onReset()
                                    onDismiss()
                                }
                                return@launch
                            }

                            val playlist = database.playlist(existingPlaylistId).firstOrNull()
                            if (playlist != null) {
                                if (playlist.playlist.bookmarkedAt == null) {
                                    database.query {
                                        update(
                                            playlist.playlist.copy(
                                                bookmarkedAt = LocalDateTime.now(),
                                                lastUpdateTime = LocalDateTime.now(),
                                            ),
                                        )
                                    }
                                }
                                val existingSongIds =
                                    database
                                        .playlistSongs(playlist.id)
                                        .firstOrNull()
                                        ?.map { it.song.id }
                                        ?.toSet() ?: emptySet()
                                val newSongIds = ids.filterNot { it in existingSongIds }

                                if (newSongIds.isEmpty()) {
                                    onMessage(context.getString(R.string.playlist_synced))
                                } else {
                                    database.transaction {
                                        var position = playlist.songCount
                                        newSongIds.forEach { songId ->
                                            insert(
                                                PlaylistSongMap(
                                                    songId = songId,
                                                    playlistId = playlist.id,
                                                    position = position++,
                                                ),
                                            )
                                        }
                                    }
                                    onMessage(context.getString(R.string.playlist_synced))
                                }
                            } else {
                                onMessage(context.getString(R.string.import_failed))
                            }

                            withContext(Dispatchers.Main) {
                                onReset()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                            withContext(Dispatchers.Main) {
                                onReset()
                                onDismiss()
                            }
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(text = stringResource(R.string.update_button)) }

            TextButton(
                enabled = !isProcessingDuplicate,
                onClick = {
                    onProcessingChange(true)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val ids = songIds ?: onGetSong()
                            if (ids.isEmpty()) {
                                onMessage(context.getString(R.string.import_failed))
                                withContext(Dispatchers.Main) {
                                    onReset()
                                    onDismiss()
                                }
                                return@launch
                            }

                            val newPlaylist =
                                PlaylistEntity(
                                    name = currentPlaylistName,
                                    browseId = null,
                                    bookmarkedAt = LocalDateTime.now(),
                                )
                            database.query { insert(newPlaylist) }

                            val playlist = database.playlist(newPlaylist.id).firstOrNull()
                            if (playlist != null) {
                                database.addSongToPlaylist(playlist, ids)
                                onMessage(context.getString(R.string.playlist_synced))
                            } else {
                                onMessage(context.getString(R.string.import_failed))
                            }

                            withContext(Dispatchers.Main) {
                                onReset()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                            withContext(Dispatchers.Main) {
                                onReset()
                                onDismiss()
                            }
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(text = stringResource(R.string.import_playlist)) }
        },
    )
}
