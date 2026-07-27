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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

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
                .yumaClickable(enabled = enabled, onClick = onClick)
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
    modifier: Modifier = Modifier,
) {
    val sizedLeadingContent: @Composable (() -> Unit)? =
        if (leadingContent != null) {
            {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingContent()
                }
            }
        } else {
            null
        }

    val sizedTrailingContent: @Composable (() -> Unit)? =
        if (trailingContent != null) {
            {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingContent()
                }
            }
        } else {
            null
        }

    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = headlineContent,
            leadingContent = sizedLeadingContent,
            trailingContent = sizedTrailingContent,
            supportingContent = supportingContent,
            modifier = Modifier.padding(horizontal = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            tonalElevation = 0.dp,
        )
    }

    if (onClick == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .yumaClickable(enabled = enabled, onClick = onClick),
        ) {
            content()
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

    val columnCount = columns.coerceAtLeast(1)
    val rows = actions.chunked(columnCount)

    Column(
        modifier = modifier,
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
    val colors = LocalYumaColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        headerContent?.invoke()
        actionGrid?.invoke()

        if (actionGrid != null && menuItems != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = colors.glassBorder,
            )
        }

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
    val colors = LocalYumaColors.current
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                ),
    ) {
        Column(content = content)
    }
}
