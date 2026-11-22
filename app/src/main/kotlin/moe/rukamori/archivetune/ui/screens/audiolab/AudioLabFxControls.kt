/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.component.PreferenceEntry

@Composable
internal fun FxPictureSegment(
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
internal fun FxSliderEntry(
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
