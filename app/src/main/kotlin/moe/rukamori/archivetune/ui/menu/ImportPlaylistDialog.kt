/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import java.time.LocalDateTime

@Composable
fun ImportPlaylistDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    playlistTitle: String,
    browseId: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentPlaylistName by remember(playlistTitle) { mutableStateOf(playlistTitle) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var existingPlaylistId by remember { mutableStateOf<String?>(null) }
    var isProcessingDuplicate by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        coroutineScope.launch {
            if (snackbarHostState != null) {
                snackbarHostState.showSnackbar(message)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun resetState() {
        songIds = null
        isImporting = false
        showDuplicateDialog = false
        existingPlaylistId = null
        isProcessingDuplicate = false
    }

    if (isVisible) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.import_playlist)) },
            initialTextFieldValue = TextFieldValue(text = playlistTitle),
            autoFocus = false,
            onDismiss = {
                resetState()
                onDismiss()
            },
            extraContent = {
                if (isImporting) {
                    CircularWavyProgressIndicator()
                }
            },
            onDone = { finalName ->
                currentPlaylistName = finalName
                isImporting = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val ids = onGetSong()
                        songIds = ids

                        if (ids.isEmpty()) {
                            showMessage(context.getString(R.string.import_failed))
                            withContext(Dispatchers.Main) {
                                resetState()
                                onDismiss()
                            }
                            return@launch
                        }

                        if (browseId != null) {
                            val existing = database.playlistByBrowseId(browseId).firstOrNull()
                            if (existing != null) {
                                if (existing.playlist.bookmarkedAt == null) {
                                    database.query {
                                        update(
                                            existing.playlist.copy(
                                                bookmarkedAt = LocalDateTime.now(),
                                                lastUpdateTime = LocalDateTime.now(),
                                            ),
                                        )
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    existingPlaylistId = existing.playlist.id
                                    isImporting = false
                                    showDuplicateDialog = true
                                }
                                return@launch
                            }
                        }

                        val newPlaylist =
                            PlaylistEntity(
                                name = finalName,
                                browseId = browseId,
                                isEditable = browseId == null,
                                bookmarkedAt = LocalDateTime.now(),
                            )
                        database.query { insert(newPlaylist) }

                        val playlist = database.playlist(newPlaylist.id).firstOrNull()
                        if (playlist != null) {
                            database.addSongToPlaylist(playlist, ids)
                        }

                        showMessage(context.getString(R.string.playlist_synced))
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showMessage(context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}")
                        withContext(Dispatchers.Main) {
                            resetState()
                            onDismiss()
                        }
                    }
                }
            },
        )
    }

    if (showDuplicateDialog && existingPlaylistId != null) {
        ImportPlaylistDuplicateDialog(
            existingPlaylistId = existingPlaylistId!!,
            currentPlaylistName = currentPlaylistName,
            songIds = songIds,
            isProcessingDuplicate = isProcessingDuplicate,
            onProcessingChange = { isProcessingDuplicate = it },
            onGetSong = onGetSong,
            onReset = { resetState() },
            onMessage = { showMessage(it) },
            onDismiss = onDismiss,
        )
    }
}
