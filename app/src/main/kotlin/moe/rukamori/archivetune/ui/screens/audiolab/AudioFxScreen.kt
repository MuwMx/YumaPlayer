/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.AudioLabViewModel

@Composable
fun AudioFxScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false

    ListenToAudioTap(active = isPlaying)

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.audio_lab_fx_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        val stageShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                .height(148.dp)
                .yumaClickable { navController.navigate("settings/audio_lab/stage") }
                .yumaGlassCard(shape = stageShape)
                .clip(stageShape),
        ) {
            StageSketch(
                stage = state.stage,
                isPlaying = isPlaying,
                stageLevelsProvider = { viewModel.stageLevels },
                modifier = Modifier.matchParentSize(),
            )
            Text(
                text = stringResource(R.string.stage_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(SettingsDimensions.SegmentedItemPaddingHorizontal),
            )
        }

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabReverbSection(
            reverb = state.reverb,
            isPlaying = isPlaying,
            onUpdate = viewModel::updateReverb,
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabEightDSection(
            eightD = state.eightD,
            isPlaying = isPlaying,
            onUpdate = viewModel::updateEightD,
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabLoudnessSection(
            loudness = state.loudness,
            reductionDbProvider = { viewModel.loudnessReductionDb },
            isPlaying = isPlaying,
            onUpdate = viewModel::updateLoudness,
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabSpatialSection(
            spatial = state.spatial,
            isPlaying = isPlaying,
            onUpdate = viewModel::updateSpatial,
        )
    }
}
