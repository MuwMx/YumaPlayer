package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerToolbar
import moe.rukamori.archivetune.ui.player.player_0.buttons.SleepTimerTopBadge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
fun FullPlayer(
    state: PlayerUiState,
    progressMsProvider: () -> Long = { 0L },
    slideOffset: () -> Float,
    density: Float,
    onCollapseClick: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    updateState: UpdateState,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onOpenQueue: () -> Unit = {},
    lyricsFractionProvider: () -> Float = { if (state.isLyricsVisible) 1f else 0f },
    queueFractionProvider: () -> Float = { 0f },
    isVisible: Boolean = true,
) {
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    // ── Immersive cover animations ────────────────────────────────────────────
    var prevLyricsVisible by remember { mutableStateOf(state.isLyricsVisible) }
    val isExitingLyrics = prevLyricsVisible && !state.isLyricsVisible
    LaunchedEffect(state.isLyricsVisible) { prevLyricsVisible = state.isLyricsVisible }

    val isOverlayVisible = state.isLyricsVisible || lyricsFractionProvider() > 0.5f || queueFractionProvider() > 0.5f

    val immersiveCoverAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled) {
            if (isOverlayVisible) 1f else 0f
        } else {
            if (isOverlayVisible) 0f else 1f
        },
        animationSpec = if (isExitingLyrics && state.isImmersiveEnabled) {
            snap()
        } else {
            tween(durationMillis = 450, easing = FastOutSlowInEasing)
        },
        label = "ImmersiveCoverAlpha"
    )
    val immersiveCoverScale by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled) {
            if (isOverlayVisible) 1f else 1.35f
        } else {
            if (isOverlayVisible) 0.8f else 1f
        },
        animationSpec = if (isExitingLyrics && state.isImmersiveEnabled) {
            snap()
        } else {
            tween(durationMillis = 450, easing = FastOutSlowInEasing)
        },
        label = "ImmersiveCoverScale"
    )

    // ── Immersive offset for controls ─────────────────────────────────────────
    val controlsOffsetY by animateDpAsState(
        targetValue = if (state.isImmersiveEnabled) 16.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "ImmersiveControlsOffset"
    )

    // ── Root ──────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        PlayerLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val overlayFraction = maxOf(lyricsFractionProvider(), queueFractionProvider())
                    alpha = (1f - overlayFraction).coerceIn(0f, 1f)
                },
            toolbar = {
                PlayerToolbar(
                    state = state,
                    onCollapseClick = onCollapseClick,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
                    onTimerBadgeClick = { onOpenSettingsMenu(PlayerMenuScreen.SLEEP_TIMER) },
                    hasUpdate = updateState is UpdateState.SoftUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                )
            },
            cover = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp)
                        .graphicsLayer {
                            alpha = immersiveCoverAlpha
                            scaleX = immersiveCoverScale
                            scaleY = immersiveCoverScale
                        }
                ) {
                    PlayerCoverCard(
                        coverUrl = state.coverUrl,
                        placeholderResId = state.placeholderResId,
                        isAlbumCoverGlowEnabled = state.isAlbumCoverGlowEnabled,
                        vibrantColor = Color(state.vibrantColor),
                        gestureEnabled = !state.isImmersiveEnabled && !state.isLyricsVisible && lyricsFractionProvider() < 0.05f && queueFractionProvider() < 0.05f,
                        onNext = { onAction(PlayerAction.Next) },
                        onPrevious = { onAction(PlayerAction.Previous) }
                    )
                }
            },
            controls = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                        .widthIn(max = 420.dp)
                        .offset { IntOffset(x = 0, y = controlsOffsetY.roundToPx()) }
                ) {
                    PlayerMetadata(
                        title = state.title,
                        artist = state.artist,
                        state = state,
                        onAction = onAction,
                        isVisible = isVisible
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PlayerSeekBar(
                        state = state,
                        progressProvider = progressMsProvider,
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
        )
    }
}