/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.settings.components.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortField
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortOrder

@Composable
internal fun DownloadSortMenu(
    sortField: DownloadLibrarySortField,
    sortOrder: DownloadLibrarySortOrder,
    onSortFieldSelected: (DownloadLibrarySortField) -> Unit,
    onSortOrderSelected: (DownloadLibrarySortOrder) -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { sheetVisible = true }) {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = stringResource(R.string.sort_by_name),
            )
        }

        if (sheetVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { sheetVisible = false },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                dragHandle = null,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding)
                            .padding(bottom = SettingsDimensions.BottomSheetBottomPadding)
                            .navigationBarsPadding(),
                    shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border =
                        BorderStroke(
                            width = SettingsDimensions.GlassBorderThickness,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    tonalElevation = SettingsDimensions.BottomSheetTonalElevation,
                    shadowElevation = SettingsDimensions.BottomSheetShadowElevation,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                                .padding(
                                    top = SettingsDimensions.BottomSheetContentPaddingTop,
                                    bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                                ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = SettingsDimensions.BottomSheetDragHandleBottomPadding)
                                    .size(
                                        width = SettingsDimensions.BottomSheetDragHandleWidth,
                                        height = SettingsDimensions.BottomSheetDragHandleHeight,
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        )

                        Text(
                            text = stringResource(R.string.sort_by_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )

                        val fieldOptions =
                            listOf(
                                DownloadLibrarySortField.NAME to R.string.sort_by_name,
                                DownloadLibrarySortField.DATE_MODIFIED to R.string.sort_by_last_updated,
                            )

                        fieldOptions.forEachIndexed { index, (field, titleRes) ->
                            val isSelected = sortField == field
                            SortOptionRow(
                                title = stringResource(titleRes),
                                isSelected = isSelected,
                                index = index,
                                count = fieldOptions.size,
                                onClick = {
                                    onSortFieldSelected(field)
                                },
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        val orderOptions =
                            listOf(
                                DownloadLibrarySortOrder.ASCENDING to R.string.sort_order_ascending,
                                DownloadLibrarySortOrder.DESCENDING to R.string.sort_order_descending,
                            )

                        orderOptions.forEachIndexed { index, (order, titleRes) ->
                            val isSelected = sortOrder == order
                            SortOptionRow(
                                title = stringResource(titleRes),
                                isSelected = isSelected,
                                index = index,
                                count = orderOptions.size,
                                onClick = {
                                    onSortOrderSelected(order)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    title: String,
    isSelected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val colors = LocalYumaColors.current
    val shape = segmentedSettingsItemShape(index, count)
    val position = yumaSegmentPosition(index, count)
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            colors.glassBackground
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                )
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = backgroundColor,
                    borderColor = colors.glassBorder,
                    position = position,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .clip(shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
