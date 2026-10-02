/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqProfilesPayload
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import java.util.UUID

@Composable
internal fun EqualizerSaveProfileDialog(
    isVisible: Boolean,
    profiles: List<EqProfile>,
    bandCenterFreqHz: List<Int>,
    bandLevelsMb: List<Int>,
    outputGainMb: Int,
    bassBoostStrength: Int,
    virtualizerStrength: Int,
    onSaveProfile: (EqProfile, EqProfilesPayload, String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    TextFieldDialog(
        title = { Text(text = stringResource(R.string.eq_save_profile)) },
        placeholder = { Text(text = stringResource(R.string.eq_profile_name)) },
        onDone = { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) {
                val newProfile =
                    EqProfile(
                        id = UUID.randomUUID().toString(),
                        name = trimmed,
                        bandCenterFreqHz = bandCenterFreqHz,
                        bandLevelsMb = bandLevelsMb,
                        outputGainMb = outputGainMb,
                        bassBoostStrength = bassBoostStrength,
                        virtualizerStrength = virtualizerStrength,
                    )

                val updatedPayload =
                    EqProfilesPayload(
                        profiles =
                            (profiles + newProfile)
                                .distinctBy { it.id }
                                .sortedBy { it.name.lowercase() },
                    )

                val safeName = trimmed.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                onSaveProfile(newProfile, updatedPayload, "$safeName-eq.json")
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun EqualizerManageProfilesDialog(
    isVisible: Boolean,
    profiles: List<EqProfile>,
    selectedProfileId: String,
    onSelectProfile: (EqProfile) -> Unit,
    onDeleteProfile: (EqProfile, EqProfilesPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    ListDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(
            items = profiles,
            key = { it.id },
            contentType = { "eq_profile" },
        ) { profile ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectProfile(profile)
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.eq_custom_profile),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = {
                        val updatedPayload =
                            EqProfilesPayload(
                                profiles = profiles.filterNot { it.id == profile.id },
                            )
                        onDeleteProfile(profile, updatedPayload)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
