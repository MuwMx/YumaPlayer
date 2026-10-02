/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import java.time.LocalDateTime

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val playlistPin = remember(playlist.id) { SpeedDialPin(type = SpeedDialPinType.PLAYLIST, id = playlist.id) }
    val isInSpeedDial =
        remember(speedDialPins, playlistPin) {
            speedDialPins.any { it.type == playlistPin.type && it.id == playlistPin.id }
        }
    var songs by remember { mutableStateOf(emptyList<Song>()) }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    var syncProgress by remember { mutableStateOf<PlaylistSyncProgressUi?>(null) }
    var syncJob by remember { mutableStateOf<Job?>(null) }
    val editable: Boolean = playlist.playlist.isEditable == true

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showHidePlaylistDialog by remember { mutableStateOf(false) }
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var showAssignTagsDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditPlaylistDialog(
            initialName = playlist.playlist.name,
            onDismiss = { showEditDialog = false },
            onSave = { name ->
                onDismiss()
                database.query {
                    update(
                        playlist.playlist.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now(),
                        ),
                    )
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                }
            },
        )
    }

    PlaylistMenuRemoveDownloadDialog(
        isVisible = showRemoveDownloadDialog,
        playlistName = playlist.playlist.name,
        songs = songs,
        context = context,
        onDismiss = { showRemoveDownloadDialog = false },
    )

    if (showAssignTagsDialog) {
        AssignTagsDialog(
            playlistId = playlist.id,
            onDismiss = {
                showAssignTagsDialog = false
                onDismiss()
            },
        )
    }

    PlaylistMenuHideDialog(
        isVisible = showHidePlaylistDialog,
        playlist = playlist,
        database = database,
        onDismiss = { showHidePlaylistDialog = false },
        onDismissMenu = onDismiss,
    )

    PlaylistMenuDeleteDialog(
        isVisible = showDeletePlaylistDialog,
        playlist = playlist,
        database = database,
        coroutineScope = coroutineScope,
        onDismiss = { showDeletePlaylistDialog = false },
        onDismissMenu = onDismiss,
    )

    PlaylistMenuHeader(
        playlist = playlist,
        dbPlaylist = dbPlaylist,
        database = database,
    )

    Spacer(modifier = Modifier.height(16.dp))

    PlaylistMenuContent(
        playlist = playlist,
        dbPlaylist = dbPlaylist,
        songs = songs,
        isInSpeedDial = isInSpeedDial,
        playlistPin = playlistPin,
        speedDialPins = speedDialPins,
        editable = editable,
        autoPlaylist = autoPlaylist,
        downloadPlaylist = downloadPlaylist,
        downloadState = downloadState,
        syncProgress = syncProgress,
        playerConnection = playerConnection,
        coroutineScope = coroutineScope,
        context = context,
        onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
        onDismiss = onDismiss,
        onShowEditDialog = { showEditDialog = true },
        onShowAssignTagsDialog = { showAssignTagsDialog = true },
        onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
        onSyncPlaylist = {
            syncJob?.cancel()
            syncJob =
                startPlaylistSyncToYouTube(
                    coroutineScope = coroutineScope,
                    context = context,
                    playlist = playlist,
                    songs = songs,
                    database = database,
                    syncUtils = syncUtils,
                    onProgressUpdate = { syncProgress = it },
                    onSuccess = onDismiss,
                )
        },
        onShowHidePlaylistDialog = { showHidePlaylistDialog = true },
        onShowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
        onUnhidePlaylist = {
            onDismiss()
            database.query {
                update(playlist.playlist.copy(isHidden = false))
            }
        },
    )

    syncProgress?.let { progress ->
        LoadingScreen(
            isVisible = true,
            value = progress.percent,
            title = stringResource(R.string.sync_playlist),
            stepText =
                if (progress.totalSongs > 0) {
                    stringResource(
                        R.string.playlist_sync_progress_step,
                        progress.completedSongs,
                        progress.totalSongs,
                    )
                } else {
                    stringResource(R.string.please_wait)
                },
            indeterminate = progress.totalSongs <= 0,
            onCancel = {
                syncJob?.cancel()
                syncJob = null
                syncProgress = null
            },
        )
    }
}
