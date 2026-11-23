/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TransitionMode
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import kotlin.math.roundToInt

@Composable
internal fun PlayerCrossfadeSection(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    val effectiveMode = when {
        state.transitionMode != TransitionMode.OFF -> state.transitionMode
        state.automixEnabled -> TransitionMode.AUTOMIX
        state.crossfadeEnabled -> TransitionMode.CROSSFADE
        else -> TransitionMode.OFF
    }
    val summary = when (effectiveMode) {
        TransitionMode.CROSSMIX -> stringResource(R.string.transitions_player_summary_crossmix)
        TransitionMode.AUTOMIX -> stringResource(
            R.string.transitions_player_summary_automix,
            state.crossfadeDurationSeconds.roundToInt(),
        )
        TransitionMode.CROSSFADE -> stringResource(
            R.string.transitions_player_summary_crossfade,
            state.crossfadeDurationSeconds.roundToInt(),
        )
        TransitionMode.OFF -> stringResource(R.string.transitions_player_summary_off)
    }

    PreferenceGroup(
        title = stringResource(R.string.transitions_title),
        modifier = modifier,
    ) {
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.transitions_title)) },
                description = summary,
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                showChevron = true,
                onClick = actions.onTransitionsClick,
            )
        }
    }
}
