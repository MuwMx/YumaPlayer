/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabEqPresets
import moe.rukamori.archivetune.audiodsp.AudioLabSoundProfiles
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.EnableMonochromeKey
import moe.rukamori.archivetune.constants.LosslessProviderOrderKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey
import moe.rukamori.archivetune.constants.TransitionMode
import moe.rukamori.archivetune.constants.TransitionModeKey
import moe.rukamori.archivetune.playback.audio.AudioOutputs
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AudioLabViewModel
import kotlin.math.roundToInt

@Composable
fun AudioLabScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bypassed by viewModel.bypassed.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    var showResetDialog by remember { mutableStateOf(false) }

    val (audioQuality) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (enableLossless) = rememberPreference(EnableLosslessKey, defaultValue = false)
    val (playbackSource) = rememberEnumPreference(PlaybackSourceKey, defaultValue = PlaybackSource.YT_MUSIC)
    val (enableMonochrome) = rememberPreference(EnableMonochromeKey, defaultValue = true)
    val (providerOrderRaw) = rememberPreference(LosslessProviderOrderKey, defaultValue = "monochrome,qbdlx")
    val (qobuzAppId) = rememberPreference(QobuzAppIdKey, defaultValue = "")
    val (qobuzAppSecret) = rememberPreference(QobuzAppSecretKey, defaultValue = "")
    val (qobuzUserAuthToken) = rememberPreference(QobuzUserAuthTokenKey, defaultValue = "")
    val (crossfadeEnabled) = rememberPreference(CrossfadeEnabledKey, defaultValue = false)
    val (crossfadeDurationSeconds) = rememberPreference(CrossfadeDurationKey, defaultValue = 5f)
    val (automixEnabled) = rememberPreference(AutomixEnabledKey, defaultValue = false)
    val (transitionMode) = rememberEnumPreference(TransitionModeKey, defaultValue = TransitionMode.OFF)

    ListenToAudioTap(active = isPlaying)

    if (showResetDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.audio_lab_reset_all),
            onDismiss = { showResetDialog = false },
            onCancel = { showResetDialog = false },
            onConfirm = {
                showResetDialog = false
                viewModel.resetAllFx()
            },
        ) {
            Text(
                text = stringResource(R.string.audio_lab_reset_all_confirm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val activeFxCount = remember(state.spatial.enabled, state.reverb.enabled, state.loudness.enabled, state.eightD.enabled) {
        listOf(state.spatial.enabled, state.reverb.enabled, state.loudness.enabled, state.eightD.enabled).count { it }
    }

    val activeProfileId = remember(state, bypassed) {
        if (bypassed) null else AudioLabSoundProfiles.matchingProfileId(state)
    }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.audio_lab_title)) },
        onBackClick = { navController.navigateUp() },
        actions = {
            IconButton(onClick = { showResetDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = stringResource(R.string.audio_lab_reset_all),
                )
            }
        },
    ) {
        AudioLabSpectrumHero(
            state = state,
            bypassed = bypassed,
            isPlaying = isPlaying,
            onToggleBypass = viewModel::toggleBypass,
            onNavigateEq = { navController.navigate("settings/audio_lab/equalizer") },
            onNavigateStage = { navController.navigate("settings/audio_lab/stage") },
            onNavigateFx = { navController.navigate("settings/audio_lab/fx") },
            modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabSoundProfilesRow(
            activeProfileId = activeProfileId,
            bypassed = bypassed,
            onSelectProfile = viewModel::applySoundProfile,
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            StageHubCard(
                enabled = state.stage.enabled,
                presetLabel = stagePresetLabel(state.stage.presetId),
                sourcesCount = state.stage.objects.size,
                onClick = { navController.navigate("settings/audio_lab/stage") },
                sketch = {
                    StageSketch(
                        stage = state.stage,
                        isPlaying = isPlaying,
                        stageLevelsProvider = { viewModel.stageLevels },
                        modifier = Modifier.size(width = 116.dp, height = 96.dp),
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            ) {
                val eqPresetName = AudioLabEqPresets.byId(state.eq.presetId)?.name ?: stringResource(R.string.custom)
                SummarySketchCard(
                    title = stringResource(R.string.audio_lab_eq_title),
                    subtitle = if (state.eq.enabled) eqPresetName else stringResource(R.string.disabled),
                    active = state.eq.enabled,
                    onClick = { navController.navigate("settings/audio_lab/equalizer") },
                    modifier = Modifier.weight(1f),
                    sketch = {
                        EqualizerSketch(
                            eq = state.eq,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                        )
                    },
                )

                val fxNames = listOfNotNull(
                    if (state.reverb.enabled) stringResource(R.string.audio_lab_reverb_title) else null,
                    if (state.eightD.enabled) stringResource(R.string.audio_lab_eight_d_title) else null,
                    if (state.loudness.enabled) stringResource(R.string.audio_lab_loudness_title) else null,
                    if (state.spatial.enabled) stringResource(R.string.audio_lab_spatial_title) else null,
                )
                SummarySketchCard(
                    title = stringResource(R.string.audio_lab_fx_title),
                    subtitle = if (fxNames.isNotEmpty()) {
                        "${fxNames.size} · ${fxNames.joinToString(", ")}"
                    } else {
                        stringResource(R.string.disabled)
                    },
                    active = activeFxCount > 0,
                    onClick = { navController.navigate("settings/audio_lab/fx") },
                    modifier = Modifier.weight(1f),
                    sketch = {
                        FxSketch(
                            activeCount = activeFxCount,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        val losslessEnabled = enableLossless || playbackSource == PlaybackSource.FLAC
        val qobuzReady = qobuzAppId.isNotBlank() && qobuzAppSecret.isNotBlank() && qobuzUserAuthToken.isNotBlank()
        val monochromeLabel = stringResource(R.string.provider_monochrome)
        val qobuzLabel = stringResource(R.string.provider_qobuz)
        val ytMusicLabel = stringResource(R.string.provider_yt_music)
        val activeLosslessProviders = providerOrderRaw
            .split(",")
            .map { it.trim() }
            .mapNotNull { id ->
                when (id) {
                    "monochrome" -> if (enableMonochrome) monochromeLabel else null
                    "qbdlx" -> if (qobuzReady) qobuzLabel else null
                    else -> null
                }
            }
            .distinct()
        val qualityDescription = if (losslessEnabled) {
            val sourcesSummary = if (activeLosslessProviders.isNotEmpty()) {
                activeLosslessProviders.joinToString(", ")
            } else {
                ytMusicLabel
            }
            stringResource(R.string.audio_quality_lossless_summary, sourcesSummary)
        } else {
            when (audioQuality) {
                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_highest)
                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
            }
        }
        val effectiveTransitionMode = when {
            transitionMode != TransitionMode.OFF -> transitionMode
            automixEnabled -> TransitionMode.AUTOMIX
            crossfadeEnabled -> TransitionMode.CROSSFADE
            else -> TransitionMode.OFF
        }
        val transitionsDescription = when (effectiveTransitionMode) {
            TransitionMode.CROSSMIX -> stringResource(R.string.transition_mode_crossmix)
            TransitionMode.AUTOMIX -> stringResource(
                R.string.transitions_player_summary_automix,
                crossfadeDurationSeconds.roundToInt(),
            )
            TransitionMode.CROSSFADE -> stringResource(
                R.string.transitions_player_summary_crossfade,
                crossfadeDurationSeconds.roundToInt(),
            )
            TransitionMode.OFF -> stringResource(R.string.disabled)
        }

        val outputs = playerConnection?.service?.audioOutputs
        val devices by remember(outputs) { outputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsStateWithLifecycle(emptyList())
        val usbDac = devices.firstOrNull { it.kind == AudioOutputs.Kind.USB }
        val bluetoothDevice = devices.firstOrNull { it.isActive && it.kind == AudioOutputs.Kind.BLUETOOTH }
        val passStatusLabel = when {
            usbDac != null -> stringResource(R.string.audiopass_kind_usb)
            bluetoothDevice != null -> stringResource(R.string.audiopass_status_bluetooth)
            else -> stringResource(R.string.audiopass_status_none)
        }
        val passSubtitle = when {
            usbDac != null -> usbDac.name.ifBlank { stringResource(R.string.audiopass_status_connected) }
            bluetoothDevice != null -> bluetoothDevice.name.ifBlank { stringResource(R.string.audiopass_status_connected) }
            else -> stringResource(R.string.audiopass_status_none_hint)
        }
        val qualityResolution = when {
            losslessEnabled -> 1f
            audioQuality == AudioQuality.HIGHEST -> 0.85f
            audioQuality == AudioQuality.HIGH -> 0.65f
            audioQuality == AudioQuality.AUTO -> 0.55f
            audioQuality == AudioQuality.LOW -> 0.15f
            else -> 0.55f
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.audio_lab_playback_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            ) {
                SummarySketchCard(
                    title = stringResource(R.string.audio_lab_quality_title),
                    subtitle = qualityDescription,
                    active = losslessEnabled,
                    onClick = { navController.navigate("settings/audio_lab/quality") },
                    modifier = Modifier.weight(1f),
                    sketch = {
                        QualitySketch(
                            resolution = qualityResolution,
                            active = losslessEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                        )
                    },
                )

                val transitionsActive = effectiveTransitionMode != TransitionMode.OFF
                SummarySketchCard(
                    title = stringResource(R.string.audio_lab_transitions_title),
                    subtitle = transitionsDescription,
                    active = transitionsActive,
                    onClick = { navController.navigate("settings/audio_lab/transitions") },
                    modifier = Modifier.weight(1f),
                    sketch = {
                        TransitionSketch(
                            active = transitionsActive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                        )
                    },
                )
            }

            AudioPassHubCard(
                statusLabel = passStatusLabel,
                subtitle = passSubtitle,
                active = usbDac != null,
                onClick = { navController.navigate("settings/audio_lab/pass") },
                sketch = {
                    PassSketch(
                        plugged = usbDac != null,
                        modifier = Modifier.size(width = 92.dp, height = 56.dp),
                    )
                },
            )
        }
    }
}
