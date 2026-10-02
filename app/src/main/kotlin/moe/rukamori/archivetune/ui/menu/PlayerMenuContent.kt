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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.utils.SpeedDialPin

@Composable
internal fun PlayerMenuContent(
    mediaMetadata: MediaMetadata,
    librarySong: Song?,
    isLocalMedia: Boolean,
    isInSpeedDial: Boolean,
    isQueueTrigger: Boolean?,
    songPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    castPlayerMenuAction: NewAction?,
    splitArtists: List<PlayerMenuSplitArtist>,
    download: Download?,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    playerConnection: PlayerConnection,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    database: MusicDatabase,
    context: Context,
    onRemoveFromQueue: (() -> Unit)?,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
    onShowDetailsDialog: () -> Unit,
    onShowEqualizerDialog: () -> Unit,
    onShowPitchTempoDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            PlayerMenuActionGrid(
                mediaMetadata = mediaMetadata,
                librarySong = librarySong,
                isLocalMedia = isLocalMedia,
                isInSpeedDial = isInSpeedDial,
                isQueueTrigger = isQueueTrigger,
                songPin = songPin,
                speedDialPins = speedDialPins,
                castPlayerMenuAction = castPlayerMenuAction,
                playerConnection = playerConnection,
                navController = navController,
                playerBottomSheetState = playerBottomSheetState,
                context = context,
                onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
                onShowChoosePlaylistDialog = onShowChoosePlaylistDialog,
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (splitArtists.isNotEmpty() || mediaMetadata.album != null) {
            item {
                PlayerMenuArtistAndAlbumSection(
                    splitArtists = splitArtists,
                    album = mediaMetadata.album,
                    navController = navController,
                    playerBottomSheetState = playerBottomSheetState,
                    onDismiss = onDismiss,
                    onShowSelectArtistDialog = onShowSelectArtistDialog,
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (!isLocalMedia) {
            item {
                PlayerMenuDownloadSection(
                    download = download,
                    mediaMetadata = mediaMetadata,
                    externalDownloaderEnabled = externalDownloaderEnabled,
                    externalDownloaderPackage = externalDownloaderPackage,
                    database = database,
                    context = context,
                    onDismiss = onDismiss,
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            PlayerMenuOptionsSection(
                isQueueTrigger = isQueueTrigger,
                onRemoveFromQueue = onRemoveFromQueue,
                playerConnection = playerConnection,
                onShowDetailsDialog = onShowDetailsDialog,
                onShowEqualizerDialog = onShowEqualizerDialog,
                onShowPitchTempoDialog = onShowPitchTempoDialog,
                onDismiss = onDismiss,
            )
        }
    }
}
