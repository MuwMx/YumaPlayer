package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
fun SleepTimerMenuContent(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBackClick: () -> Unit
) {
    val sleepTimerSecs = state.sleepTimerRemainingSeconds
    val isTimerActive = sleepTimerSecs != null

    var selectedMinutes by remember { mutableIntStateOf(15) }
    val displayMinutes = if (sleepTimerSecs != null) {
        (sleepTimerSecs + 59) / 60
    } else {
        selectedMinutes
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sleep_timer_title),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .yumaCombinedClickable {
                        if (isTimerActive) {
                            onAction(PlayerAction.AdjustSleepTimer(-5))
                        } else {
                            if (selectedMinutes > 5) selectedMinutes -= 5
                        }
                    }
                    .yumaGlassCard(
                        shape = RoundedCornerShape(50),
                        backgroundColor = LocalYumaColors.current.glassBackground,
                        borderColor = LocalYumaColors.current.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("-5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = LocalArchiveTuneFontFamily.current)
            }

            Column(
                modifier = Modifier.width(140.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$displayMinutes",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
                Text(
                    text = stringResource(R.string.minutes_unit),
                    color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
                    fontSize = 12.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .yumaCombinedClickable {
                        if (isTimerActive) {
                            onAction(PlayerAction.AdjustSleepTimer(5))
                        } else {
                            if (selectedMinutes < 120) selectedMinutes += 5
                        }
                    }
                    .yumaGlassCard(
                        shape = RoundedCornerShape(50),
                        backgroundColor = LocalYumaColors.current.glassBackground,
                        borderColor = LocalYumaColors.current.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("+5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = LocalArchiveTuneFontFamily.current)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val actionColor = if (isTimerActive) Color(0xFFB33A3A).copy(alpha = 0.8f) else Color(state.vibrantColor)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .yumaCombinedClickable {
                    if (isTimerActive) {
                        onAction(PlayerAction.StopSleepTimer)
                    } else {
                        onAction(PlayerAction.StartSleepTimer(selectedMinutes))
                    }
                    onBackClick()
                }
                .yumaGlassCard(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = actionColor,
                    borderColor = Color.Transparent,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isTimerActive) stringResource(R.string.stop_timer) else stringResource(R.string.start_timer),
                color = if (isTimerActive) Color.White else (if (Color(state.vibrantColor).luminance() > 0.5f) Color.Black else Color.White),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        }
    }
}
