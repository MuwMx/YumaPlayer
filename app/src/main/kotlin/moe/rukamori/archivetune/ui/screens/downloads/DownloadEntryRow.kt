/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun DownloadEntry(
    entry: DownloadEntryUiModel,
    inProgress: Boolean,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
    onRemoveChild: (DownloadEntryUiModel) -> Unit = {},
) {
    Card(
        onClick = if (inProgress || entry.children.isEmpty()) onOpen else onExpandToggle,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.supportingText?.let { supportingText ->
                        Text(
                            text = supportingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (inProgress) {
                        if (entry.failed) {
                            Text(
                                text = stringResource(R.string.download_failed_state),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.download_progress_percent, entry.percent),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(R.string.download_speed, formatFileSize(entry.speedBytesPerSecond)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { entry.progress },
                            modifier = Modifier.fillMaxWidth(),
                            amplitude = { if (entry.paused) 0f else 1f },
                        )
                    } else if (entry.destinationRoute != null || entry.totalCount > 1) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, entry.totalCount, entry.totalCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            leadingContent = {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(entry.placeholderIcon()),
                    error = painterResource(entry.placeholderIcon()),
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                )
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!inProgress) {
                        if (entry.children.isNotEmpty()) {
                            IconButton(onClick = onExpandToggle) {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (isExpanded) R.drawable.expand_less else R.drawable.expand_more,
                                        ),
                                    contentDescription = null,
                                )
                            }
                        }
                        entry.durationSeconds?.let { durationSeconds ->
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = makeTimeString(durationSeconds * 1_000L),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    } else {
                        DownloadActionButton(
                            icon = if (entry.paused) R.drawable.play else R.drawable.pause,
                            contentDescription =
                                stringResource(
                                    if (entry.paused) R.string.resume_download else R.string.pause_download,
                                ),
                            onClick = if (entry.paused) onResume else onPause,
                        )
                    }
                    DownloadActionButton(
                        icon = if (inProgress) R.drawable.close else R.drawable.delete,
                        contentDescription =
                            stringResource(if (inProgress) android.R.string.cancel else R.string.remove_download),
                        onClick = onRemove,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
        if (isExpanded && entry.children.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                entry.children.forEach { child ->
                    DownloadChildEntryRow(
                        child = child,
                        onRemove = { onRemoveChild(child) },
                    )
                }
            }
        }
    }
}

private fun DownloadEntryUiModel.placeholderIcon(): Int =
    when {
        destinationRoute?.startsWith("album/") == true -> R.drawable.album
        destinationRoute != null -> R.drawable.queue_music
        else -> R.drawable.music_note
    }
