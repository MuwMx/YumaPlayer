/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import kotlin.math.abs

@Composable
internal fun EqSection(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
internal fun EqBandSliderRow(
    label: String,
    value: Int,
    valueLabel: String,
    minMb: Int,
    maxMb: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val normalizedLevel = abs(value).toFloat() / maxOf(abs(minMb), abs(maxMb), 1)
    val accentColor = if (value < 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val containerColor by animateColorAsState(
        targetValue =
            when {
                value > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + (normalizedLevel * 0.25f))
                value < 0 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f + (normalizedLevel * 0.25f))
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eqBandContainer",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(56.dp),
            )

            Spacer(Modifier.width(8.dp))

            Slider(
                value = value.toFloat().coerceIn(minMb.toFloat(), maxMb.toFloat()),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = minMb.toFloat()..maxMb.toFloat(),
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun EqToggleSliderRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    formatValue: (Int) -> String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val containerColor by animateColorAsState(
        targetValue =
            if (enabled) {
                MaterialTheme.colorScheme.secondaryContainer.copy(
                    alpha = 0.45f,
                )
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "eqToggleContainer",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(id = if (enabled) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )

            Spacer(Modifier.width(12.dp))

            Slider(
                value = value.toFloat().coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
                onValueChange = { onValueChange(it.toInt().coerceIn(valueRange.first, valueRange.last)) },
                onValueChangeFinished = { onValueChangeFinished?.invoke() },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}
