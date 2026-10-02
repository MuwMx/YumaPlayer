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
import androidx.media3.exoplayer.offline.DownloadService
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads

@Composable
internal fun YouTubeAlbumMenuContent(
    albumItem: AlbumItem,
    album: AlbumWithSongs?,
    isInSpeedDial: Boolean,
    downloadState: Int,
    downloads: Map<String, androidx.media3.exoplayer.offline.Download>,
    playerConnection: PlayerConnection,
    context: Context,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSpeedDial: () -> Unit,
    onViewArtist: () -> Unit,
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
            YouTubeAlbumActionGrid(
                albumItem = albumItem,
                album = album,
                playerConnection = playerConnection,
                context = context,
                onDismiss = onDismiss,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubeAlbumPrimaryActions(
                album = album,
                isInSpeedDial = isInSpeedDial,
                playerConnection = playerConnection,
                onDismiss = onDismiss,
                onAddToPlaylist = onAddToPlaylist,
                onToggleSpeedDial = onToggleSpeedDial,
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            YouTubeAlbumDownloadSection(
                downloadState = downloadState,
                onRemoveDownload = {
                    album?.songs?.forEach { song ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    }
                },
                onDownload = {
                    album?.songs?.let { songs ->
                        sendAddMissingDownloads(
                            context = context,
                            songs =
                                songs.map { song ->
                                    HeaderDownloadItem(
                                        id = song.id,
                                        title = song.song.title,
                                    )
                                },
                            downloads = downloads,
                        )
                    }
                },
            )
        }

        if (albumItem.artists != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                YouTubeAlbumArtistSection(
                    onViewArtist = onViewArtist,
                )
            }
        }
    }
}
