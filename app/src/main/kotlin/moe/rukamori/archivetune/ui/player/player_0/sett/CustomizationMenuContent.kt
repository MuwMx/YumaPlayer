package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
fun CustomizationMenuContent(
    state: PlayerUiState,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onAction: (PlayerAction) -> Unit
) {
    val count = 4

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Customization",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
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
            vibrantColor = Color(state.vibrantColor),
            index = 0,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = stringResource(id = R.string.immersive_mode),
            subtitle = if (state.isImmersiveEnabled) {
                "Current: Immersive\nFull screen cover background."
            } else {
                "Current: Standard\nCompact player card."
            },
            checked = state.isImmersiveEnabled,
            onCheckedChange = onImmersiveChanged,
            vibrantColor = Color(state.vibrantColor),
            index = 1,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = "Codec Info",
            subtitle = "Show audio format and sample rate above seekbar",
            checked = state.showCodecInfo,
            onCheckedChange = { onAction(PlayerAction.ToggleCodecInfo) },
            vibrantColor = Color(state.vibrantColor),
            index = 2,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = "Ambient Glow",
            subtitle = "Soft neon light around the player card",
            checked = state.isAlbumCoverGlowEnabled,
            onCheckedChange = { onAction(PlayerAction.ToggleAlbumCoverGlow) },
            vibrantColor = Color(state.vibrantColor),
            index = 3,
            count = count,
        )

        Text(
            text = "More visual effects coming soon...",
            color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
    }
}
