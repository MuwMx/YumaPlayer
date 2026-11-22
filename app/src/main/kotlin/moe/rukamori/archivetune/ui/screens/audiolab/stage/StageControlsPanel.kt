/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.StageListener
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import java.util.Locale

@Composable
internal fun StageControlsPanel(
    stage: StageSettings,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onUpdateObject: (Int, (StageObject) -> StageObject) -> Unit,
    onUpdateListener: ((StageListener) -> StageListener) -> Unit,
    onRemoveObject: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberStageCanvasPalette()
    PreferenceGroup(
        title = stringResource(R.string.stage_title),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.stage_enabled)) },
                description = stringResource(R.string.stage_subtitle),
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = stage.enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        item(visible = stage.enabled) {
            PreferenceEntry(
                title = {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH),
                        contentPadding = PaddingValues(vertical = SettingsDimensions.RowTextSpacing),
                    ) {
                        item(key = ListenerKey) {
                            StageChip(
                                label = stringResource(R.string.stage_label_you),
                                selected = selectedKey == ListenerKey,
                                onClick = { onSelect(ListenerKey) },
                                dotColor = palette.listener,
                            )
                        }
                        items(
                            items = stage.objects,
                            key = { it.id },
                        ) { obj ->
                            val sameCount = stage.objects.count { it.source == obj.source }
                            val indexInType = stage.objects.filter { it.source == obj.source }.indexOfFirst { it.id == obj.id } + 1
                            val base = stageSourceLabel(obj.source)
                            val label = if (sameCount > 1) "$base $indexInType" else base
                            StageChip(
                                label = label,
                                selected = selectedKey == obj.id,
                                onClick = { onSelect(obj.id) },
                                dotColor = palette.colorFor(obj.source),
                            )
                        }
                    }
                },
            )
        }

        val selectedObject = stage.objects.firstOrNull { it.id == selectedKey }
        item(visible = stage.enabled && selectedKey == ListenerKey) {
            ListenerControlCard(
                listener = stage.listener,
                onUpdateListener = onUpdateListener,
            )
        }

        item(visible = stage.enabled && selectedObject != null) {
            if (selectedObject != null) {
                val canRemove = stage.objects.count { it.source == selectedObject.source } > 1
                ObjectControlCard(
                    obj = selectedObject,
                    canRemove = canRemove,
                    onUpdateObject = { transform -> onUpdateObject(selectedObject.id, transform) },
                    onRemove = { onRemoveObject(selectedObject.id) },
                )
            }
        }
    }
}

@Composable
private fun ListenerControlCard(
    listener: StageListener,
    onUpdateListener: ((StageListener) -> StageListener) -> Unit,
) {
    PreferenceEntry(
        title = { Text(stringResource(R.string.stage_label_you)) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.RowTextSpacing)) {
                StageAxisSlider("X", listener.x, -1f..1f) { v -> onUpdateListener { it.copy(x = v) } }
                StageAxisSlider("Y", listener.y, 0f..1f) { v -> onUpdateListener { it.copy(y = v) } }
                StageAxisSlider("Z", listener.z, -1f..1f) { v -> onUpdateListener { it.copy(z = v) } }
                StageAxisSlider(
                    label = "Yaw",
                    value = listener.yaw,
                    range = -180f..180f,
                    valueText = "${listener.yaw.toInt()}°",
                ) { v -> onUpdateListener { it.copy(yaw = v) } }
            }
        },
    )
}

@Composable
private fun ObjectControlCard(
    obj: StageObject,
    canRemove: Boolean,
    onUpdateObject: ((StageObject) -> StageObject) -> Unit,
    onRemove: () -> Unit,
) {
    PreferenceEntry(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stageSourceLabel(obj.source))
                Row(horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH)) {
                    StageChip(
                        label = stringResource(if (obj.muted) R.string.stage_action_unmute else R.string.stage_action_mute),
                        selected = obj.muted,
                        onClick = { onUpdateObject { it.copy(muted = !it.muted) } },
                    )
                    StageChip(
                        label = stringResource(if (obj.solo) R.string.stage_action_unsolo else R.string.stage_action_solo),
                        selected = obj.solo,
                        onClick = { onUpdateObject { it.copy(solo = !it.solo) } },
                    )
                    if (canRemove) {
                        Box(
                            modifier = Modifier
                                .size(SettingsDimensions.PreferenceChipHeight)
                                .yumaClickable(onClick = onRemove)
                                .clip(RoundedCornerShape(SettingsDimensions.PreferenceChipCornerRadius))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.stage_action_remove),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(SettingsDimensions.ChevronSize),
                            )
                        }
                    }
                }
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsDimensions.RowTextSpacing)) {
                StageAxisSlider("X", obj.x, -1f..1f) { v -> onUpdateObject { it.copy(x = v) } }
                StageAxisSlider("Y", obj.y, 0f..1f) { v -> onUpdateObject { it.copy(y = v) } }
                StageAxisSlider("Z", obj.z, -1f..1f) { v -> onUpdateObject { it.copy(z = v) } }
                StageAxisSlider(
                    label = stringResource(R.string.stage_object_level),
                    value = obj.levelDb,
                    range = -12f..6f,
                    valueText = String.format(Locale.US, "%+.1f dB", obj.levelDb),
                ) { v -> onUpdateObject { it.copy(levelDb = v) } }
                if (obj.source == StageSource.Ambience) {
                    StageAxisSlider(
                        label = stringResource(R.string.stage_object_width),
                        value = obj.width,
                        range = 0f..1f,
                        valueText = "${(obj.width * 100).toInt()}%",
                    ) { v -> onUpdateObject { it.copy(width = v) } }
                }
            }
        },
    )
}
