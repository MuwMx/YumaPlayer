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

@Composable
internal fun TempoControls(
    tempo: Float,
    pitch: Float,
    onTempoChange: (Float) -> Unit,
    applyPlaybackParameters: (Float, Float) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.speed),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        Text(
            text = stringResource(R.string.tempo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "x${formatMultiplier(tempo)}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End,
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            enabled = tempo > TempoMin,
            onClick = {
                val newTempo = (tempo - 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                onTempoChange(newTempo)
                applyPlaybackParameters(newTempo, pitch)
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.remove),
                contentDescription = null,
            )
        }

        Slider(
            value = multiplierToSlider(tempo),
            onValueChange = { slider ->
                val updated = sliderToMultiplier(slider).quantize(0.01f)
                if (abs(updated - tempo) >= 0.005f) {
                    onTempoChange(updated)
                    applyPlaybackParameters(updated, pitch)
                }
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(),
        )

        IconButton(
            enabled = tempo < TempoMax,
            onClick = {
                val newTempo = (tempo + 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                onTempoChange(newTempo)
                applyPlaybackParameters(newTempo, pitch)
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
            val selected = abs(tempo - preset) < 0.005f
            FilterChip(
                selected = selected,
                onClick = {
                    onTempoChange(preset)
                    applyPlaybackParameters(preset, pitch)
                },
                label = { Text("x${formatMultiplier(preset)}") },
            )
        }
    }
}
