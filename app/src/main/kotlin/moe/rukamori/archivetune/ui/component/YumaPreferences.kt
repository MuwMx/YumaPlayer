/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

/**
 * Стеклянный контейнер категории настроек YumaPlayer.
 */
@Composable
fun YumaPreferenceCategory(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalYumaColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = SettingsDimensions.YumaTitleFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current
                ),
                modifier =
                    Modifier.padding(
                        start = SettingsDimensions.YumaTitlePaddingStart,
                        bottom = SettingsDimensions.YumaTitlePaddingBottom,
                        top = SettingsDimensions.YumaTitlePaddingBottom,
                    )
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.YumaCategorySpacing),
            content = content
        )
    }
}

/**
 * Строка настроек YumaPlayer — плашка с 16dp отступами и упругим сжатием ВСЕЙ КАРТОЧКИ целиком.
 */
/**
 * Строка настроек YumaPlayer — 1 в 1 как SettingsMenuRow из шторки плеера.
 * Мгновенный отклик клика, темная плашка Color(0x0DFFFFFF), без 1dp граней.
 */
@Composable
fun YumaPreferenceRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes iconResId: Int? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && onClick != null) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "SettingsRowScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(SettingsDimensions.GlassCornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SettingsDimensions.GlassStartColor,
                        SettingsDimensions.GlassEndColor,
                    ),
                ),
            )
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (iconResId != null) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = title,
                    tint = Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                    modifier = Modifier
                        .size(SettingsDimensions.YumaRowIconSize)
                        .padding(end = SettingsDimensions.YumaRowIconSpacing)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color.White.copy(alpha = SettingsDimensions.YumaRowDisabledAlpha),
                    fontSize = SettingsDimensions.YumaRowTitleSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(SettingsDimensions.YumaRowTextSpacing))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                        fontSize = SettingsDimensions.YumaRowSubtitleSize,
                        lineHeight = SettingsDimensions.YumaRowSubtitleLineHeight,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showArrow) {
                Spacer(modifier = Modifier.width(SettingsDimensions.YumaRowArrowSpacing))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = "Go",
                    tint = Color.White.copy(alpha = SettingsDimensions.YumaRowArrowAlpha),
                    modifier = Modifier.size(SettingsDimensions.YumaRowArrowSize)
                )
            }
        }
    }
}

/**
 * Строка настроек YumaPlayer со Switch — 1 в 1 как в шторке плеера.
 */
@Composable
fun YumaSwitchPreferenceRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes iconResId: Int? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) SettingsAnimations.PressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "SettingsSwitchRowScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(SettingsDimensions.GlassCornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SettingsDimensions.GlassStartColor,
                        SettingsDimensions.GlassEndColor,
                    ),
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (iconResId != null) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = title,
                    tint = Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                    modifier = Modifier
                        .size(SettingsDimensions.YumaRowIconSize)
                        .padding(end = SettingsDimensions.YumaRowIconSpacing)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color.White.copy(alpha = SettingsDimensions.YumaRowDisabledAlpha),
                    fontSize = SettingsDimensions.YumaRowTitleSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(SettingsDimensions.YumaRowTextSpacing))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                        fontSize = SettingsDimensions.YumaRowSubtitleSize,
                        lineHeight = SettingsDimensions.YumaRowSubtitleLineHeight,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = SettingsDimensions.YumaSwitchTrackAlpha),
                    uncheckedThumbColor = Color.White.copy(alpha = SettingsDimensions.YumaSwitchUncheckedThumbAlpha),
                    uncheckedTrackColor = SettingsDimensions.YumaSwitchUncheckedTrackColor,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
