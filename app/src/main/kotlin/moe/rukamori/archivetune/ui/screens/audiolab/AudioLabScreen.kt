/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabEqPresets
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.AudioLabViewModel

@Composable
fun AudioLabScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    var showResetDialog by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            StageHubCard(
                enabled = state.stage.enabled,
                onClick = { navController.navigate("settings/audio_lab/stage") },
                sketch = {
                    StageSketch(
                        stage = state.stage,
                        isPlaying = isPlaying,
                        stageLevelsProvider = { viewModel.stageLevels },
                        modifier = Modifier.size(width = 88.dp, height = 64.dp),
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
                                .height(56.dp),
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
                                .height(56.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StageHubCard(
    enabled: Boolean,
    onClick: () -> Unit,
    sketch: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sketch()
        Spacer(Modifier.width(SettingsDimensions.SegmentedIconSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgeSpacing),
            ) {
                Text(
                    text = stringResource(R.string.stage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusPill(active = enabled)
            }
            Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
            Text(
                text = stringResource(R.string.stage_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(SettingsDimensions.RowChevronSpacing))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(SettingsDimensions.ChevronSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SettingsDimensions.RowChevronAlpha),
        )
    }
}

@Composable
private fun SummarySketchCard(
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sketch: @Composable () -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Column(
        modifier = modifier
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
    ) {
        sketch()
        Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SettingsDimensions.BadgeCornerRadius))
            .background(bg)
            .padding(
                horizontal = SettingsDimensions.BadgePaddingH,
                vertical = SettingsDimensions.BadgePaddingV,
            ),
    ) {
        Text(
            text = stringResource(if (active) R.string.enabled else R.string.disabled),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}
