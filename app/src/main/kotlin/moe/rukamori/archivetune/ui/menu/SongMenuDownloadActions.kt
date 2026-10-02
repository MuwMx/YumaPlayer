/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.work.WorkInfo
import androidx.work.WorkManager
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.download.FlacDownloader
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.NewMenuItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongMenuDownloadItems(
    song: Song,
    download: Download?,
    playbackSource: PlaybackSource,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    downloadIndex: Int,
    flacIndex: Int,
    externalDownloaderIndex: Int,
    mutationItemCount: Int,
    context: Context,
    onDismiss: () -> Unit,
) {
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
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = null,
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
                index = downloadIndex,
                count = mutationItemCount,
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
                index = downloadIndex,
                count = mutationItemCount,
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
                    val downloadRequest =
                        DownloadRequest
                            .Builder(song.id, song.id.toUri())
                            .setCustomCacheKey(song.id)
                            .setData(song.song.title.toByteArray())
                            .build()
                    DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false,
                    )
                },
                index = downloadIndex,
                count = mutationItemCount,
            )
        }
    }

    if (playbackSource == PlaybackSource.FLAC) {
        val flacWorkInfos by WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("flac_download_${song.id}")
            .collectAsState(emptyList())
        val flacWorkState = flacWorkInfos.firstOrNull()?.state

        when (flacWorkState) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    onClick = {
                        WorkManager.getInstance(context).cancelUniqueWork("flac_download_${song.id}")
                    },
                    index = flacIndex,
                    count = mutationItemCount,
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.remove_download)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        FlacDownloader.deleteFlac(
                            context,
                            song.id,
                            song.song.title,
                            song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
                            song.song.albumName.orEmpty(),
                        )
                    },
                    index = flacIndex,
                    count = mutationItemCount,
                )
            }
            else -> {
                NewMenuItem(
                    headlineContent = { Text(text = stringResource(R.string.download_flac)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        FlacDownloader.downloadFlac(
                            context,
                            song.id,
                            song.song.title,
                            song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
                            song.song.albumName.orEmpty(),
                        )
                    },
                    index = flacIndex,
                    count = mutationItemCount,
                )
            }
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
                        data = Uri.parse(url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.external_downloader_not_installed),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            index = externalDownloaderIndex,
            count = mutationItemCount,
        )
    }
}
