/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun AppearanceSettingsContent(
    state: AppearanceSettingsUiState,
    actions: AppearanceSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        AppearanceThemeSection(
            state = state,
            actions = actions,
        )
        AppearanceMiscSection(
            state = state,
            actions = actions,
        )
    }
}
