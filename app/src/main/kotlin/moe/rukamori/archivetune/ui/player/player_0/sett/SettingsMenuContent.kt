package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.ui.menu.rememberCastPlayerMenuAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
fun SettingsMenuContent(
    state: PlayerUiState,
    updateState: UpdateState,
    onNavigateToAbout: () -> Unit,
    onNavigateToCustomization: () -> Unit,
    onNavigateToSleepTimer: () -> Unit,
    onNavigateToDetails: () -> Unit,
    onNavigateToDownload: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onAction: (PlayerAction) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songId = state.trackUrl
    val songPin = remember(songId) { SpeedDialPin(type = SpeedDialPinType.SONG, id = songId) }
    val isPinned = remember(speedDialPins, songPin) {
        speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.player_settings),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuRowButton(
                iconRes = R.drawable.ic_share,
                onClick = { onAction(PlayerAction.Share) },
                modifier = Modifier.weight(1f)
            )

            val sleepTimerText by remember(state.sleepTimerRemainingSeconds) {
                derivedStateOf {
                    val totalSecs = state.sleepTimerRemainingSeconds
                    if (totalSecs != null && totalSecs > 0) {
                        val m = totalSecs / 60
                        val s = totalSecs % 60
                        "%02d:%02d".format(m, s)
                    } else null
                }
            }

            MenuRowButton(
                iconRes = R.drawable.ic_sleep_timer,
                timerText = sleepTimerText,
                isActive = sleepTimerText != null,
                vibrantColor = Color(state.vibrantColor),
                onClick = onNavigateToSleepTimer,
                modifier = Modifier.weight(1f)
            )
        }

        if (updateState is UpdateState.SoftUpdate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .yumaCombinedClickable {
                        uriHandler.openUri(updateState.updateUrl)
                    }
                    .yumaGlassCard(
                        shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge),
                        backgroundColor = Color(0xFFB33A3A).copy(alpha = 0.8f),
                        borderColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = SettingsDimensions.GlassBorderThickness
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.download),
                        contentDescription = stringResource(R.string.update_button),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val formattedVer = if (updateState.versionName.startsWith("v", ignoreCase = true)) updateState.versionName else "v${updateState.versionName}"
                        Text(text = stringResource(R.string.update_available_format, formattedVer), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = LocalArchiveTuneFontFamily.current)
                        Text(text = stringResource(R.string.update_download_telegram), color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha), fontSize = 12.sp, fontFamily = LocalArchiveTuneFontFamily.current)
                    }
                }
            }
        }

        val castAction = rememberCastPlayerMenuAction()
        val hasCast = castAction != null

        val totalRowCount = 8 + (if (hasCast) 1 else 0)
        var currentRow = 0

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.interface_and_visuals),
            subtitle = stringResource(R.string.interface_and_visuals_desc),
            iconResId = R.drawable.ic_palette,
            onClick = onNavigateToCustomization,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.start_radio_title),
            subtitle = stringResource(R.string.start_radio_desc),
            iconResId = R.drawable.radio,
            onClick = { onAction(PlayerAction.StartRadio) },
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.add_to_playlist_title),
            subtitle = stringResource(R.string.add_to_playlist_desc),
            iconResId = R.drawable.playlist_add,
            onClick = onOpenAddToPlaylist,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        if (castAction != null) {
            CompactMenuRow(
                title = castAction.text,
                subtitle = stringResource(R.string.stream_to_chromecast),
                leadingContent = castAction.icon,
                onClick = castAction.onClick,
                showArrow = true,
                index = currentRow++,
                count = totalRowCount,
            )

            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))
        }

        CompactMenuRow(
            title = stringResource(R.string.download),
            subtitle = stringResource(R.string.save_track_offline),
            iconResId = R.drawable.download,
            onClick = onNavigateToDownload,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.track_details),
            subtitle = stringResource(R.string.track_details_desc),
            iconResId = R.drawable.ic_about,
            onClick = onNavigateToDetails,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.equalizer),
            subtitle = stringResource(R.string.system_audio_effects),
            iconResId = R.drawable.equalizer,
            onClick = onOpenEqualizer,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = stringResource(R.string.playback_speed_label),
            subtitle = stringResource(R.string.playback_speed_desc),
            iconResId = R.drawable.speed,
            onClick = onOpenPlaybackSpeed,
            showArrow = true,
            index = currentRow++,
            count = totalRowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = if (isPinned) stringResource(R.string.unpin_track) else stringResource(R.string.pin_track),
            subtitle = if (isPinned) stringResource(R.string.remove_from_speed_dial_title) else stringResource(R.string.pin_to_speed_dial_title),
            iconResId = if (isPinned) R.drawable.bookmark_filled else R.drawable.bookmark,
            isActive = isPinned,
            activeIconTint = Color(state.vibrantColor),
            onClick = {
                val updated = toggleSpeedDialPin(speedDialPins, songPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updated))
            },
            index = currentRow++,
            count = totalRowCount,
        )
    }
}
