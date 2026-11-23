/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
internal fun StageHubCard(
    enabled: Boolean,
    presetLabel: String,
    sourcesCount: Int,
    onClick: () -> Unit,
    sketch: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgeSpacing),
            ) {
                Text(
                    text = stringResource(R.string.stage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusPill(active = enabled)
            }
            Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
            Text(
                text = "$presetLabel · ${stringResource(R.string.audio_lab_stage_sources_count, sourcesCount)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.stage_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(SettingsDimensions.SegmentedIconSpacing))
        sketch()
    }
}

@Composable
internal fun AudioPassHubCard(
    statusLabel: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    sketch: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgeSpacing),
            ) {
                Text(
                    text = stringResource(R.string.audiopass_hub_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusBadgePill(label = statusLabel, active = active)
            }
            Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(SettingsDimensions.SegmentedIconSpacing))
        sketch()
    }
}

@Composable
internal fun SummarySketchCard(
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sketch: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Column(
        modifier = modifier
            .heightIn(min = 128.dp)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
    ) {
        sketch()
        Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgeSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .size(SettingsDimensions.BadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun StatusPill(active: Boolean) {
    StatusBadgePill(
        label = stringResource(if (active) R.string.enabled else R.string.disabled),
        active = active,
    )
}

@Composable
internal fun StatusBadgePill(
    label: String,
    active: Boolean,
) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SettingsDimensions.BadgeCornerRadius))
            .background(bg)
            .padding(
                horizontal = SettingsDimensions.BadgePaddingH,
                vertical = SettingsDimensions.BadgePaddingV,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun stagePresetLabel(presetId: String?): String = when (presetId) {
    "studio" -> stringResource(R.string.stage_preset_studio)
    "living" -> stringResource(R.string.stage_preset_living)
    "front_row" -> stringResource(R.string.stage_preset_front_row)
    "on_stage" -> stringResource(R.string.stage_preset_on_stage)
    "around" -> stringResource(R.string.stage_preset_around)
    "hall" -> stringResource(R.string.stage_preset_hall)
    else -> stringResource(R.string.custom)
}
