/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
internal fun PlaylistMenuPrimaryListItems(
    playlist: Playlist,
    songs: List<Song>,
    isInSpeedDial: Boolean,
    playlistPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    editable: Boolean,
    autoPlaylist: Boolean?,
    downloadPlaylist: Boolean?,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowEditDialog: () -> Unit,
    onShowAssignTagsDialog: () -> Unit,
) {
    val actionItemCount =
        (if (playlist.playlist.browseId != null) 1 else 0) +
            1 +
            1 +
            1 +
            (if (editable && autoPlaylist != true) 1 else 0) +
            (if (autoPlaylist != true && downloadPlaylist != true) 1 else 0)
    val browseIdOffset = if (playlist.playlist.browseId != null) 1 else 0

    MenuSurfaceSection {
        playlist.playlist.browseId?.let { browseId ->
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.start_radio)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                    )
                },
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.playlist(browseId).getOrNull()?.playlist?.let { playlistItem ->
                            playlistItem.radioEndpoint?.let { radioEndpoint ->
                                withContext(Dispatchers.Main) {
                                    playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                }
                            }
                        }
                    }
                    onDismiss()
                },
                index = 0,
                count = actionItemCount,
            )
        }

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
                    playerConnection.playNext(songs.map { it.toMediaItem() })
                }
                onDismiss()
            },
            index = browseIdOffset,
            count = actionItemCount,
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
                onDismiss()
                playerConnection.addToQueue(songs.map { it.toMediaItem() })
            },
            index = browseIdOffset + 1,
            count = actionItemCount,
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
                val updatedPins = toggleSpeedDialPin(speedDialPins, playlistPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                onDismiss()
            },
            index = browseIdOffset + 2,
            count = actionItemCount,
        )

        if (editable && autoPlaylist != true) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.edit)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                onClick = onShowEditDialog,
                index = browseIdOffset + 3,
                count = actionItemCount,
            )
        }

        if (autoPlaylist != true && downloadPlaylist != true) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.manage_tags)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.style),
                        contentDescription = null,
                    )
                },
                onClick = onShowAssignTagsDialog,
                index = browseIdOffset + 3 + (if (editable && autoPlaylist != true) 1 else 0),
                count = actionItemCount,
            )
        }
    }
}
