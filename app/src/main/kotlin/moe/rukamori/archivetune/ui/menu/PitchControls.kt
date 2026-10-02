/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PitchControls(
    pitch: Float,
    tempo: Float,
    pitchMode: PitchMode,
    onPitchModeChange: (PitchMode) -> Unit,
    onPitchChange: (Float) -> Unit,
    applyPlaybackParameters: (Float, Float) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.discover_tune),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        Text(
            text = stringResource(R.string.pitch),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        Text(
            text =
                when (pitchMode) {
                    PitchMode.Semitones -> {
                        val semitones = pitchToSemitones(pitch)
                        "${if (semitones > 0) "+" else ""}$semitones"
                    }

                    PitchMode.Multiplier -> {
                        "x${formatMultiplier(pitch)}"
                    }
                },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = pitchMode == PitchMode.Semitones,
            onClick = { onPitchModeChange(PitchMode.Semitones) },
            label = { Text(stringResource(R.string.pitch_mode_semitones_short)) },
        )
        FilterChip(
            selected = pitchMode == PitchMode.Multiplier,
            onClick = { onPitchModeChange(PitchMode.Multiplier) },
            label = { Text(stringResource(R.string.pitch_mode_multiplier_short)) },
        )
    }

    when (pitchMode) {
        PitchMode.Semitones -> {
            val currentSemitones = pitchToSemitones(pitch)
            Slider(
                value = currentSemitones.toFloat(),
                onValueChange = { slider ->
                    val semitones = slider.roundToInt().coerceIn(-12, 12)
                    val updated = semitonesToPitch(semitones)
                    if (abs(updated - pitch) >= 0.0005f) {
                        onPitchChange(updated)
                        applyPlaybackParameters(tempo, updated)
                    }
                },
                valueRange = -12f..12f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
            ) {
                val presets = SemitonePresets
                presets.forEach { preset ->
                    val selected = currentSemitones == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = semitonesToPitch(preset)
                            onPitchChange(updated)
                            applyPlaybackParameters(tempo, updated)
                        },
                        label = { Text("${if (preset > 0) "+" else ""}$preset") },
                    )
                }
            }
        }

        PitchMode.Multiplier -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    enabled = pitch > PitchMin,
                    onClick = {
                        val updated = (pitch - 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                        onPitchChange(updated)
                        applyPlaybackParameters(tempo, updated)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.remove),
                        contentDescription = null,
                    )
                }

                Slider(
                    value = multiplierToSlider(pitch),
                    onValueChange = { slider ->
                        val updated = sliderToMultiplier(slider).quantize(0.01f)
                        if (abs(updated - pitch) >= 0.005f) {
                            onPitchChange(updated)
                            applyPlaybackParameters(tempo, updated)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(),
                )

                IconButton(
                    enabled = pitch < PitchMax,
                    onClick = {
                        val updated = (pitch + 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                        onPitchChange(updated)
                        applyPlaybackParameters(tempo, updated)
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
            ) {
                val presets = MultiplierPresets
                presets.forEach { preset ->
                    val selected = abs(pitch - preset) < 0.005f
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onPitchChange(preset)
                            applyPlaybackParameters(tempo, preset)
                        },
                        label = { Text("x${formatMultiplier(preset)}") },
                    )
                }
            }
        }
    }
}
