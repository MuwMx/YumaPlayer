package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.work.WorkInfo
import androidx.work.WorkManager
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.download.FlacDownloader
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily

@Composable
fun DownloadMenuContent(
    state: PlayerUiState,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val download by LocalDownloadUtil.current
        .getDownload(state.trackUrl)
        .collectAsState(initial = null)

    val (playbackSource) = moe.rukamori.archivetune.utils.rememberEnumPreference(
        moe.rukamori.archivetune.constants.PlaybackSourceKey,
        defaultValue = moe.rukamori.archivetune.constants.PlaybackSource.YT_MUSIC
    )
    val (externalDownloaderEnabled) = moe.rukamori.archivetune.utils.rememberPreference(
        moe.rukamori.archivetune.constants.ExternalDownloaderEnabledKey,
        defaultValue = false
    )
    val (externalDownloaderPackage) = moe.rukamori.archivetune.utils.rememberPreference(
        moe.rukamori.archivetune.constants.ExternalDownloaderPackageKey,
        defaultValue = ""
    )

    val hasFlac = playbackSource == moe.rukamori.archivetune.constants.PlaybackSource.FLAC
    val totalRows = 1 + (if (hasFlac) 1 else 0) + (if (externalDownloaderEnabled) 1 else 0)
    var currentRow = 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.download),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        when (download?.state) {
            Download.STATE_COMPLETED -> {
                CompactMenuRow(
                    title = stringResource(R.string.filter_downloaded),
                    subtitle = stringResource(R.string.tap_to_delete_offline_cache),
                    iconResId = R.drawable.offline,
                    isActive = true,
                    activeIconTint = Color(0xFFFF5252),
                    onClick = {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            state.trackUrl,
                            false,
                        )
                    },
                    index = currentRow++,
                    count = totalRows,
                )
            }
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                CompactMenuRow(
                    title = stringResource(R.string.downloading_ellipsis),
                    subtitle = stringResource(R.string.tap_to_cancel),
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    },
                    onClick = {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            state.trackUrl,
                            false,
                        )
                    },
                    index = currentRow++,
                    count = totalRows,
                )
            }
            else -> {
                CompactMenuRow(
                    title = stringResource(R.string.standard_download),
                    subtitle = stringResource(R.string.standard_download_desc),
                    iconResId = R.drawable.download,
                    onClick = {
                        val downloadRequest = DownloadRequest
                            .Builder(state.trackUrl, state.trackUrl.toUri())
                            .setCustomCacheKey(state.trackUrl)
                            .setData(state.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                    },
                    index = currentRow++,
                    count = totalRows,
                )
            }
        }

        if (hasFlac) {
            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))
            val flacWorkInfos by WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow("flac_download_${state.trackUrl}")
                .collectAsState(emptyList())
            val flacWorkState = flacWorkInfos.firstOrNull()?.state

            when (flacWorkState) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    CompactMenuRow(
                        title = stringResource(R.string.downloading_flac_ellipsis),
                        subtitle = stringResource(R.string.tap_to_cancel),
                        leadingContent = {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                        },
                        onClick = {
                            WorkManager.getInstance(context).cancelUniqueWork("flac_download_${state.trackUrl}")
                        },
                        index = currentRow++,
                        count = totalRows,
                    )
                }
                WorkInfo.State.SUCCEEDED -> {
                    CompactMenuRow(
                        title = stringResource(R.string.flac_downloaded),
                        subtitle = stringResource(R.string.flac_downloaded_desc),
                        iconResId = R.drawable.offline,
                        isActive = true,
                        activeIconTint = Color(0xFFFF5252),
                        onClick = {
                            FlacDownloader.deleteFlac(
                                context,
                                state.trackUrl,
                                state.title,
                                state.artist,
                                "",
                            )
                        },
                        index = currentRow++,
                        count = totalRows,
                    )
                }
                else -> {
                    CompactMenuRow(
                        title = stringResource(R.string.lossless_flac),
                        subtitle = stringResource(R.string.lossless_flac_desc),
                        iconResId = R.drawable.download,
                        onClick = {
                            FlacDownloader.downloadFlac(
                                context,
                                state.trackUrl,
                                state.title,
                                state.artist,
                                "",
                            )
                        },
                        index = currentRow++,
                        count = totalRows,
                    )
                }
            }
        }

        if (externalDownloaderEnabled) {
            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))
            CompactMenuRow(
                title = stringResource(R.string.external_downloader),
                subtitle = stringResource(R.string.external_downloader_row_desc),
                iconResId = R.drawable.download,
                onClick = {
                    onDismissRequest()
                    val url = "https://music.youtube.com/watch?v=${state.trackUrl}"
                    if (externalDownloaderPackage.isBlank()) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.external_downloader_not_configured),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        return@CompactMenuRow
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setPackage(externalDownloaderPackage)
                        data = android.net.Uri.parse(url)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.external_downloader_not_installed),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                index = currentRow++,
                count = totalRows,
            )
        }
    }
}
