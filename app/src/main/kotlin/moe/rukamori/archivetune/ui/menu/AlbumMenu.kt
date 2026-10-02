/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.media3.exoplayer.offline.Download.STATE_STOPPED
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AlbumMenu(
    originalAlbum: Album,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val libraryAlbum by database.album(originalAlbum.id).collectAsState(initial = originalAlbum)
    val album = libraryAlbum ?: originalAlbum
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        database.albumSongs(album.id).collect {
            songs = it
        }
    }

    var downloadState by remember {
        mutableStateOf(STATE_STOPPED)
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == STATE_COMPLETED }) {
                    STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == STATE_QUEUED ||
                            downloads[it.id]?.state == STATE_DOWNLOADING ||
                            downloads[it.id]?.state == STATE_COMPLETED
                    }
                ) {
                    STATE_DOWNLOADING
                } else {
                    STATE_STOPPED
                }
        }
    }

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val albumPin = remember(album.id) { SpeedDialPin(type = SpeedDialPinType.ALBUM, id = album.id) }
    val isInSpeedDial =
        remember(speedDialPins, albumPin) {
            speedDialPins.any { it.type == albumPin.type && it.id == albumPin.id }
        }
    val isLocalAlbum = album.album.isLocal

    val splitArtists = rememberSplitArtists(album.artists, artistSeparators)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorPlaylistAddDialog by rememberSaveable { mutableStateOf(false) }
    val notAddedList by remember { mutableStateOf(mutableListOf<Song>()) }

    AlbumAddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        songs = songs,
        onDismiss = { showChoosePlaylistDialog = false },
    )

    AlbumErrorPlaylistAddDialog(
        isVisible = showErrorPlaylistAddDialog,
        notAddedList = notAddedList,
        onDismiss = {
            showErrorPlaylistAddDialog = false
            onDismiss()
        },
    )

    AlbumArtistSelectDialog(
        isVisible = showSelectArtistDialog,
        splitArtists = splitArtists,
        onDismiss = { showSelectArtistDialog = false },
        onSelectArtist = { artistId ->
            navController.navigate("artist/$artistId")
            showSelectArtistDialog = false
            onDismiss()
        },
    )

    AlbumMenuHeader(
        album = album,
        onToggleBookmark = {
            database.query {
                update(album.album.toggleLike())
            }
        },
    )

    Spacer(modifier = Modifier.height(16.dp))

    AlbumMenuContent(
        album = album,
        songs = songs,
        isLocalAlbum = isLocalAlbum,
        isInSpeedDial = isInSpeedDial,
        downloadState = downloadState,
        rotationAnimation = rotationAnimation,
        playerConnection = playerConnection,
        context = context,
        onDismiss = onDismiss,
        onAddToPlaylist = { showChoosePlaylistDialog = true },
        onToggleSpeedDial = {
            val updatedPins = toggleSpeedDialPin(speedDialPins, albumPin)
            onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
            onDismiss()
        },
        onRemoveDownload = {
            songs.forEach { song ->
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    song.id,
                    false,
                )
            }
        },
        onDownload = {
            sendAddMissingDownloads(
                context = context,
                songs =
                    songs.map { song ->
                        HeaderDownloadItem(
                            id = song.id,
                            title = song.song.title,
                        )
                    },
                downloads = downloadUtil.downloads.value,
            )
        },
        onViewArtist = {
            if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        },
        onRefetch = {
            refetchIconDegree -= 360
            scope.launch(Dispatchers.IO) {
                YouTube.album(album.id).onSuccess {
                    database.transaction {
                        update(album.album, it, album.artists)
                    }
                }
            }
        },
    )
}
