/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.component.PreferenceSelectionOption
import moe.rukamori.archivetune.ui.screens.settings.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategoryUiModel

@Composable
internal fun SponsorBlockCategoryBottomSheet(
    title: @Composable () -> Unit,
    values: List<SponsorBlockCategoryUiModel>,
    checkedValues: List<SponsorBlockCategoryUiModel>,
    onDismiss: () -> Unit,
    onValueCheckedChange: (SponsorBlockCategoryUiModel, Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val hideSheet: (() -> Unit) -> Unit =
        remember(coroutineScope, sheetState) {
            { completion ->
                coroutineScope
                    .launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        completion()
                    }
            }
        }

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
                        title()
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SettingsDimensions.BottomSheetListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                ) {
                    itemsIndexed(
                        items = values,
                        key = { _, item -> item.category.name },
                    ) { index, option ->
                        val isChecked = option in checkedValues
                        val shape = remember(index, values.size) {
                            segmentedSettingsItemShape(index, values.size)
                        }
                        val position = remember(index, values.size) {
                            yumaSegmentPosition(index, values.size)
                        }
                        PreferenceSelectionOption(
                            text = stringResource(option.labelRes),
                            selected = isChecked,
                            shape = shape,
                            position = position,
                            onClick = { onValueCheckedChange(option, !isChecked) },
                        )
                    }
                }

                Spacer(Modifier.height(SettingsDimensions.BottomSheetActionRowSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { hideSheet(onDismiss) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = stringResource(android.R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.width(SettingsDimensions.BottomSheetActionButtonSpacing))
                    TextButton(
                        onClick = { hideSheet(onConfirm) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = stringResource(android.R.string.ok),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
