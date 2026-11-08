/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.component.rememberPreferenceIconShape
import moe.rukamori.archivetune.ui.screens.settings.SettingsItem
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
fun SettingsRow(
    item: SettingsItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent = if (item.accentColor.isSpecified) item.accentColor else MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "rowScale",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .focusable()
                .clickable(interactionSource = interactionSource, indication = null, onClick = item.onClick)
                .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconShape = rememberPreferenceIconShape(item.title)
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.RowIconSize)
                    .clip(iconShape)
                    .background(color = effectiveAccent.copy(alpha = SettingsDimensions.RowIconBgAlpha), shape = iconShape),
                contentAlignment = Alignment.Center,
            ) {
                val iconModifier = Modifier.size(SettingsDimensions.RowIconInnerSize)
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = { Badge(containerColor = MaterialTheme.colorScheme.error, modifier = Modifier.size(SettingsDimensions.BadgeSize)) },
                    ) {
                        Icon(painter = item.icon, contentDescription = null, tint = effectiveAccent, modifier = iconModifier)
                    }
                } else {
                    Icon(painter = item.icon, contentDescription = null, tint = effectiveAccent, modifier = iconModifier)
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.RowIconSpacing))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(SettingsDimensions.RowTextSpacing))
                    MarqueeText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.showUpdateIndicator) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(SettingsDimensions.BadgeSpacing))
                Surface(
                    shape = RoundedCornerShape(SettingsDimensions.BadgeCornerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SettingsDimensions.BadgePaddingH, vertical = SettingsDimensions.BadgePaddingV),
                    )
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.RowChevronSpacing))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SettingsDimensions.RowChevronAlpha),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                thickness = SettingsDimensions.DividerThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = SettingsDimensions.DividerAlpha),
            )
        }
    }
}

@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.2f,
        modifier = modifier.padding(
            horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
            vertical = SettingsDimensions.SectionHeaderBottomPadding,
        ),
    )
}

@Composable
fun SettingsSegmentedItem(
    item: SettingsItem,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent = if (item.accentColor.isSpecified) item.accentColor else MaterialTheme.colorScheme.primary
    val iconContentCandidate = contentColorFor(effectiveAccent)
    val iconContentColor = if (iconContentCandidate.isSpecified) iconContentCandidate else MaterialTheme.colorScheme.surface
    val shape = remember(index, count) { segmentedSettingsItemShape(index, count) }
    val position = remember(index, count) { yumaSegmentPosition(index, count) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "settingsSegmentScale",
    )

    val colors = LocalYumaColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .yumaGlassCard(shape = shape, backgroundColor = colors.glassBackground, borderColor = colors.glassBorder, position = position)
            .clip(shape)
            .focusable()
            .clickable(interactionSource = interactionSource, indication = null, onClick = item.onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsDimensions.SegmentedItemMinHeight)
                .padding(horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal, vertical = SettingsDimensions.SegmentedItemPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconShape = rememberPreferenceIconShape(item.title)
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.SegmentedIconBoxSize)
                    .clip(iconShape)
                    .background(effectiveAccent),
                contentAlignment = Alignment.Center,
            ) {
                val iconModifier = Modifier.size(SettingsDimensions.SegmentedIconSize)
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = { Badge(containerColor = MaterialTheme.colorScheme.error, modifier = Modifier.size(SettingsDimensions.BadgeSize)) },
                    ) {
                        Icon(painter = item.icon, contentDescription = null, tint = iconContentColor, modifier = iconModifier)
                    }
                } else {
                    Icon(painter = item.icon, contentDescription = null, tint = iconContentColor, modifier = iconModifier)
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.SegmentedIconSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                MarqueeText(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedRowSpacing))
                    MarqueeText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
            }

            item.badge?.let { badge ->
                Spacer(modifier = Modifier.width(SettingsDimensions.SegmentedBadgeSpacing))
                Surface(
                    shape = RoundedCornerShape(SettingsDimensions.SegmentedBadgeCornerPercent),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SettingsDimensions.SegmentedBadgePaddingH, vertical = SettingsDimensions.SegmentedBadgePaddingV),
                    )
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.RowChevronSpacing))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SettingsDimensions.RowChevronAlpha),
            )
        }
    }
}

fun segmentedSettingsItemShape(index: Int, count: Int): Shape {
    val large = SettingsDimensions.SegmentedCornerLarge
    val small = SettingsDimensions.SegmentedCornerSmall
    return when {
        count <= 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = small)
        index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = large, bottomStart = large)
        else -> RoundedCornerShape(small)
    }
}
