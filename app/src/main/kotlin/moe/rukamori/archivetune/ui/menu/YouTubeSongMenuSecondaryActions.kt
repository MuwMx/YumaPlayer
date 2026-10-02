/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun YouTubeSongDownloadSection(
    song: SongItem,
    download: Download?,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    database: MusicDatabase,
    context: Context,
    onDismiss: () -> Unit,
) {
    val downloadItemCount = 1 + (if (externalDownloaderEnabled) 1 else 0)
    MenuSurfaceSection {
        when (download?.state) {
            Download.STATE_COMPLETED -> {
                NewMenuItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.remove_download),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    },
                    index = 0,
                    count = downloadItemCount,
                )
            }

            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false,
                        )
                    },
                    index = 0,
                    count = downloadItemCount,
                )
            }

            else -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.action_download)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        database.transaction {
                            insert(song.toMediaMetadata())
                        }
                        val downloadRequest =
                            DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                    },
                    index = 0,
                    count = downloadItemCount,
                )
            }
        }

        if (externalDownloaderEnabled) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismiss()
                    val url = "https://music.youtube.com/watch?v=${song.id}"
                    if (externalDownloaderPackage.isBlank()) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.external_downloader_not_configured),
                                Toast.LENGTH_LONG,
                            ).show()
                        return@NewMenuItem
                    }
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage(externalDownloaderPackage)
                            data = android.net.Uri.parse(url)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    try {
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.external_downloader_not_installed),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
                index = 1,
                count = downloadItemCount,
            )
        }
    }
}

@Composable
internal fun YouTubeSongArtistAndAlbumSection(
    splitArtists: List<YouTubeSongSplitArtist>,
    album: Album?,
    navController: NavController,
    onDismiss: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
) {
    val artistAlbumCount = (if (splitArtists.isNotEmpty()) 1 else 0) + (if (album != null) 1 else 0)
    MenuSurfaceSection {
        if (splitArtists.isNotEmpty()) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.artist),
                        contentDescription = null,
                    )
                },
                onClick = {
                    if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                        navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                        onDismiss()
                    } else {
                        onShowSelectArtistDialog()
                    }
                },
                index = 0,
                count = artistAlbumCount,
            )
        }

        album?.let { alb ->
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.album),
                        contentDescription = null,
                    )
                },
                onClick = {
                    navController.navigate("album/${alb.id}")
                    onDismiss()
                },
                index = if (splitArtists.isNotEmpty()) 1 else 0,
                count = artistAlbumCount,
            )
        }
    }
}
