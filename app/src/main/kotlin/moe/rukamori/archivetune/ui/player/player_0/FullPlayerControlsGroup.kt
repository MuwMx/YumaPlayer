package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
internal fun FullPlayerControlsGroup(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    slideOffset: () -> Float,
    controlsOffsetY: () -> Dp,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onOpenQueue: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
            .widthIn(max = 420.dp)
            .offset { IntOffset(x = 0, y = controlsOffsetY().roundToPx()) }
    ) {
        PlayerMetadata(
            title = state.title,
            artist = state.artist,
            state = state,
            onAction = onAction,
            onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
            isVisible = isVisible
        )

        Spacer(modifier = Modifier.height(8.dp))

        PlayerSeekBar(
            state = state,
            playbackProgress = playbackProgress,
            durationMs = state.durationMs,
            vibrantColor = Color(state.vibrantColor),
            slideOffset = slideOffset,
            showCodecInfo = state.showCodecInfo,
            codecInfo = state.codecInfo,
            sleepTimerRemainingSeconds = state.sleepTimerRemainingSeconds,
            onOpenSleepTimer = { onOpenSettingsMenu(PlayerMenuScreen.SLEEP_TIMER) },
            onSeek = onSeek,
            onSeekStarted = onSeekStarted,
            isVisible = isVisible
        )

        Spacer(modifier = Modifier.height(14.dp))

        PlayerTransportControls(
            isPlaying = state.isPlaying,
            vibrantColor = Color(state.vibrantColor),
            slideOffset = slideOffset,
            onAction = onAction,
            isLarge = true,
            state = state,
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlayerBottomBar(
            state = state,
            onAction = onAction,
            onOpenQueue = onOpenQueue
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
