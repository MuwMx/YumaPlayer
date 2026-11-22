/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabEqPreset
import moe.rukamori.archivetune.audiodsp.AudioLabEqPresets
import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.EightDParams
import moe.rukamori.archivetune.audiodsp.LoudnessParams
import moe.rukamori.archivetune.audiodsp.ReverbParams
import moe.rukamori.archivetune.audiodsp.SpatialMode
import moe.rukamori.archivetune.audiodsp.SpatialParams
import moe.rukamori.archivetune.audiodsp.StandardEqFrequencies
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun AudioLabEqSection(
    eq: AudioLabEqSettings,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (AudioLabEqPreset) -> Unit,
    onPreampChange: (Float) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_lab_eq_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_eq_enabled)) },
                description = stringResource(
                    if (eq.enabled) R.string.audio_lab_eq_status_dsp_active else R.string.audio_lab_eq_status_system_active,
                ),
                icon = { Icon(painterResource(R.drawable.equalizer), null) },
                checked = eq.enabled,
                onCheckedChange = onEnabledChange,
            )
        }
        item(visible = eq.enabled) {
            ListPreference(
                title = { Text(stringResource(R.string.audio_lab_eq_preset)) },
                icon = { Icon(painterResource(R.drawable.tune), null) },
                selectedValue = AudioLabEqPresets.byId(eq.presetId) ?: AudioLabEqPresets.Flat,
                values = AudioLabEqPresets.All,
                valueText = { preset -> eqPresetLabel(preset) },
                onValueSelected = onPresetSelected,
            )
        }
        item(visible = eq.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_eq_preamp),
                value = eq.preampDb,
                range = -12f..12f,
                valueText = String.format(Locale.US, "%+.1f dB", eq.preampDb),
                onValueChange = onPreampChange,
            )
        }
        item(visible = eq.enabled) {
            EqBandsCard(
                bandGainsDb = eq.bandGainsDb,
                onBandGainChange = onBandGainChange,
            )
        }
        item(visible = eq.enabled) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.audio_lab_eq_reset)) },
                icon = { Icon(painterResource(R.drawable.replay), null) },
                onClick = onReset,
            )
        }
    }
}

@Composable
internal fun AudioLabSpatialSection(
    spatial: SpatialParams,
    isPlaying: Boolean,
    onUpdate: ((SpatialParams) -> SpatialParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_lab_spatial_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            FxPictureSegment {
                SpatialPicture(spatial = spatial, isPlaying = isPlaying)
            }
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_spatial_enabled)) },
                description = stringResource(R.string.audio_lab_spatial_subtitle),
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = spatial.enabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )
        }
        item(visible = spatial.enabled) {
            EnumListPreference(
                title = { Text(stringResource(R.string.audio_lab_spatial_mode)) },
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                selectedValue = spatial.mode,
                valueText = { mode ->
                    when (mode) {
                        SpatialMode.Crossfeed -> stringResource(R.string.audio_lab_spatial_mode_crossfeed)
                        SpatialMode.Speakers -> stringResource(R.string.audio_lab_spatial_mode_speakers)
                    }
                },
                onValueSelected = { mode -> onUpdate { it.copy(mode = mode) } },
            )
        }
        item(visible = spatial.enabled && spatial.mode == SpatialMode.Crossfeed) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_spatial_cutoff),
                value = spatial.cutoffHz,
                range = 200f..2000f,
                valueText = "${spatial.cutoffHz.roundToInt()} Hz",
                onValueChange = { v -> onUpdate { it.copy(cutoffHz = v) } },
            )
        }
        item(visible = spatial.enabled && spatial.mode == SpatialMode.Crossfeed) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_spatial_feed),
                value = spatial.feedDb,
                range = 0f..12f,
                valueText = String.format(Locale.US, "%.1f dB", spatial.feedDb),
                onValueChange = { v -> onUpdate { it.copy(feedDb = v) } },
            )
        }
        item(visible = spatial.enabled && spatial.mode == SpatialMode.Speakers) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_spatial_speaker_angle),
                value = spatial.speakerAngle,
                range = 10f..60f,
                valueText = "${spatial.speakerAngle.roundToInt()}°",
                onValueChange = { v -> onUpdate { it.copy(speakerAngle = v) } },
            )
        }
        item(visible = spatial.enabled && spatial.mode == SpatialMode.Speakers) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_spatial_amount),
                value = spatial.amount,
                range = 0f..1f,
                valueText = "${(spatial.amount * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(amount = v) } },
            )
        }
        item(visible = spatial.enabled) {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_spatial_surround)) },
                checked = spatial.surround,
                onCheckedChange = { surround -> onUpdate { it.copy(surround = surround) } },
            )
        }
        item(visible = spatial.enabled) {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_spatial_mono)) },
                checked = spatial.mono,
                onCheckedChange = { mono -> onUpdate { it.copy(mono = mono) } },
            )
        }
    }
}

