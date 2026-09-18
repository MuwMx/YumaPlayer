/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
fun NewActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val colors = LocalYumaColors.current
    val containerColor = if (backgroundColor.isSpecified) backgroundColor else colors.glassBorder.copy(alpha = 0.10f)
    val actionContentColor = if (contentColor.isSpecified) contentColor else colors.textPrimary

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .yumaClickable(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
                .yumaGlassCard(
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = containerColor,
                    borderColor = Color.Transparent,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = actionContentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
fun NewMenuItem(
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
    modifier: Modifier = Modifier,
) {
    val shape = remember(index, count) {
        val large = SettingsDimensions.SegmentedCornerLarge
        val small = SettingsDimensions.SegmentedCornerSmall
        when {
            count <= 1 -> RoundedCornerShape(large)
            index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = small)
            index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = large, bottomStart = large)
            else -> RoundedCornerShape(small)
        }
    }
    val position = remember(index, count) { yumaSegmentPosition(index, count) }
    val colors = LocalYumaColors.current
    val isLast = index == count - 1

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else SettingsDimensions.SegmentedItemGap)
            .yumaClickable(enabled = enabled && onClick != null, pressedScale = 0.96f, onClick = onClick ?: {})
            .yumaGlassCard(
                shape = shape,
                backgroundColor = colors.glassBackground,
                borderColor = colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
                position = position,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingContent()
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary)) {
                    headlineContent()
                }
                if (supportingContent != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)) {
                        supportingContent()
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingContent()
                }
            }
        }
    }
}

@Composable
fun NewMenuSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    if (actions.isEmpty()) return

    val colors = LocalYumaColors.current
    val columnCount = columns.coerceAtLeast(1)
    val rows = actions.chunked(columnCount)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge),
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { action ->
                        NewActionButton(
                            icon = action.icon,
                            text = action.text,
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f),
                            enabled = action.enabled,
                            backgroundColor = action.backgroundColor,
                            contentColor = action.contentColor,
                        )
                    }

                    repeat(columnCount - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
)

@Composable
fun NewMenuContent(
    headerContent: @Composable (() -> Unit)? = null,
    actionGrid: @Composable (() -> Unit)? = null,
    menuItems: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        headerContent?.invoke()
        actionGrid?.invoke()
        menuItems?.invoke()
    }
}

@Composable
fun NewIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val colors = LocalYumaColors.current
    val containerColor = if (backgroundColor.isSpecified) backgroundColor else colors.glassBorder.copy(alpha = 0.10f)
    val iconContentColor = if (contentColor.isSpecified) contentColor else colors.textPrimary

    Box(
        modifier = modifier
            .size(40.dp)
            .yumaClickable(enabled = enabled, onClick = onClick)
            .yumaGlassCard(
                shape = RoundedCornerShape(12.dp),
                backgroundColor = containerColor,
                borderColor = Color.Transparent,
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
fun NewMenuContainer(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
    ) {
        content()
    }
}

@Composable
fun MenuSurfaceSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}
