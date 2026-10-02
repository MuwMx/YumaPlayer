/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqCapabilities

@Composable
internal fun EqualizerBandsSection(
    caps: EqCapabilities,
    bandCount: Int,
    bandLevelsMb: List<Int>,
    minMb: Int,
    maxMb: Int,
    onResetBands: () -> Unit,
    onBandLevelsChange: (List<Int>) -> Unit,
    onBandLevelsChangeFinished: () -> Unit,
) {
    EqSection(
        title = stringResource(R.string.eq_bands),
        trailing = {
            TextButton(
                onClick = onResetBands,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(R.string.reset))
            }
        },
    ) {
        caps.centerFreqHz.forEachIndexed { band, hz ->
            val label = formatHz(hz)
            val value = bandLevelsMb.getOrNull(band) ?: 0
            val valueDb = (value / 100f).coerceIn(-24f, 24f)

            EqBandSliderRow(
                label = label,
                value = value,
                valueLabel = formatDb(valueDb),
                minMb = minMb,
                maxMb = maxMb,
                onValueChange = { newValue ->
                    val coerced = newValue.toInt().coerceIn(minMb, maxMb)
                    val updated =
                        bandLevelsMb.toMutableList().apply {
                            while (size < bandCount) add(0)
                            set(band, coerced)
                        }
                    onBandLevelsChange(updated)
                },
                onValueChangeFinished = onBandLevelsChangeFinished,
            )

            if (band != caps.centerFreqHz.lastIndex) {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun EqualizerEffectsSections(
    outputGainEnabled: Boolean,
    outputGainLocal: Int,
    bassBoostEnabled: Boolean,
    bassBoostStrengthLocal: Int,
    virtualizerEnabled: Boolean,
    virtualizerStrengthLocal: Int,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainLocalChange: (Int) -> Unit,
    onOutputGainFinished: (Int) -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Int) -> Unit,
    onBassBoostFinished: (Int) -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onVirtualizerFinished: (Int) -> Unit,
) {
    EqSection(title = stringResource(R.string.eq_output_gain)) {
        EqToggleSliderRow(
            enabled = outputGainEnabled,
            onEnabledChange = onOutputGainEnabledChange,
            value = outputGainLocal,
            onValueChange = onOutputGainLocalChange,
            valueRange = -1500..1500,
            formatValue = { formatDb(it / 100f) },
            onValueChangeFinished = { onOutputGainFinished(outputGainLocal) },
        )
    }

    Spacer(Modifier.height(16.dp))

    EqSection(title = stringResource(R.string.eq_bass_boost)) {
        EqToggleSliderRow(
            enabled = bassBoostEnabled,
            onEnabledChange = onBassBoostEnabledChange,
            value = bassBoostStrengthLocal,
            onValueChange = onBassBoostStrengthChange,
            valueRange = 0..1000,
            formatValue = { "${it / 10}%" },
            onValueChangeFinished = { onBassBoostFinished(bassBoostStrengthLocal) },
        )
    }

    Spacer(Modifier.height(16.dp))

    EqSection(title = stringResource(R.string.eq_virtualizer)) {
        EqToggleSliderRow(
            enabled = virtualizerEnabled,
            onEnabledChange = onVirtualizerEnabledChange,
            value = virtualizerStrengthLocal,
            onValueChange = onVirtualizerStrengthChange,
            valueRange = 0..1000,
            formatValue = { "${it / 10}%" },
            onValueChangeFinished = { onVirtualizerFinished(virtualizerStrengthLocal) },
        )
    }
}