@Composable
internal fun AudioLabReverbSection(
    reverb: ReverbParams,
    isPlaying: Boolean,
    onUpdate: ((ReverbParams) -> ReverbParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_lab_reverb_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            FxPictureSegment {
                ReverbPicture(reverb = reverb, isPlaying = isPlaying)
            }
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_reverb_enabled)) },
                description = stringResource(R.string.audio_lab_reverb_subtitle),
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = reverb.enabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_mix),
                value = reverb.mix,
                range = 0f..1f,
                valueText = "${(reverb.mix * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(mix = v) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_room_size),
                value = reverb.roomSize,
                range = 0f..1f,
                valueText = "${(reverb.roomSize * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(roomSize = v) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_damping),
                value = reverb.damping,
                range = 0f..1f,
                valueText = "${(reverb.damping * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(damping = v) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_width),
                value = reverb.width,
                range = 0f..1f,
                valueText = "${(reverb.width * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(width = v) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_predelay),
                value = reverb.preDelayMs,
                range = 0f..100f,
                valueText = "${reverb.preDelayMs.roundToInt()} ms",
                onValueChange = { v -> onUpdate { it.copy(preDelayMs = v) } },
            )
        }
        item(visible = reverb.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_reverb_low_cut),
                value = reverb.lowCutHz,
                range = 20f..1000f,
                valueText = "${reverb.lowCutHz.roundToInt()} Hz",
                onValueChange = { v -> onUpdate { it.copy(lowCutHz = v) } },
            )
        }
    }
}

@Composable
internal fun AudioLabLoudnessSection(
    loudness: LoudnessParams,
    reductionDbProvider: () -> Float,
    isPlaying: Boolean,
    onUpdate: ((LoudnessParams) -> LoudnessParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_lab_loudness_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            FxPictureSegment {
                LoudnessPicture(
                    loudness = loudness,
                    reductionDbProvider = reductionDbProvider,
                    isPlaying = isPlaying,
                )
            }
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_loudness_enabled)) },
                description = stringResource(R.string.audio_lab_loudness_subtitle),
                icon = { Icon(painterResource(R.drawable.volume_up), null) },
                checked = loudness.enabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )
        }
        item(visible = loudness.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_loudness_boost),
                value = loudness.boostDb,
                range = 0f..12f,
                valueText = String.format(Locale.US, "%.1f dB", loudness.boostDb),
                onValueChange = { v -> onUpdate { it.copy(boostDb = v) } },
            )
        }
        item(visible = loudness.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_loudness_compression),
                value = loudness.compression,
                range = 0f..1f,
                valueText = "${(loudness.compression * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(compression = v) } },
            )
        }
    }
}

