/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadMediaType
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
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
    val colors = LocalYumaColors.current
    val shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    position = YumaSegmentPosition.Single,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .clip(shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.SegmentedIconBoxSize)
                        .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(section.mediaType.iconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(SettingsDimensions.SegmentedIconSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(section.mediaType.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inProgress) {
                    YdsHeaderActionButton(
                        icon = if (section.paused) R.drawable.play else R.drawable.pause,
                        contentDescription =
                            stringResource(
                                if (section.paused) R.string.resume_download else R.string.pause_download,
                            ),
                        onClick = if (section.paused) onResume else onPause,
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

@Composable
internal fun YdsHeaderActionButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = LocalYumaColors.current
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                )
                .yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    position = YumaSegmentPosition.Single,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
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
