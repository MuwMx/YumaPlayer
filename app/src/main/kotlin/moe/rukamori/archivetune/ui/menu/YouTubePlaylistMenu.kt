/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val syncUtils = LocalSyncUtils.current
    val dbPlaylist by database.playlistByBrowseId(playlist.id).collectAsState(initial = null)
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val playlistPin =
        remember(dbPlaylist?.playlist?.id) {
            dbPlaylist?.playlist?.id?.let { SpeedDialPin(type = SpeedDialPinType.PLAYLIST, id = it) }
        }
    val isInSpeedDial =
        remember(speedDialPins, playlistPin) {
            playlistPin?.let { pin -> speedDialPins.any { it.type == pin.type && it.id == pin.id } } == true
        }

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }
    val notAddedList by remember { mutableStateOf(mutableListOf<MediaMetadata>()) }

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
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
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    YouTubePlaylistAddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        playlist = playlist,
        songs = songs,
        database = database,
        context = context,
        onDismiss = { showChoosePlaylistDialog = false },
    )

    YouTubePlaylistRemoveDownloadDialog(
        isVisible = showRemoveDownloadDialog,
        playlistTitle = playlist.title,
        songs = songs,
        context = context,
        onDismiss = { showRemoveDownloadDialog = false },
    )

    ImportPlaylistDialog(
        isVisible = showImportPlaylistDialog,
        onGetSong = {
            val allSongs =
                songs
                    .ifEmpty {
                        YouTube
                            .playlist(playlist.id)
                            .completed()
                            .getOrNull()
                            ?.songs
                            .orEmpty()
                    }.map {
                        it.toMediaMetadata()
                    }
            database.withTransaction {
                allSongs.forEach(::insert)
            }
            allSongs.map { it.id }
        },
        playlistTitle = playlist.title,
        browseId = playlist.id,
        snackbarHostState = snackbarHostState,
        onDismiss = { showImportPlaylistDialog = false },
    )

    YouTubePlaylistErrorDialog(
        isVisible = showErrorPlaylistAddDialog,
        notAddedList = notAddedList,
        onDismiss = {
            showErrorPlaylistAddDialog = false
            onDismiss()
        },
    )

    YouTubePlaylistMenuHeader(
        playlist = playlist,
        songs = songs,
        dbPlaylist = dbPlaylist,
        database = database,
        coroutineScope = coroutineScope,
    )

    Spacer(modifier = Modifier.height(16.dp))

    YouTubePlaylistMenuContent(
        playlist = playlist,
        songs = songs,
        dbPlaylist = dbPlaylist,
        isInSpeedDial = isInSpeedDial,
        playlistPin = playlistPin,
        speedDialPins = speedDialPins,
        onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
        downloadState = downloadState,
        downloads = downloadUtil.downloads.value,
        canSelect = canSelect,
        playerConnection = playerConnection,
        database = database,
        syncUtils = syncUtils,
        coroutineScope = coroutineScope,
        context = context,
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss,
        selectAction = selectAction,
        onShowChoosePlaylistDialog = { showChoosePlaylistDialog = true },
        onShowImportPlaylistDialog = { showImportPlaylistDialog = true },
        onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
    )
}
