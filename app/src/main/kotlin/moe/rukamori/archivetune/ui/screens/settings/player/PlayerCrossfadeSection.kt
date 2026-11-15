/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutomixTransitionDuration
import moe.rukamori.archivetune.ui.component.CrossfadeSliderPreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsAnimations

@Composable
internal fun PlayerCrossfadeSection(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_crossfade_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_crossfade_title)) },
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.crossfadeEnabled,
                onCheckedChange = actions.onCrossfadeEnabledChange,
            )
        }
        item(visible = state.crossfadeEnabled) {
            SwitchPreference(
                title = { Text(stringResource(R.string.automix_title)) },
                description = stringResource(R.string.automix_description),
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.automixEnabled,
                onCheckedChange = actions.onAutomixEnabledChange,
            )
        }
        item(visible = state.crossfadeEnabled && state.automixEnabled) {
            EnumListPreference(
                title = { Text(stringResource(R.string.automix_transition_length)) },
                icon = { Icon(painterResource(R.drawable.speed), null) },
                selectedValue = state.automixTransitionDuration,
                onValueSelected = actions.onAutomixTransitionDurationChange,
                valueText = { duration ->
                    when (duration) {
                        AutomixTransitionDuration.AUTO -> stringResource(R.string.automix_duration_auto)
                        AutomixTransitionDuration.S4 -> stringResource(R.string.automix_duration_4s)
                        AutomixTransitionDuration.S8 -> stringResource(R.string.automix_duration_8s)
                        AutomixTransitionDuration.S12 -> stringResource(R.string.automix_duration_12s)
                        AutomixTransitionDuration.S16 -> stringResource(R.string.automix_duration_16s)
                        AutomixTransitionDuration.S20 -> stringResource(R.string.automix_duration_20s)
                    }
                },
            )
        }
        item(visible = state.crossfadeEnabled) {
            CrossfadeSliderPreference(
                valueSeconds = state.crossfadeDurationSeconds,
                onValueChange = actions.onCrossfadeDurationSecondsChange,
            )
        }
        item(visible = state.crossfadeEnabled) {
            SwitchPreference(
                title = { Text(stringResource(R.string.crossfade_gapless_title)) },
                description = stringResource(R.string.crossfade_gapless_description),
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.crossfadeGapless,
                onCheckedChange = actions.onCrossfadeGaplessChange,
            )
        }
    }
}
