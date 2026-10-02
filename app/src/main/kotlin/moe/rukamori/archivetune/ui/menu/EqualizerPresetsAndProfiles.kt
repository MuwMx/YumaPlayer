/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqCapabilities
import moe.rukamori.archivetune.playback.PlayerConnection

@Composable
internal fun EqualizerPresetsSection(
    caps: EqCapabilities,
    selectedProfileId: String,
    playerConnection: PlayerConnection,
    onSelectProfileId: (String) -> Unit,
) {
    EqSection(title = stringResource(R.string.eq_presets)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedProfileId == "flat",
                onClick = {
                    playerConnection.service.applyEqFlatPreset()
                    onSelectProfileId("flat")
                },
                label = { Text(text = stringResource(R.string.eq_flat)) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = null,
            )

            caps.systemPresets.forEachIndexed { index, name ->
                FilterChip(
                    selected = selectedProfileId == "system:$index",
                    onClick = {
                        playerConnection.service.applySystemEqPreset(index)
                        onSelectProfileId("system:$index")
                    },
                    label = {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    border = null,
                )
            }
        }
    }
}

@Composable
internal fun EqualizerProfilesSection(
    currentProfileTitle: String,
    eqEnabled: Boolean,
    onShowManageProfiles: () -> Unit,
    onShowSaveProfile: () -> Unit,
    onImportClick: () -> Unit,
) {
    EqSection(
        title = stringResource(R.string.eq_profiles),
        subtitle = stringResource(R.string.eq_profile_hint),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentProfileTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (eqEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = stringResource(if (eqEnabled) R.string.enabled else R.string.disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (eqEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onShowManageProfiles) {
                Text(text = stringResource(R.string.eq_manage))
            }
            FilledTonalButton(onClick = onShowSaveProfile) {
                Text(text = stringResource(R.string.eq_save))
            }
            OutlinedButton(onClick = onImportClick) {
                Text(text = stringResource(R.string.eq_import))
            }
        }
    }
}
