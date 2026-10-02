/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

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
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun YouTubeAlbumDownloadSection(
    downloadState: Int,
    onRemoveDownload: () -> Unit,
    onDownload: () -> Unit,
) {
    MenuSurfaceSection {
        when (downloadState) {
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
                    onClick = onRemoveDownload,
                    index = 0,
                    count = 1,
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
                    onClick = onRemoveDownload,
                    index = 0,
                    count = 1,
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
                    onClick = onDownload,
                    index = 0,
                    count = 1,
                )
            }
        }
    }
}
