/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun YouTubePlaylistSyncItem(
    playlist: PlaylistItem,
    dbPlaylist: Playlist?,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    context: Context,
    snackbarHostState: SnackbarHostState?,
    index: Int,
    count: Int,
) {
    NewMenuItem(
        headlineContent = { Text(text = stringResource(R.string.yt_sync)) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
            )
        },
        trailingContent = {
            val checked = dbPlaylist?.playlist?.isAutoSync ?: false
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val currentDbPlaylist = dbPlaylist
                            if (currentDbPlaylist?.playlist == null) {
                                val playlistPage = YouTube.playlist(playlist.id).completed().getOrNull()
                                val fetchedSongs = playlistPage?.songs.orEmpty()

                                if (fetchedSongs.isEmpty() && newValue) {
                                    withContext(Dispatchers.Main) {
                                        if (snackbarHostState != null) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.import_failed))
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.import_failed),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                    return@launch
                                }

                                database.transaction {
                                    val playlistEntity =
                                        PlaylistEntity(
                                            name = playlist.title,
                                            browseId = playlist.id,
                                            thumbnailUrl = playlist.thumbnail,
                                            isEditable = false,
                                            isAutoSync = newValue,
                                            remoteSongCount =
                                                playlist.songCountText?.let {
                                                    Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                                },
                                            playEndpointParams = playlist.playEndpoint?.params,
                                            shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                            radioEndpointParams = playlist.radioEndpoint?.params,
                                        )
                                    insert(playlistEntity)
                                    fetchedSongs.forEach { song -> insert(song.toMediaMetadata()) }
                                    fetchedSongs
                                        .mapIndexed { i, song ->
                                            PlaylistSongMap(
                                                songId = song.id,
                                                playlistId = playlistEntity.id,
                                                position = i,
                                                setVideoId = song.setVideoId,
                                            )
                                        }.forEach(::insert)
                                }

                                if (newValue) {
                                    withContext(Dispatchers.Main) {
                                        if (snackbarHostState != null) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.playlist_synced),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                }
                            } else {
                                val existing = currentDbPlaylist.playlist
                                database.query {
                                    update(existing.copy(isAutoSync = newValue))
                                }

                                if (newValue) {
                                    syncUtils.syncAutoSyncPlaylists()
                                    withContext(Dispatchers.Main) {
                                        if (snackbarHostState != null) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(R.string.playlist_synced),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                val errorMsg =
                                    context.getString(R.string.import_failed) + ": ${e.message ?: "Unknown error"}"
                                if (snackbarHostState != null) {
                                    snackbarHostState.showSnackbar(errorMsg)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            errorMsg,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        }
                    }
                },
            )
        },
        index = index,
        count = count,
    )
}
