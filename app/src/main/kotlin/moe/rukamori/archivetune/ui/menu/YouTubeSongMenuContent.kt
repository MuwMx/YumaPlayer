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
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState

@Composable
internal fun YouTubeSongMenuContent(
    song: SongItem,
    librarySong: Song?,
    download: Download?,
    splitArtists: List<YouTubeSongSplitArtist>,
    isInSpeedDial: Boolean,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    database: MusicDatabase,
    playerConnection: PlayerConnection,
    coroutineScope: CoroutineScope,
    bottomSheetPageState: BottomSheetPageState,
    navController: NavController,
    context: Context,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSpeedDial: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
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
            YouTubeSongPrimaryActionGrid(
                song = song,
                playerConnection = playerConnection,
                context = context,
                onDismiss = onDismiss,
                onAddToPlaylist = onAddToPlaylist,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubeSongLibraryAndSpeedDialSection(
                song = song,
                librarySong = librarySong,
                isInSpeedDial = isInSpeedDial,
                database = database,
                coroutineScope = coroutineScope,
                context = context,
                onToggleSpeedDial = onToggleSpeedDial,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubeSongDownloadSection(
                song = song,
                download = download,
                externalDownloaderEnabled = externalDownloaderEnabled,
                externalDownloaderPackage = externalDownloaderPackage,
                database = database,
                context = context,
                onDismiss = onDismiss,
            )
        }

        if (splitArtists.isNotEmpty() || song.album != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                YouTubeSongArtistAndAlbumSection(
                    splitArtists = splitArtists,
                    album = song.album,
                    navController = navController,
                    onDismiss = onDismiss,
                    onShowSelectArtistDialog = onShowSelectArtistDialog,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubeSongDetailsSection(
                songId = song.id,
                bottomSheetPageState = bottomSheetPageState,
                onDismiss = onDismiss,
            )
        }
    }
}
