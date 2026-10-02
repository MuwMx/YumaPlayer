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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.SpeedDialPin

@Composable
internal fun YouTubePlaylistMenuContent(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    isInSpeedDial: Boolean,
    playlistPin: SpeedDialPin?,
    speedDialPins: List<SpeedDialPin>,
    onSpeedDialSongIdsChange: (String) -> Unit,
    downloadState: Int,
    downloads: Map<String, Download>,
    canSelect: Boolean,
    playerConnection: PlayerConnection,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
    context: Context,
    snackbarHostState: SnackbarHostState?,
    onDismiss: () -> Unit,
    selectAction: () -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onShowImportPlaylistDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
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
            YouTubePlaylistActionGrid(
                playlist = playlist,
                playerConnection = playerConnection,
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubePlaylistPrimaryMenuItems(
                playlist = playlist,
                songs = songs,
                dbPlaylist = dbPlaylist,
                isInSpeedDial = isInSpeedDial,
                playlistPin = playlistPin,
                speedDialPins = speedDialPins,
                onSpeedDialSongIdsChange = onSpeedDialSongIdsChange,
                playerConnection = playerConnection,
                database = database,
                syncUtils = syncUtils,
                coroutineScope = coroutineScope,
                context = context,
                snackbarHostState = snackbarHostState,
                onDismiss = onDismiss,
                onShowChoosePlaylistDialog = onShowChoosePlaylistDialog,
                onShowImportPlaylistDialog = onShowImportPlaylistDialog,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubePlaylistDownloadSection(
                downloadState = downloadState,
                songs = songs,
                playlist = playlist,
                downloads = downloads,
                coroutineScope = coroutineScope,
                context = context,
                onShowRemoveDownloadDialog = onShowRemoveDownloadDialog,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubePlaylistShareAndSelectSection(
                playlist = playlist,
                canSelect = canSelect,
                context = context,
                onDismiss = onDismiss,
                selectAction = selectAction,
            )
        }
    }
}
