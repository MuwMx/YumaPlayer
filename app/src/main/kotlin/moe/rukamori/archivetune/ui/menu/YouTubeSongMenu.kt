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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.song(song.id).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val artists =
        remember {
            song.artists.mapNotNull {
                it.id?.let { artistId ->
                    MediaMetadata.Artist(id = artistId, name = it.name)
                }
            }
        }

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(song.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = song.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }

    val splitArtists = rememberYouTubeSongSplitArtists(artists, artistSeparators)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

    YouTubeSongAddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        song = song,
        database = database,
        context = context,
        onDismiss = { showChoosePlaylistDialog = false },
    )

    YouTubeSongArtistSelectDialog(
        isVisible = showSelectArtistDialog,
        splitArtists = splitArtists,
        onDismiss = { showSelectArtistDialog = false },
        onSelectArtist = { artistId ->
            navController.navigate("artist/$artistId")
            showSelectArtistDialog = false
            onDismiss()
        },
    )

    YouTubeSongMenuHeader(
        song = song,
        librarySong = librarySong,
        database = database,
        syncUtils = syncUtils,
    )

    Spacer(modifier = Modifier.height(16.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current

    YouTubeSongMenuContent(
        song = song,
        librarySong = librarySong,
        download = download,
        splitArtists = splitArtists,
        isInSpeedDial = isInSpeedDial,
        externalDownloaderEnabled = externalDownloaderEnabled,
        externalDownloaderPackage = externalDownloaderPackage,
        database = database,
        playerConnection = playerConnection,
        coroutineScope = coroutineScope,
        bottomSheetPageState = bottomSheetPageState,
        navController = navController,
        context = context,
        onDismiss = onDismiss,
        onAddToPlaylist = { showChoosePlaylistDialog = true },
        onToggleSpeedDial = {
            coroutineScope.launch {
                if (!isInSpeedDial) {
                    withContext(Dispatchers.IO) {
                        database.transaction {
                            insert(song.toMediaMetadata())
                        }
                    }
                }

                val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                onDismiss()
            }
        },
        onShowSelectArtistDialog = { showSelectArtistDialog = true },
    )
}
