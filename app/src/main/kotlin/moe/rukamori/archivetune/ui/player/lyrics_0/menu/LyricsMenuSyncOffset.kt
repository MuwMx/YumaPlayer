package moe.rukamori.archivetune.ui.player.lyrics_0.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.lyrics.language.LyricsTranslator
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import kotlin.math.roundToInt

@Composable
internal fun LyricsMenuSyncOffset(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    // Временное локальное смещение, инициализируемое значением из стейта
    var tempOffset by remember(state.lyricsSyncOffset) { mutableStateOf(state.lyricsSyncOffset) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.sync_offset),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = LyricsTranslator.formatLyricsSyncOffset(tempOffset),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Slider(
                value = tempOffset.toFloat(),
                onValueChange = { tempOffset = it.roundToInt() },
                valueRange = -1000f..1000f,
                steps = 79,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(state.vibrantColor),
                    activeTrackColor = Color(state.vibrantColor),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-1s", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = font)
                Text("+1s", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = font)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                tempOffset = 0
            }) {
                Text(stringResource(R.string.action_reset), color = Color.White, fontFamily = font)
            }
            Row {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_cancel), color = Color.White, fontFamily = font)
                }
                TextButton(onClick = {
                    onAction(PlayerAction.SetLyricsSyncOffset(tempOffset))
                    onBack()
                }) {
                    Text(stringResource(R.string.action_apply), color = Color(state.vibrantColor), fontWeight = FontWeight.Bold, fontFamily = font)
                }
            }
        }
    }
}
