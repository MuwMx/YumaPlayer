package moe.rukamori.archivetune.ui.player.player_0.sett

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
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
            text = "Player Settings",
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
                        contentDescription = "Update",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val formattedVer = if (updateState.versionName.startsWith("v", ignoreCase = true)) updateState.versionName else "v${updateState.versionName}"
                        Text(text = "Update Available ($formattedVer)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = LocalArchiveTuneFontFamily.current)
                        Text(text = "Tap to download from Telegram", color = Color.White.copy(alpha = SettingsDimensions.YumaRowSubtitleAlpha), fontSize = 12.sp, fontFamily = LocalArchiveTuneFontFamily.current)
                    }
                }
            }
        }

        val rowCount = 8

        CompactMenuRow(
            title = "Interface & Visuals",
            subtitle = "Blur, Glow, and background styles",
            iconResId = R.drawable.ic_palette,
            onClick = onNavigateToCustomization,
            showArrow = true,
            index = 0,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Start Radio",
            subtitle = "Radio from current track",
            iconResId = R.drawable.radio,
            onClick = { onAction(PlayerAction.StartRadio) },
            index = 1,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Add to Playlist",
            subtitle = "Add to custom playlist",
            iconResId = R.drawable.playlist_add,
            onClick = onOpenAddToPlaylist,
            showArrow = true,
            index = 2,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Download",
            subtitle = "Save track offline",
            iconResId = R.drawable.download,
            onClick = onNavigateToDownload,
            showArrow = true,
            index = 3,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Track Details",
            subtitle = "Codec, bitrate, file info",
            iconResId = R.drawable.ic_about,
            onClick = onNavigateToDetails,
            showArrow = true,
            index = 4,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Equalizer",
            subtitle = "System audio effects",
            iconResId = R.drawable.equalizer,
            onClick = onOpenEqualizer,
            showArrow = true,
            index = 5,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = "Playback Speed",
            subtitle = "Tempo and pitch settings",
            iconResId = R.drawable.speed,
            onClick = onOpenPlaybackSpeed,
            showArrow = true,
            index = 6,
            count = rowCount,
        )

        Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

        CompactMenuRow(
            title = if (isPinned) "Unpin Track" else "Pin Track",
            subtitle = if (isPinned) "Remove from Speed Dial" else "Pin to Speed Dial",
            iconResId = if (isPinned) R.drawable.bookmark_filled else R.drawable.bookmark,
            isActive = isPinned,
            activeIconTint = Color(state.vibrantColor),
            onClick = {
                val updated = toggleSpeedDialPin(speedDialPins, songPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updated))
            },
            index = 7,
            count = rowCount,
        )
    }
}

@Composable
fun AboutMenuSection(
    state: PlayerUiState,
    updateState: UpdateState,
    onAction: (PlayerAction) -> Unit,
    onDismissRequest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "About & Support",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalArchiveTuneFontFamily.current,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )
    }
}

/**
 * Компактная строка меню настроек плеера.
 * Минимальная высота 44dp (Touch Target).
 * Иконка 20dp, шрифты 14sp/11sp, отступы 10dp.
 */
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
                leadingContent()
            } else if (iconResId != null) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = title,
                    tint = if (isActive) activeIconTint else Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                    modifier = Modifier.size(20.dp)
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
private fun MenuRowButton(
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
            text = "Sleep Timer",
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
                    text = "minutes",
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
                text = if (isTimerActive) "Stop Timer" else "Start Timer",
                color = if (isTimerActive) Color.White else (if (Color(state.vibrantColor).luminance() > 0.5f) Color.Black else Color.White),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        }
    }
}
