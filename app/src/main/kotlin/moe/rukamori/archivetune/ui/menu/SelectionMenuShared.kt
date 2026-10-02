/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem

@Composable
internal fun SelectionMenuActionGrid(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    MenuSurfaceSection {
        NewActionGrid(
            actions =
                listOf(
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = stringResource(R.string.play),
                        onClick = onPlay,
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = stringResource(R.string.shuffle),
                        onClick = onShuffle,
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = onAddToPlaylist,
                    ),
                ),
        )
    }
}

@Composable
internal fun SelectionMenuRemoveDownloadDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
) {
    if (!isVisible) return
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            TextButton(
                onClick = {
                    onDismiss()
                    onConfirmRemove()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
internal fun SelectionMenuDownloadSection(
    downloadState: Int,
    onShowRemoveDownloadDialog: () -> Unit,
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
                    onClick = onShowRemoveDownloadDialog,
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
                    onClick = onShowRemoveDownloadDialog,
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
