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
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerToolbar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

@Composable
fun FullPlayer(
    state: PlayerUiState,
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
) {
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    val rawColor = Color(state.vibrantColor)

    // ── Colours ───────────────────────────────────────────────────────────────
    val animatedBgColor by animateColorAsState(
        targetValue = Color(state.gradientColor),
        animationSpec = tween(600),
        label = "VibrantGradientColor"
    )

    val animatedAccentColor by animateColorAsState(
        targetValue = Color(state.vibrantColor),
        animationSpec = tween(400),
        label = "AccentPaletteColor"
    )

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(animatedBgColor, Color(0xFF121212))
    )

    // ── Lyrics visibility alpha ───────────────────────────────────────────────
    val lyricsAlphaState by animateFloatAsState(
        targetValue = if (state.isLyricsVisible) 0f else 1f,
        animationSpec = tween(350),
        label = "FullPlayerContentLyricsAlpha"
    )

    // ── Immersive cover animations ────────────────────────────────────────────
    var prevLyricsVisible by remember { mutableStateOf(state.isLyricsVisible) }
    val isExitingLyrics = prevLyricsVisible && !state.isLyricsVisible
    LaunchedEffect(state.isLyricsVisible) { prevLyricsVisible = state.isLyricsVisible }

    val immersiveCoverAlpha by animateFloatAsState(
        targetValue = if (state.isImmersiveEnabled) {
            if (state.isLyricsVisible) 1f else 0f
        } else {
            if (state.isLyricsVisible) 0f else 1f
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
            if (state.isLyricsVisible) 1f else 1.35f
        } else {
            if (state.isLyricsVisible) 0.8f else 1f
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
        targetValue = if (state.isImmersiveEnabled) 32.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "ImmersiveControlsOffset"
    )

    // ── Root ──────────────────────────────────────────────────────────────────
    PlayerBackgroundLayers(state = state, gradientBrush = gradientBrush)
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        PlayerLayout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = lyricsAlphaState
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
                        .padding(horizontal = 16.dp)
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
                        coverDrawable = state.coverDrawable,
                        placeholderResId = state.placeholderResId
                    )
                }
            },
            controls = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 16.dp)
                        .widthIn(max = 420.dp)
                        .offset { IntOffset(x = 0, y = controlsOffsetY.roundToPx()) }
                ) {
                    PlayerMetadata(
                        title = state.title,
                        artist = state.artist,
                        state = state,
                        onAction = onAction
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PlayerSeekBar(
                        progressMs = state.progressMs,
                        durationMs = state.durationMs,
                        animatedAccentColor = animatedAccentColor,
                        slideOffset = slideOffset,
                        onSeek = onSeek,
                        onSeekStarted = onSeekStarted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PlayerTransportControls(
                        isPlaying = state.isPlaying,
                        animatedAccentColor = animatedAccentColor,
                        slideOffset = slideOffset,
                        onAction = onAction,
                        isLarge = true,
                        state = state,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    PlayerBottomBar(
                        state = state,
                        onAction = onAction
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        )
    }
}