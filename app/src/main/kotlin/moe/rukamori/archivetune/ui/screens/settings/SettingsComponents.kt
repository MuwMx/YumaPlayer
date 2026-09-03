/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.component.LocalPreferenceItemIndex
import moe.rukamori.archivetune.ui.component.rememberPreferenceIconShape
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions


@Composable
fun SettingsPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = SettingsDimensions.CardElevation),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SettingsDimensions.BannerContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = SettingsDimensions.BannerIconBgAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.security),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.BannerIconSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.BannerColumnSpacing),
            ) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = SettingsDimensions.BannerSubtitleAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.BannerTextSpacing))

            Button(
                onClick = onRequestPermission,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                contentPadding =
                    PaddingValues(
                        horizontal = SettingsDimensions.BannerButtonPaddingH,
                        vertical = SettingsDimensions.BannerButtonPaddingV,
                    ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.allow),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun SettingsUpdateBanner(
    latestVersion: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val shape = RoundedCornerShape(SettingsDimensions.BannerCardCornerRadius)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                )
                .yumaClickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SettingsDimensions.BannerContentPadding,
                        vertical = SettingsDimensions.BannerIconSpacing,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.BannerIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SettingsDimensions.BannerIconBgAlphaSoft)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(SettingsDimensions.BannerIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.BannerIconSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.BannerColumnSpacing),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (latestVersion.startsWith("v", ignoreCase = true)) latestVersion else "v$latestVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SettingsDimensions.BannerVersionAlpha),
                    fontWeight = FontWeight.Medium,
                )
            }

            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SettingsDimensions.BannerCloseAlpha),
                    modifier = Modifier.size(SettingsDimensions.BannerCloseIconSize),
                )
            }
        }
    }
}


@Composable
fun SettingsRow(
    item: SettingsItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectiveAccent =
        if (item.accentColor.isSpecified) {
            item.accentColor
        } else {
            MaterialTheme.colorScheme.primary
        }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "rowScale",
    )

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.focusable()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = item.onClick,
                    ).padding(
                        horizontal = SettingsDimensions.RowHorizontalPadding,
                        vertical = SettingsDimensions.RowVerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconShape = rememberPreferenceIconShape(item.title)
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.RowIconSize)
                    .clip(iconShape)
                    .background(
                        color = effectiveAccent.copy(alpha = SettingsDimensions.RowIconBgAlpha),
                        shape = iconShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(SettingsDimensions.BadgeSize),
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = effectiveAccent,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = effectiveAccent,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
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
                    modifier = Modifier,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(SettingsDimensions.RowTextSpacing))
                    MarqueeText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (item.showUpdateIndicator) {
                                effectiveAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        modifier = Modifier,
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
                        modifier = Modifier.padding(
                            horizontal = SettingsDimensions.BadgePaddingH,
                            vertical = SettingsDimensions.BadgePaddingV,
                        ),
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
        modifier =
            modifier.padding(
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
    val effectiveAccent =
        if (item.accentColor.isSpecified) {
            item.accentColor
        } else {
            MaterialTheme.colorScheme.primary
        }
    val iconContentCandidate = contentColorFor(effectiveAccent)
    val iconContentColor =
        if (iconContentCandidate.isSpecified) {
            iconContentCandidate
        } else {
            MaterialTheme.colorScheme.surface
        }
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
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    position = position,
                )
                .clip(shape)
                .focusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = item.onClick,
                )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsDimensions.SegmentedItemMinHeight)
                    .padding(
                        horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                        vertical = SettingsDimensions.SegmentedItemPaddingVertical,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconShape = rememberPreferenceIconShape(item.title)
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.SegmentedIconBoxSize)
                        .clip(iconShape)
                        .background(effectiveAccent),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showUpdateIndicator) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(SettingsDimensions.SegmentedBadgeSize),
                            )
                        },
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = iconContentColor,
                            modifier = Modifier.size(SettingsDimensions.SegmentedIconSize),
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(SettingsDimensions.SegmentedIconSize),
                    )
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.SegmentedIconSpacing))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                MarqueeText(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    modifier = Modifier,
                )
                item.subtitle?.let { subtitle ->
                    Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedRowSpacing))
                    MarqueeText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        modifier = Modifier,
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
                        modifier =
                            Modifier.padding(
                                horizontal = SettingsDimensions.SegmentedBadgePaddingH,
                                vertical = SettingsDimensions.SegmentedBadgePaddingV,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.width(SettingsDimensions.RowChevronSpacing))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SettingsDimensions.RowChevronAlpha)
            )
        }
    }
}

private fun segmentedSettingsItemShape(
    index: Int,
    count: Int,
): Shape {
    val large = SettingsDimensions.SegmentedCornerLarge
    val small = SettingsDimensions.SegmentedCornerSmall
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}


@Composable
fun SettingsScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        content()
    }
}

@Composable
fun YumaSettingsScaffold(
    title: @Composable () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsScreenBackground(modifier = modifier) {
        GlassScaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = {
                        val backIcon = @Composable {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }

                        if (onBackLongClick != null) {
                            IconButton(
                                onClick = onBackClick,
                                onLongClick = onBackLongClick,
                                content = backIcon,
                            )
                        } else {
                            androidx.compose.material3.IconButton(
                                onClick = onBackClick,
                                content = backIcon,
                            )
                        }
                    },
                    actions = actions,
                    colors = GlassDefaults.topAppBarColors(),
                )
            },
        ) { innerPadding ->
            val baseModifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(top = innerPadding.calculateTopPadding())

            if (scrollable) {
                Column(
                    modifier = baseModifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = SettingsDimensions.ScreenBottomPadding),
                    content = content,
                )
            } else {
                Column(
                    modifier = baseModifier.padding(bottom = SettingsDimensions.ScreenBottomPadding),
                    content = content,
                )
            }
        }
    }
}
