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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerMenuDownloadSection(
    download: Download?,
    mediaMetadata: MediaMetadata,
    externalDownloaderEnabled: Boolean,
    externalDownloaderPackage: String,
    database: MusicDatabase,
    context: Context,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        when (download?.state) {
            Download.STATE_COMPLETED -> {
                ListItem(
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
                    modifier =
                        Modifier.clickable {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                mediaMetadata.id,
                                false,
                            )
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.downloading)) },
                    leadingContent = {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                mediaMetadata.id,
                                false,
                            )
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            else -> {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.action_download)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            database.transaction {
                                insert(mediaMetadata)
                            }
                            val downloadRequest =
                                DownloadRequest
                                    .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                    .setCustomCacheKey(mediaMetadata.id)
                                    .setData(mediaMetadata.title.toByteArray())
                                    .build()
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                downloadRequest,
                                false,
                            )
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        if (externalDownloaderEnabled) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                modifier =
                    Modifier.clickable {
                        onDismiss()
                        val url = "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                        if (externalDownloaderPackage.isBlank()) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.external_downloader_not_configured),
                                    Toast.LENGTH_LONG,
                                ).show()
                            return@clickable
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
