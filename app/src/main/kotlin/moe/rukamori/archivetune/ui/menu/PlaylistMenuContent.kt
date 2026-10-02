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
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SpeedDialPin

@Composable
internal fun PlaylistMenuContent(
    playlist: Playlist,
    dbPlaylist: Playlist?,
    songs: List<Song>,
    isInSpeedDial: Boolean,
    playlistPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    editable: Boolean,
    autoPlaylist: Boolean?,
    downloadPlaylist: Boolean?,
    downloadState: Int,
    syncProgress: PlaylistSyncProgressUi?,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
    context: Context,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onShowEditDialog: () -> Unit,
    onShowAssignTagsDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onSyncPlaylist: () -> Unit,
    onShowHidePlaylistDialog: () -> Unit,
    onShowDeletePlaylistDialog: () -> Unit,
    onUnhidePlaylist: () -> Unit,
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            PlaylistMenuActionGrid(
                songs = songs,
                playlist = playlist,
                dbPlaylist = dbPlaylist,
                playerConnection = playerConnection,
                context = context,
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            PlaylistMenuPrimaryListItems(
                playlist = playlist,
                songs = songs,
                isInSpeedDial = isInSpeedDial,
                playlistPin = playlistPin,
                speedDialPins = speedDialPins,
                editable = editable,
                autoPlaylist = autoPlaylist,
                downloadPlaylist = downloadPlaylist,
                playerConnection = playerConnection,
                coroutineScope = coroutineScope,
                onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
                onDismiss = onDismiss,
                onShowEditDialog = onShowEditDialog,
                onShowAssignTagsDialog = onShowAssignTagsDialog,
            )
        }

        if (downloadPlaylist != true) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PlaylistMenuDownloadSection(
                    downloadState = downloadState,
                    songs = songs,
                    context = context,
                    onShowRemoveDownloadDialog = onShowRemoveDownloadDialog,
                )
            }
        }

        if (autoPlaylist != true) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PlaylistMenuSyncHideDeleteSection(
                    playlist = playlist,
                    onSyncClick = {
                        if (syncProgress == null) {
                            onSyncPlaylist()
                        }
                    },
                    onHideClick = {
                        if (playlist.playlist.isHidden) {
                            onUnhidePlaylist()
                        } else {
                            onShowHidePlaylistDialog()
                        }
                    },
                    onDeleteClick = onShowDeletePlaylistDialog,
                )
            }
        }

        playlist.playlist.shareLink?.let { shareLink ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PlaylistMenuShareSection(
                    shareLink = shareLink,
                    context = context,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
