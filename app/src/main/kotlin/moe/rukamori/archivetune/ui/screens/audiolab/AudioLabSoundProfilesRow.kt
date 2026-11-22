/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabSoundProfiles
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable

@Composable
fun AudioLabSoundProfilesRow(
    activeProfileId: String?,
    bypassed: Boolean,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val yumaColors = LocalYumaColors.current
    val chipShape = RoundedCornerShape(50)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = AudioLabSoundProfiles.All, key = { it.id }) { profile ->
            val selected = !bypassed && profile.id == activeProfileId
            val bg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    yumaColors.cardBackgroundOpaque
                },
                animationSpec = if (animationsDisabled) snap() else tween(200),
                label = "profileChipBg",
            )
            val fg = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .clip(chipShape)
                    .yumaClickable { onSelectProfile(profile.id) }
                    .background(bg)
                    .border(1.dp, borderColor, chipShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(soundProfileLabelRes(profile.id)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}

@StringRes
internal fun soundProfileLabelRes(profileId: String): Int = when (profileId) {
    AudioLabSoundProfiles.Pure.id -> R.string.audio_lab_profile_pure
    AudioLabSoundProfiles.Studio3D.id -> R.string.audio_lab_profile_studio_3d
    AudioLabSoundProfiles.ConcertHall.id -> R.string.audio_lab_profile_concert_hall
    AudioLabSoundProfiles.LateNight.id -> R.string.audio_lab_profile_late_night
    AudioLabSoundProfiles.ClubBass.id -> R.string.audio_lab_profile_club_bass
    else -> R.string.audio_lab_profile_pure
}
