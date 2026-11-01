/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.playerAwareBottomPadding
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategoryUiModel
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettingsScreenState

@Composable
internal fun PlayerBehaviorSection(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.player),
        modifier = modifier,
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_start_on_bluetooth)) },
                icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                checked = state.autoStartOnBluetooth,
                onCheckedChange = actions.onAutoStartOnBluetoothChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.pause_on_device_mute)) },
                icon = { Icon(painterResource(R.drawable.volume_off), null) },
                checked = state.pauseOnDeviceMute,
                onCheckedChange = actions.onPauseOnDeviceMuteChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                icon = { Icon(painterResource(R.drawable.skip_next), null) },
                checked = state.autoSkipNextOnError,
                onCheckedChange = actions.onAutoSkipNextOnErrorChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.wakelock)) },
                description = stringResource(R.string.wakelock_desc),
                icon = { Icon(painterResource(R.drawable.lock), null) },
                checked = state.wakelockEnabled,
                onCheckedChange = actions.onWakelockChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.low_data_mode_title)) },
                description = stringResource(R.string.low_data_mode_description),
                icon = { Icon(painterResource(R.drawable.android_cell), null) },
                checked = state.lowDataMode,
                onCheckedChange = actions.onLowDataModeChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                icon = { Icon(painterResource(R.drawable.swipe), null) },
                checked = state.stopMusicOnTaskClear,
                onCheckedChange = actions.onStopMusicOnTaskClearChange,
            )
        }
    }
}

@Composable
internal fun PlayerSettingsContent(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    modifier: Modifier = Modifier,
    sponsorBlockState: SponsorBlockSettingsScreenState = SponsorBlockSettingsScreenState.Loading,
    onSponsorBlockEnabledChange: (Boolean) -> Unit = {},
    onCategorySheetOpen: () -> Unit = {},
    onCategorySheetDismiss: () -> Unit = {},
    onCategoryCheckedChange: (SponsorBlockCategoryUiModel, Boolean) -> Unit = { _, _ -> },
    onCategorySelectionConfirm: () -> Unit = {},
    onApiUrlEditorOpen: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .playerAwareBottomPadding()
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        PlayerLosslessSection(state = state, actions = actions)
        PlayerAudioSection(state = state, actions = actions)
        PlayerCrossfadeSection(state = state, actions = actions)
        PlayerSponsorBlockSection(
            state = sponsorBlockState,
            onEnabledChange = onSponsorBlockEnabledChange,
            onCategorySheetOpen = onCategorySheetOpen,
            onCategorySheetDismiss = onCategorySheetDismiss,
            onCategoryCheckedChange = onCategoryCheckedChange,
            onCategorySelectionConfirm = onCategorySelectionConfirm,
            onApiUrlEditorOpen = onApiUrlEditorOpen,
            onRetry = onRetry,
        )
        PlayerBehaviorSection(state = state, actions = actions)
    }
}
