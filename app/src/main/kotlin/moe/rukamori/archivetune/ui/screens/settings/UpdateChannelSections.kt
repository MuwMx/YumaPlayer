/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.utils.GitCommit

@Composable
internal fun UpdateChannelPanel(
    updateChannel: UpdateChannel,
    onStableSelected: () -> Unit,
    onCanarySelected: () -> Unit,
) {
    val isCanary = updateChannel != UpdateChannel.STABLE
    PreferenceEntry(
        title = { Text(text = stringResource(R.string.update_channel)) },
        description = stringResource(R.string.update_channel_desc),
        icon = {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = null,
            )
        },
        content = {
            Spacer(Modifier.height(10.dp))
            val colors = LocalYumaColors.current
            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.glassBackground)
                    .border(SettingsDimensions.GlassBorderThickness, colors.glassBorder, barShape)
                    .padding(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChannelSelectChip(
                        label = stringResource(R.string.channel_stable),
                        isSelected = !isCanary,
                        onClick = onStableSelected,
                        modifier = Modifier.weight(1f),
                    )
                    ChannelSelectChip(
                        label = stringResource(R.string.channel_canary),
                        isSelected = isCanary,
                        onClick = onCanarySelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ChannelSelectChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (!enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .yumaClickable(enabled = enabled, pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun NightlyInstallPanel(
    latestCommit: GitCommit?,
    onInstallNightly: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.channel_nightly)) {
        item {
            PreferenceEntry(
                title = { Text(text = stringResource(R.string.updates_nightly_title)) },
                description = stringResource(R.string.updates_nightly_description) + "\n" +
                    stringResource(R.string.updates_latest_commit, latestCommit?.sha ?: "-"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                onClick = onInstallNightly,
            )
        }
    }
}
