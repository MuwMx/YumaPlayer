package moe.rukamori.archivetune.ui.player.lyrics_0.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ShowLyricsPlayerControlsKey
import moe.rukamori.archivetune.ui.player.player_0.buttons.LyricsMenuScreen
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsSwitchRow
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun LyricsMenuMain(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onDismiss: () -> Unit,
    onNavigateTo: (LyricsMenuScreen) -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val count = 7
    val (showPlayerControls, setShowPlayerControls) = rememberPreference(ShowLyricsPlayerControlsKey, true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.lyrics_options),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        SettingsSwitchRow(
            title = stringResource(R.string.lyrics_auto_download),
            subtitle = stringResource(R.string.lyrics_auto_download_desc),
            checked = state.isAutoDownloadEnabled,
            onCheckedChange = { onAction(PlayerAction.ToggleAutoDownload) },
            vibrantColor = Color(state.vibrantColor),
            index = 0,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsSwitchRow(
            title = stringResource(R.string.lyrics_player_controls),
            subtitle = stringResource(R.string.lyrics_player_controls_desc),
            checked = showPlayerControls,
            onCheckedChange = setShowPlayerControls,
            vibrantColor = Color(state.vibrantColor),
            index = 1,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = stringResource(R.string.edit_lyrics),
            subtitle = stringResource(R.string.edit_lyrics_desc),
            iconResId = R.drawable.edit,
            onClick = {
                onAction(PlayerAction.PrepareLyricsEdit)
                onNavigateTo(LyricsMenuScreen.EDIT)
            },
            index = 2,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = stringResource(R.string.translate_lyrics),
            subtitle = stringResource(R.string.translate_lyrics_desc),
            iconResId = R.drawable.translate,
            onClick = {
                onNavigateTo(LyricsMenuScreen.TRANSLATE)
            },
            index = 3,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = stringResource(R.string.sync_offset),
            subtitle = stringResource(R.string.sync_offset_desc),
            iconResId = R.drawable.speed,
            onClick = {
                onNavigateTo(LyricsMenuScreen.SYNC_OFFSET)
            },
            index = 4,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = stringResource(R.string.lyrics_provider),
            subtitle = stringResource(R.string.lyrics_provider_desc),
            iconResId = R.drawable.queue_music,
            onClick = {
                onNavigateTo(LyricsMenuScreen.PROVIDERS)
            },
            index = 5,
            count = count,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        SettingsMenuRow(
            title = stringResource(R.string.search_refresh_lyrics),
            subtitle = stringResource(R.string.search_refresh_lyrics_desc),
            iconResId = R.drawable.ic_search,
            onClick = {
                onAction(PlayerAction.SearchLyrics)
                onDismiss()
            },
            index = 6,
            count = count,
        )
    }
}
