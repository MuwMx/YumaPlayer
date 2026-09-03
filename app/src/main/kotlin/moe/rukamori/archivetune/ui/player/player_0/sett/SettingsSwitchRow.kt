package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    vibrantColor: Color,
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .yumaCombinedClickable(onClick = { onCheckedChange(!checked) })
            .yumaGlassCard(
                shape = shape,
                backgroundColor = colors.glassBackground,
                borderColor = colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
                position = position,
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = LocalArchiveTuneFontFamily.current)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF161616),
                    checkedTrackColor = vibrantColor,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}
