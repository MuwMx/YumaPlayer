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
            text = stringResource(R.string.customization),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        SettingsSwitchRow(
            title = stringResource(R.string.theme),
            subtitle = if (state.isBlurBackgroundEnabled) {
                stringResource(R.string.theme_blur_desc)
            } else {
                stringResource(R.string.theme_gradient_desc)
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
                stringResource(R.string.theme_immersive_desc)
            } else {
                stringResource(R.string.theme_standard_desc)
            },
            checked = state.isImmersiveEnabled,
            onCheckedChange = onImmersiveChanged,
            vibrantColor = Color(state.vibrantColor),
            index = 1,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = stringResource(R.string.codec_info),
            subtitle = stringResource(R.string.codec_info_desc),
            checked = state.showCodecInfo,
            onCheckedChange = { onAction(PlayerAction.ToggleCodecInfo) },
            vibrantColor = Color(state.vibrantColor),
            index = 2,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = stringResource(R.string.ambient_glow),
            subtitle = stringResource(R.string.ambient_glow_desc),
            checked = state.isAlbumCoverGlowEnabled,
            onCheckedChange = { onAction(PlayerAction.ToggleAlbumCoverGlow) },
            vibrantColor = Color(state.vibrantColor),
            index = 3,
            count = count,
        )

        Text(
            text = stringResource(R.string.more_visual_effects_coming_soon),
            color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
    }
}
