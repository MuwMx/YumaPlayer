package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction

private val localFont = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

@Composable
fun CustomizationMenuContent(
    state: PlayerUiState,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onAction: (PlayerAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Customization",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = localFont,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        SettingsSwitchRow(
            title = "Theme",
            subtitle = if (state.isBlurBackgroundEnabled) {
                "Current: Blur\nHigh-performance blur effect."
            } else {
                "Current: Gradient\nClean art gradient background."
            },
            checked = state.isBlurBackgroundEnabled,
            onCheckedChange = onBackgroundStyleChanged,
            vibrantColor = Color(state.vibrantColor)
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSwitchRow(
            title = stringResource(id = R.string.immersive_mode),
            subtitle = if (state.isImmersiveEnabled) {
                "Current: Immersive\nFull screen cover background."
            } else {
                "Current: Standard\nCompact player card."
            },
            checked = state.isImmersiveEnabled,
            onCheckedChange = onImmersiveChanged,
            vibrantColor = Color(state.vibrantColor)
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsSwitchRow(
            title = "Ambient Glow",
            subtitle = "Soft neon light around the player card (Coming Soon)",
            checked = false,
            onCheckedChange = {},
            vibrantColor = Color(state.vibrantColor)
        )

        Text(
            text = "More visual effects coming soon...",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
    }
}
