/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
internal fun YouTubePlaylistPrimaryMenuItems(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    isInSpeedDial: Boolean,
    playlistPin: SpeedDialPin?,
    speedDialPins: List<SpeedDialPin>,
    onSpeedDialSongIdsChange: (String) -> Unit,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    context: Context,
    snackbarHostState: SnackbarHostState?,
    onDismiss: () -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowImportPlaylistDialog: () -> Unit,
) {
    val actionCount = 6
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.play_next)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.playlist_play),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch {
                    songs
                        .ifEmpty {
                            withContext(Dispatchers.IO) {
                                YouTube
                                    .playlist(playlist.id)
                                    .completed()
                                    .getOrNull()
                                    ?.songs
                                    .orEmpty()
                            }
                        }.let { list ->
                            playerConnection.playNext(list.map { it.toMediaItem() })
                        }
                }
                onDismiss()
            },
            index = 0,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.add_to_queue)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch {
                    songs
                        .ifEmpty {
                            withContext(Dispatchers.IO) {
                                YouTube
                                    .playlist(playlist.id)
                                    .completed()
                                    .getOrNull()
                                    ?.songs
                                    .orEmpty()
                            }
                        }.let { list ->
                            playerConnection.addToQueue(list.map { it.toMediaItem() })
                        }
                }
                onDismiss()
            },
            index = 1,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.add_to_playlist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.playlist_add),
                    contentDescription = null,
                )
            },
            onClick = onShowChoosePlaylistDialog,
            index = 2,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (isInSpeedDial) {
                                R.string.remove_from_speed_dial
                            } else {
                                R.string.pin_to_speed_dial
                            },
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch {
                    val pin =
                        if (isInSpeedDial) {
                            playlistPin
                        } else {
                            val localPlaylistId =
                                withContext(Dispatchers.IO) {
                                    database
                                        .playlistByBrowseId(playlist.id)
                                        .first()
                                        ?.playlist
                                        ?.id
                                        ?: run {
                                            val playlistEntity =
                                                PlaylistEntity(
                                                    name = playlist.title,
                                                    browseId = playlist.id,
                                                    thumbnailUrl = playlist.thumbnail,
                                                    isEditable = false,
                                                    remoteSongCount =
                                                        playlist.songCountText?.let {
                                                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                                        },
                                                    playEndpointParams = playlist.playEndpoint?.params,
                                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                    radioEndpointParams = playlist.radioEndpoint?.params,
                                                )
                                            database.transaction {
                                                insert(playlistEntity)
                                            }
                                            playlistEntity.id
                                        }
                                }
                            SpeedDialPin(type = SpeedDialPinType.PLAYLIST, id = localPlaylistId)
                        }

                    if (pin == null) return@launch

                    val updatedPins = toggleSpeedDialPin(speedDialPins, pin)
                    onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                    onDismiss()
                }
            },
            index = 3,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.import_playlist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                )
            },
            onClick = onShowImportPlaylistDialog,
            index = 4,
            count = actionCount,
        )

        YouTubePlaylistSyncItem(
            playlist = playlist,
            dbPlaylist = dbPlaylist,
            database = database,
            syncUtils = syncUtils,
            coroutineScope = coroutineScope,
            context = context,
            snackbarHostState = snackbarHostState,
            index = 5,
            count = actionCount,
        )
    }
}
