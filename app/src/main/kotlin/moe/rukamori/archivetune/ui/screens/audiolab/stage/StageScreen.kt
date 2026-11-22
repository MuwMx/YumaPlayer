/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.AudioLabViewModel

@Composable
fun StageScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stage = state.stage
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false

    var selectedKey by remember { mutableIntStateOf(ListenerKey) }
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        StageAddSheet(
            objects = stage.objects,
            onAddSource = { source ->
                if (viewModel.addStageObject(source)) {
                    viewModel.state.value.stage.objects.lastOrNull()?.let { created ->
                        selectedKey = created.id
                    }
                }
            },
            onDismiss = { showAddSheet = false },
        )
    }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.stage_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        StageRoom(
            stage = stage,
            selectedKey = selectedKey,
            onSelect = { selectedKey = it },
            onMoveObject = { id, x, y, z ->
                viewModel.updateStageObject(id) { it.copy(x = x, y = y, z = z) }
            },
            onMoveListener = { x, y, z ->
                viewModel.updateStageListener { it.copy(x = x, y = y, z = z) }
            },
            onOpenAddSheet = { showAddSheet = true },
            stageLevelsProvider = { viewModel.stageLevels },
            isPlaying = isPlaying,
            modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        StagePresetsPanel(
            selectedPresetId = stage.presetId,
            onPresetSelected = viewModel::applyStagePreset,
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        StageControlsPanel(
            stage = stage,
            selectedKey = selectedKey,
            onSelect = { selectedKey = it },
            onEnabledChange = { enabled -> viewModel.updateStage { it.copy(enabled = enabled) } },
            onUpdateObject = viewModel::updateStageObject,
            onUpdateListener = viewModel::updateStageListener,
            onRemoveObject = { id ->
                if (viewModel.removeStageObject(id) && selectedKey == id) {
                    selectedKey = ListenerKey
                }
            },
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        StageRoomSettingsPanel(
            stage = stage,
            onUpdateStage = viewModel::updateStage,
            onSetSpeakers = viewModel::setStageSpeakers,
        )
    }
}
