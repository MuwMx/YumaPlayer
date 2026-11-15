/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadMediaType
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.ui.utils.formatFileSize

@Composable
internal fun DownloadSectionHeader(
    section: DownloadSectionUiModel,
    inProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(section.mediaType.titleRes()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    text =
                        if (inProgress) {
                            stringResource(
                                R.string.download_progress_summary,
                                section.percent,
                                formatFileSize(section.speedBytesPerSecond),
                            )
                        } else {
                            section.countText()
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(section.mediaType.iconRes()),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (inProgress) {
                        DownloadActionButton(
                            icon = if (section.paused) R.drawable.play else R.drawable.pause,
                            contentDescription =
                                stringResource(
                                    if (section.paused) R.string.resume_download else R.string.pause_download,
                                ),
                            onClick = if (section.paused) onResume else onPause,
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
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
private fun DownloadSectionUiModel.countText(): String =
    when (mediaType) {
        DownloadMediaType.PLAYLIST -> pluralStringResource(R.plurals.n_playlist, entries.size, entries.size)
        DownloadMediaType.ALBUM -> pluralStringResource(R.plurals.n_album, entries.size, entries.size)
        DownloadMediaType.SONG -> pluralStringResource(R.plurals.n_song, entries.size, entries.size)
    }

private fun DownloadMediaType.titleRes(): Int =
    when (this) {
        DownloadMediaType.PLAYLIST -> R.string.playlists
        DownloadMediaType.ALBUM -> R.string.albums
        DownloadMediaType.SONG -> R.string.songs
    }

private fun DownloadMediaType.iconRes(): Int =
    when (this) {
        DownloadMediaType.PLAYLIST -> R.drawable.queue_music
        DownloadMediaType.ALBUM -> R.drawable.album
        DownloadMediaType.SONG -> R.drawable.music_note
    }
