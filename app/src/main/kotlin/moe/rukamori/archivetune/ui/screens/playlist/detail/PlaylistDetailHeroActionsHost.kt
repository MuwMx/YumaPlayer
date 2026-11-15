/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.playlist.source.PlaylistCapabilities
import moe.rukamori.archivetune.playlist.source.PlaylistTracksSource
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistDownloadStateHolder
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

/**
 * Hero action-row wiring, extracted from PlaylistDetailScreen to keep it
 * under the 250-line rule. Play/shuffle dispatch per origin through
 * [onPlaylistDetailPlay]/[onPlaylistDetailShuffle]; download follows the
 * legacy Online/Auto/Top shape (Completed -> remove downloads, else ->
 * add missing and un-dismiss the progress toolbar).
 *
 * LOCAL EDIT SLOT / ONLINE LIKE SLOT: those leading buttons join via
 * [leadingAction] with their route flips — keep this signature stable.
 */
@Composable
internal fun PlaylistDetailHeroActionsHost(
    capabilities: PlaylistCapabilities,
    source: PlaylistTracksSource,
    playerConnection: PlayerConnection,
    downloadHolder: PlaylistDownloadStateHolder,
    title: String,
    tracks: List<PlaylistTrack>,
    leadingAction: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    // Legacy heroes route a Completed download button through a confirm
    // dialog (Top/Auto/Local); never remove downloads un-confirmed.
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        PlaylistDetailRemoveDownloadDialog(
            playlistName = title,
            songIds = tracks.map { it.id },
            onDismiss = { showRemoveDownloadDialog = false },
        )
    }

    PlaylistDetailHeroActions(
        capabilities = capabilities,
        downloadState = downloadHolder.downloadState,
        onPlay = {
            onPlaylistDetailPlay(
                source = source,
                playerConnection = playerConnection,
                title = title,
                tracks = tracks,
            )
        },
        onShuffle = {
            onPlaylistDetailShuffle(
                source = source,
                playerConnection = playerConnection,
                title = title,
                tracks = tracks,
            )
        },
        onDownload = {
            when (downloadHolder.downloadState) {
                HeaderDownloadState.Completed ->
                    showRemoveDownloadDialog = true

                else -> {
                    downloadHolder.dismissed = false
                    sendAddMissingDownloads(
                        context = context,
                        songs =
                            tracks.map { track ->
                                HeaderDownloadItem(
                                    id = track.id,
                                    title = track.title,
                                )
                            },
                        downloads = downloadHolder.downloads,
                    )
                }
            }
        },
        onAddToQueue = {
            onPlaylistDetailAddToQueue(
                playerConnection = playerConnection,
                tracks = tracks,
            )
        },
        leadingAction = leadingAction,
    )
}
