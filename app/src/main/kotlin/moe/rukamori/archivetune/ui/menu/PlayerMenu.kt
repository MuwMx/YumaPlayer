/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey
import moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.player.rememberDeviceMusicVolumeController
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onRemoveFromQueue: (() -> Unit)? = null,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float -> deviceMusicVolumeController.setVolumeFraction(volume) }
        }
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = null)

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata.id)
        .collectAsStateWithLifecycle(initialValue = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(mediaMetadata.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = mediaMetadata.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }
    val isLocalMedia =
        remember(librarySong?.song?.isLocal, mediaMetadata.id) {
            librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId()
        }
    val castPlayerMenuAction = rememberCastPlayerMenuAction()

    val splitArtists = rememberPlayerMenuSplitArtists(artists, artistSeparators)

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }
    var showEqualizerDialog by rememberSaveable { mutableStateOf(false) }

    PlayerMenuAddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        mediaMetadata = mediaMetadata,
        database = database,
        context = context,
        onDismiss = { showChoosePlaylistDialog = false },
    )

    PlayerMenuSelectArtistDialog(
        isVisible = showSelectArtistDialog,
        splitArtists = splitArtists,
        navController = navController,
        playerBottomSheetState = playerBottomSheetState,
        onDismiss = { showSelectArtistDialog = false },
        onDismissMenu = onDismiss,
    )

    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    PlayerMenuEqualizerDialog(
        isVisible = showEqualizerDialog,
        playerConnection = playerConnection,
        context = context,
        activityResultLauncher = activityResultLauncher,
        onDismiss = { showEqualizerDialog = false },
    )

    val nowPlayingTitle =
        remember(mediaMetadata.title) {
            mediaMetadata.title.ifBlank { context.getString(R.string.no_title) }
        }

    val nowPlayingSubtitle =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = " • ") { it.name }
        }

    val darkScheme = darkColorScheme()
    MaterialTheme(colorScheme = darkScheme) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalYumaColors provides darkYumaColorScheme(darkScheme),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PlayerMenuHeader(
                    mediaMetadata = mediaMetadata,
                    nowPlayingTitle = nowPlayingTitle,
                    nowPlayingSubtitle = nowPlayingSubtitle,
                )

                if (isQueueTrigger != true) {
                    Spacer(modifier = Modifier.height(12.dp))

                    PlayerVolumeCard(
                        volume = deviceMusicVolumeController.volumeFraction,
                        onVolumeChange = onPlayerVolumeChange,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PlayerMenuContent(
                    mediaMetadata = mediaMetadata,
                    librarySong = librarySong,
                    isLocalMedia = isLocalMedia,
                    isInSpeedDial = isInSpeedDial,
                    isQueueTrigger = isQueueTrigger,
                    songPin = songPin,
                    speedDialPins = speedDialPins,
                    castPlayerMenuAction = castPlayerMenuAction,
                    splitArtists = splitArtists,
                    download = download,
                    externalDownloaderEnabled = externalDownloaderEnabled,
                    externalDownloaderPackage = externalDownloaderPackage,
                    playerConnection = playerConnection,
                    navController = navController,
                    playerBottomSheetState = playerBottomSheetState,
                    database = database,
                    context = context,
                    onRemoveFromQueue = onRemoveFromQueue,
                    onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
                    onShowChoosePlaylistDialog = { showChoosePlaylistDialog = true },
                    onShowSelectArtistDialog = { showSelectArtistDialog = true },
                    onShowDetailsDialog = onShowDetailsDialog,
                    onShowEqualizerDialog = { showEqualizerDialog = true },
                    onShowPitchTempoDialog = { showPitchTempoDialog = true },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
