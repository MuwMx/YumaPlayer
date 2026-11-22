/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.StageNativeConstants
import moe.rukamori.archivetune.audiodsp.StageObject
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.ui.component.PreferenceSelectionOption
import moe.rukamori.archivetune.ui.screens.settings.components.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
internal fun StageAddSheet(
    objects: List<StageObject>,
    onAddSource: (StageSource) -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val hideSheet: (() -> Unit) -> Unit = remember(coroutineScope, sheetState) {
        { completion ->
            coroutineScope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                completion()
            }
        }
    }

    val sources = remember { StageSource.entries }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding)
                .padding(bottom = SettingsDimensions.BottomSheetBottomPadding)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = SettingsDimensions.BottomSheetTonalElevation,
            shadowElevation = SettingsDimensions.BottomSheetShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                    .padding(
                        top = SettingsDimensions.BottomSheetContentPaddingTop,
                        bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = SettingsDimensions.BottomSheetDragHandleBottomPadding)
                        .size(
                            width = SettingsDimensions.BottomSheetDragHandleWidth,
                            height = SettingsDimensions.BottomSheetDragHandleHeight,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )

                ProvideTextStyle(
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SettingsDimensions.BottomSheetTitleBottomPadding),
                    ) {
                        Text(stringResource(R.string.stage_action_add))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SettingsDimensions.BottomSheetListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                ) {
                    itemsIndexed(
                        items = sources,
                        key = { _, item -> item.name },
                    ) { index, source ->
                        val count = objects.count { it.source == source }
                        val canAdd = count < StageNativeConstants.Copies
                        val shape = remember(index, sources.size) {
                            segmentedSettingsItemShape(index, sources.size)
                        }
                        val position = remember(index, sources.size) {
                            yumaSegmentPosition(index, sources.size)
                        }
                        val countDesc = "$count / ${StageNativeConstants.Copies}"
                        val description = if (canAdd) {
                            countDesc
                        } else {
                            "$countDesc · ${stringResource(R.string.stage_max_copies_reached)}"
                        }
                        PreferenceSelectionOption(
                            text = stageSourceLabel(source),
                            description = description,
                            selected = false,
                            shape = shape,
                            position = position,
                            modifier = Modifier.alpha(if (canAdd) 1f else 0.5f),
                            onClick = {
                                if (canAdd) {
                                    hideSheet {
                                        onAddSource(source)
                                        onDismiss()
                                    }
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(SettingsDimensions.SegmentGap))
            }
        }
    }
}

@Composable
internal fun stageSourceLabel(source: StageSource): String = when (source) {
    StageSource.Vocal -> stringResource(R.string.stage_source_vocal)
    StageSource.Center -> stringResource(R.string.stage_source_center)
    StageSource.Left -> stringResource(R.string.stage_source_left)
    StageSource.Right -> stringResource(R.string.stage_source_right)
    StageSource.Ambience -> stringResource(R.string.stage_source_ambience)
    StageSource.Sub -> stringResource(R.string.stage_source_sub)
}
