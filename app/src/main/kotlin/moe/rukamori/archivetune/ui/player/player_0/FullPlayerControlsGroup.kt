package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    queueFractionProvider: () -> Float,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onOpenQueue: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val isBottomBarInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.18f }
    }
    val isSeekbarInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.74f }
    }
    val isTransportInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.79f }
    }
    val isMetadataInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.70f }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
            .widthIn(max = 420.dp)
            .offset { IntOffset(x = 0, y = controlsOffsetY().roundToPx()) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .queueFade(queueFractionProvider, 0.45f, 0.75f)
        ) {
            PlayerMetadata(
                title = state.title,
                artist = state.artist,
                state = state,
                onAction = {
                    if (isMetadataInteractive) {
                        onAction(it)
                    }
                },
                onMoreClick = {
                    if (isMetadataInteractive) {
                        onOpenSettingsMenu(PlayerMenuScreen.SETTINGS)
                    }
                },
                isVisible = isVisible
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .queueFade(queueFractionProvider, 0.50f, 0.80f)
        ) {
            PlayerSeekBar(
                state = state,
                playbackProgress = playbackProgress,
                durationMs = state.durationMs,
                vibrantColor = Color(state.vibrantColor),
                slideOffset = slideOffset,
                showCodecInfo = state.showCodecInfo,
                codecInfo = state.codecInfo,
                sleepTimerRemainingSeconds = state.sleepTimerRemainingSeconds,
                onOpenSleepTimer = {
                    if (isSeekbarInteractive) {
                        onOpenSettingsMenu(PlayerMenuScreen.SLEEP_TIMER)
                    }
                },
                onSeek = {
                    if (isSeekbarInteractive) {
                        onSeek(it)
                    }
                },
                onSeekStarted = {
                    if (isSeekbarInteractive) {
                        onSeekStarted()
                    }
                },
                isVisible = isVisible
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .queueFade(queueFractionProvider, 0.55f, 0.85f)
        ) {
            PlayerTransportControls(
                isPlaying = state.isPlaying,
                vibrantColor = Color(state.vibrantColor),
                slideOffset = slideOffset,
                onAction = {
                    if (isTransportInteractive) {
                        onAction(it)
                    }
                },
                isLarge = true,
                state = state,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .queueFade(queueFractionProvider, 0.05f, 0.20f)
        ) {
            PlayerBottomBar(
                state = state,
                onAction = {
                    if (isBottomBarInteractive) {
                        onAction(it)
                    }
                },
                onOpenQueue = {
                    if (isBottomBarInteractive) {
                        onOpenQueue()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun Modifier.queueFade(
    fractionProvider: () -> Float,
    start: Float,
    end: Float,
): Modifier = graphicsLayer {
    val fraction = fractionProvider().coerceIn(0f, 1f)
    alpha = 1f - normalizeFraction(fraction, start, end)
}

