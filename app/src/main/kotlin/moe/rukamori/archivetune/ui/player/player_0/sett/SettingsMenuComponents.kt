package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
fun CompactMenuRow(
    title: String,
    subtitle: String,
    iconResId: Int? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = false,
    isActive: Boolean = false,
    activeIconTint: Color = Color.White,
    index: Int = 0,
    count: Int = 1,
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
    val containerBg = if (isActive) activeIconTint.copy(alpha = 0.15f) else colors.glassBackground

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .yumaCombinedClickable(onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                backgroundColor = containerBg,
                borderColor = if (isActive) activeIconTint.copy(alpha = 0.3f) else colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
                position = position,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                Box(
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    contentAlignment = Alignment.Center
                ) {
                    leadingContent()
                }
            } else if (iconResId != null) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = title,
                    tint = if (isActive) activeIconTint else Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isActive) activeIconTint else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
            }
            if (showArrow) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = SettingsDimensions.YumaRowArrowAlpha),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun MenuRowButton(
    iconRes: Int,
    timerText: String? = null,
    isActive: Boolean = false,
    vibrantColor: Color = Color.Transparent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isActive && vibrantColor.luminance() > 0.5f) Color.Black else Color.White
    val colors = LocalYumaColors.current
    val backgroundColor = if (isActive) vibrantColor else colors.glassBackground
    val borderColor = if (isActive) Color.Transparent else colors.glassBorder

    Box(
        modifier = modifier
            .height(48.dp)
            .yumaCombinedClickable(onClick = onClick)
            .yumaGlassCard(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isActive && timerText != null) {
            Text(
                text = timerText,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        } else {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isActive) contentColor else Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
