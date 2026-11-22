/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.StagePreset
import moe.rukamori.archivetune.audiodsp.StagePresets
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSpeakers
import moe.rukamori.archivetune.audiodsp.StageWalls
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import java.util.Locale

@Composable
internal fun StagePresetsPanel(
    selectedPresetId: String?,
    onPresetSelected: (StagePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stage_preset),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH),
        ) {
            items(
                items = StagePresets.All,
                key = { it.id },
            ) { preset ->
                StageChip(
                    label = stagePresetLabel(preset.id),
                    selected = preset.id == selectedPresetId,
                    onClick = { onPresetSelected(preset) },
                )
            }
        }
    }
}

@Composable
internal fun StageRoomSettingsPanel(
    stage: StageSettings,
    onUpdateStage: ((StageSettings) -> StageSettings) -> Unit,
    onSetSpeakers: (StageSpeakers) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.stage_room_size),
        modifier = modifier,
    ) {
        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.stage_walls)) },
                icon = { Icon(painterResource(R.drawable.tune), null) },
                selectedValue = stage.walls,
                valueText = { wall ->
                    when (wall) {
                        StageWalls.Soft -> stringResource(R.string.stage_walls_soft)
                        StageWalls.Wood -> stringResource(R.string.stage_walls_wood)
                        StageWalls.Concrete -> stringResource(R.string.stage_walls_concrete)
                    }
                },
                onValueSelected = { wall -> onUpdateStage { it.copy(presetId = null, walls = wall) } },
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.stage_room_size)) },
                trailingContent = { Text("${(stage.roomSize * 100).toInt()}%", style = MaterialTheme.typography.labelLarge) },
                content = {
                    Slider(
                        value = stage.roomSize,
                        onValueChange = { v -> onUpdateStage { it.copy(presetId = null, roomSize = v) } },
                        valueRange = 0f..1f,
                    )
                },
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.stage_room_amount)) },
                trailingContent = { Text("${(stage.roomAmount * 100).toInt()}%", style = MaterialTheme.typography.labelLarge) },
                content = {
                    Slider(
                        value = stage.roomAmount,
                        onValueChange = { v -> onUpdateStage { it.copy(presetId = null, roomAmount = v) } },
                        valueRange = 0f..1f,
                    )
                },
            )
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.stage_master_level)) },
                trailingContent = {
                    Text(String.format(Locale.US, "%+.1f dB", stage.levelDb), style = MaterialTheme.typography.labelLarge)
                },
                content = {
                    Slider(
                        value = stage.levelDb,
                        onValueChange = { v -> onUpdateStage { it.copy(levelDb = v) } },
                        valueRange = -12f..6f,
                    )
                },
            )
        }
        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.stage_speakers_mode)) },
                icon = { Icon(painterResource(R.drawable.volume_up), null) },
                selectedValue = stage.onSpeakers,
                valueText = { mode ->
                    when (mode) {
                        StageSpeakers.Off -> stringResource(R.string.stage_speakers_off)
                        StageSpeakers.Stereo -> stringResource(R.string.stage_speakers_stereo)
                        StageSpeakers.Keep3D -> stringResource(R.string.stage_speakers_keep_3d)
                    }
                },
                onValueSelected = onSetSpeakers,
            )
        }
    }
}

@Composable
internal fun StageAxisSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String = String.format(Locale.US, "%.2f", value),
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(0.62f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.18f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun StageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dotColor: Color? = null,
) {
    val colors = LocalYumaColors.current
    val chipShape = RoundedCornerShape(SettingsDimensions.PreferenceChipCornerRadius)
    val bg = if (selected) MaterialTheme.colorScheme.primary else colors.glassBackground
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(SettingsDimensions.PreferenceChipHeight)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(
                shape = chipShape,
                backgroundColor = bg,
                borderColor = if (selected) MaterialTheme.colorScheme.primary else colors.glassBorder,
            )
            .clip(chipShape)
            .padding(
                horizontal = SettingsDimensions.PreferenceChipPaddingH,
                vertical = SettingsDimensions.PreferenceChipPaddingV,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH),
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (selected) fg else dotColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun stagePresetLabel(id: String): String = when (id) {
    "studio" -> stringResource(R.string.stage_preset_studio)
    "living" -> stringResource(R.string.stage_preset_living)
    "front_row" -> stringResource(R.string.stage_preset_front_row)
    "on_stage" -> stringResource(R.string.stage_preset_on_stage)
    "around" -> stringResource(R.string.stage_preset_around)
    "hall" -> stringResource(R.string.stage_preset_hall)
    else -> id
}
