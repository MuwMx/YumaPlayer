/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun DownloadEntry(
    entry: DownloadEntryUiModel,
    inProgress: Boolean,
    shape: Shape,
    position: YumaSegmentPosition,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandToggle: () -> Unit = {},
) {
    val colors = LocalYumaColors.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = if (inProgress || entry.children.isEmpty()) onOpen else onExpandToggle,
                )
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    position = position,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .clip(shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(entry.placeholderIcon()),
                error = painterResource(entry.placeholderIcon()),
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius)),
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                entry.supportingText?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (inProgress) {
                    if (entry.failed) {
                        Text(
                            text = stringResource(R.string.download_failed_state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.download_progress_percent, entry.percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.download_speed, formatFileSize(entry.speedBytesPerSecond)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearWavyProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        amplitude = { if (entry.paused) 0f else 1f },
                    )
                } else if (entry.destinationRoute != null || entry.totalCount > 1) {
                    Text(
                        text = pluralStringResource(R.plurals.n_song, entry.totalCount, entry.totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    entry.durationSeconds?.let { durationSeconds ->
                        val context = LocalContext.current
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = makeTimeString(durationSeconds * 1_000L, context),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    YdsHeaderActionButton(
                        icon = if (entry.paused) R.drawable.play else R.drawable.pause,
                        contentDescription =
                            stringResource(
                                if (entry.paused) R.string.resume_download else R.string.pause_download,
                            ),
                        onClick = if (entry.paused) onResume else onPause,
                    )
                }
                YdsHeaderActionButton(
                    icon = if (inProgress) R.drawable.close else R.drawable.delete,
                    contentDescription =
                        stringResource(if (inProgress) android.R.string.cancel else R.string.remove_download),
                    onClick = onRemove,
                )
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