@Composable
internal fun AudioLabEightDSection(
    eightD: EightDParams,
    isPlaying: Boolean,
    onUpdate: ((EightDParams) -> EightDParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.audio_lab_eight_d_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            FxPictureSegment {
                EightDPicture(eightD = eightD, isPlaying = isPlaying)
            }
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_lab_eight_d_enabled)) },
                description = stringResource(R.string.audio_lab_eight_d_subtitle),
                icon = { Icon(painterResource(R.drawable.speed), null) },
                checked = eightD.enabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )
        }
        item(visible = eightD.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_eight_d_speed),
                value = eightD.rotationSpeedHz,
                range = 0.05f..1f,
                valueText = String.format(Locale.US, "%.2f Hz", eightD.rotationSpeedHz),
                onValueChange = { v -> onUpdate { it.copy(rotationSpeedHz = v) } },
            )
        }
        item(visible = eightD.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_eight_d_depth),
                value = eightD.depth,
                range = 0f..1f,
                valueText = "${(eightD.depth * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(depth = v) } },
            )
        }
        item(visible = eightD.enabled) {
            FxSliderEntry(
                title = stringResource(R.string.audio_lab_eight_d_reverb),
                value = eightD.reverbLevel,
                range = 0f..1f,
                valueText = "${(eightD.reverbLevel * 100).roundToInt()}%",
                onValueChange = { v -> onUpdate { it.copy(reverbLevel = v) } },
            )
        }
    }
}

@Composable
private fun FxPictureSegment(
    content: @Composable () -> Unit,
) {
    PreferenceEntry(
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            ) {
                content()
            }
        },
    )
}

@Composable
private fun FxSliderEntry(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    PreferenceEntry(
        title = { Text(title) },
        trailingContent = {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = {
            Slider(
                value = value.coerceIn(range),
                onValueChange = onValueChange,
                valueRange = range,
            )
        },
    )
}

@Composable
private fun EqBandsCard(
    bandGainsDb: List<Float>,
    onBandGainChange: (Int, Float) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    PreferenceEntry(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SettingsDimensions.RowTextSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StandardEqFrequencies.forEachIndexed { index, freq ->
                    val gain = bandGainsDb.getOrElse(index) { 0f }.coerceIn(-12f, 12f)
                    val label = if (freq >= 1000) "${freq / 1000}k" else "$freq"
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .pointerInput(index) {
                                    detectTapGestures { offset ->
                                        val norm = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                        onBandGainChange(index, (norm * 24f - 12f).coerceIn(-12f, 12f))
                                    }
                                }
                                .pointerInput(index) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val norm = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                        onBandGainChange(index, (norm * 24f - 12f).coerceIn(-12f, 12f))
                                    }
                                },
                        ) {
                            val cx = size.width * 0.5f
                            val top = 8.dp.toPx()
                            val bottom = size.height - 8.dp.toPx()
                            val midY = (top + bottom) * 0.5f
                            val norm = ((gain + 12f) / 24f).coerceIn(0f, 1f)
                            val thumbY = bottom - norm * (bottom - top)
                            drawLine(
                                color = trackColor,
                                start = Offset(cx, top),
                                end = Offset(cx, bottom),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = primary,
                                start = Offset(cx, midY),
                                end = Offset(cx, thumbY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = Offset(cx, thumbY))
                            drawCircle(color = primary, radius = 4.5.dp.toPx(), center = Offset(cx, thumbY))
                        }
                        Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun eqPresetLabel(preset: AudioLabEqPreset): String = when (preset.id) {
    "flat" -> stringResource(R.string.audio_lab_preset_flat)
    "bass_boost" -> stringResource(R.string.audio_lab_preset_bass_boost)
    "vocal" -> stringResource(R.string.audio_lab_preset_vocal)
    "treble_boost" -> stringResource(R.string.audio_lab_preset_treble_boost)
    "warm" -> stringResource(R.string.audio_lab_preset_warm)
    "studio" -> stringResource(R.string.audio_lab_preset_studio)
    "club" -> stringResource(R.string.audio_lab_preset_club)
    "acoustic" -> stringResource(R.string.audio_lab_preset_acoustic)
    else -> preset.name
}
