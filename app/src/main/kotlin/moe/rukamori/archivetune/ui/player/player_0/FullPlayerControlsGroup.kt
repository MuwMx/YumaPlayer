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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import timber.log.Timber
private val SeekbarToTransportGap = 14.dp
private val CompactSeekbarToTransportGap = 4.dp
@Immutable
internal data class CompactedControlsLayout(
    val rootHeight: Float = 0f,
    val capsuleHeaderHeight: Float = 0f,
    val toolbarBottom: Float = 0f,
    val m0: Float = 0f,
    val b0: Float = 0f,
    val d: Float = 0f,
    val g: Float = 0f,
) {
    val isReady: Boolean
        get() = rootHeight > 0f && capsuleHeaderHeight > 0f && toolbarBottom > 0f && m0 > 0f && b0 > m0
    val bc: Float
        get() = b0

    val gc: Float
        get() = bc - (m0 + d)
    val qPeek: Float
        get() = capsuleHeaderHeight + 0.55f * (rootHeight - capsuleHeaderHeight)
    val deltaPeek: Float
        get() = if (isReady) minOf(0f, qPeek - g - bc) else 0f
    val deficit: Float
        get() = if (isReady) maxOf(
            0f,
            toolbarBottom - (m0 + d + deltaPeek)
        ) else 0f
    val isFeasible: Boolean
        get() = isReady && deficit <= 0f
    val effectiveDeltaPeek: Float
        get() = if (isFeasible) deltaPeek else 0f
}
internal fun computeCompactedControlsLayout(
    rootHeight: Float,
    capsuleHeaderHeight: Float,
    toolbarBottom: Float,
    m0: Float,
    b0: Float,
    measuredGap: Float,
    targetGap: Float,
    gapG: Float,
): CompactedControlsLayout {
    val d = 0f
    return CompactedControlsLayout(
        rootHeight = rootHeight,
        capsuleHeaderHeight = capsuleHeaderHeight,
        toolbarBottom = toolbarBottom,
        m0 = m0,
        b0 = b0,
        d = d,
        g = gapG,
    )
}
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
    rootHeightPx: Float = 0f,
    capsuleHeaderHeightPx: Float = 0f,
    toolbarBottomInRootPx: Float = 0f,
    rootCoordinates: LayoutCoordinates? = null,
) {
    val density = LocalDensity.current
    val targetGapPx = with(density) {
        CompactSeekbarToTransportGap.roundToPx().toFloat()
    }
    val gapGPx = with(density) { SettingsDimensions.PlayerControlsVerticalGap.roundToPx().toFloat() }
    val measuredGapPx = with(density) { SeekbarToTransportGap.roundToPx().toFloat() }
    var m0Px by remember { mutableFloatStateOf(0f) }
    var b0Px by remember { mutableFloatStateOf(0f) }
    val layoutInfo = remember(
        rootHeightPx, capsuleHeaderHeightPx, toolbarBottomInRootPx,
        m0Px, b0Px, measuredGapPx, targetGapPx, gapGPx
    ) {
        computeCompactedControlsLayout(
            rootHeight = rootHeightPx,
            capsuleHeaderHeight = capsuleHeaderHeightPx,
            toolbarBottom = toolbarBottomInRootPx,
            m0 = m0Px,
            b0 = b0Px,
            measuredGap = measuredGapPx,
            targetGap = targetGapPx,
            gapG = gapGPx,
        )
    }
    LaunchedEffect(layoutInfo.isReady, layoutInfo.isFeasible, layoutInfo.deficit) {
        if (layoutInfo.isReady && !layoutInfo.isFeasible) {
            Timber.tag("FullPlayerGeometry").e(
                "Feasibility deficit: H=%.1f, C=%.1f, T=%.1f, M0=%.1f, B0=%.1f, d=%.1f, Qpeek=%.1f, g=%.1f, deficit=%.1f",
                layoutInfo.rootHeight, layoutInfo.capsuleHeaderHeight, layoutInfo.toolbarBottom,
                layoutInfo.m0, layoutInfo.b0, layoutInfo.d, layoutInfo.qPeek, layoutInfo.g, layoutInfo.deficit
            )
        }
    }
    val isMainControlsInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.57f }
    }
    val isLikeInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.4275f }
    }
    val isBottomBarInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.4275f }
    }
    val queueProgress =
        (queueFractionProvider().coerceIn(0f, 1f) / 0.45f)
            .coerceIn(0f, 1f)

    val seekbarTransportGap =
        SeekbarToTransportGap +
                (CompactSeekbarToTransportGap - SeekbarToTransportGap) * queueProgress

    val metadataSeekbarGap =
        8.dp + (4.dp - 8.dp) * queueProgress

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
                .onGloballyPositioned { coords ->
                    val root = rootCoordinates
                    if (root != null && root.isAttached && coords.isAttached) {
                        m0Px = root.localPositionOf(coords, Offset.Zero).y
                        b0Px = root.localPositionOf(coords, Offset(0f, coords.size.height.toFloat())).y
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val q = queueFractionProvider().coerceIn(0f, 1f)
                        val p = (q / 0.45f).coerceIn(0f, 1f)
                        translationY = p * layoutInfo.effectiveDeltaPeek
                        alpha = if (q <= 0.45f) {
                            1f
                        } else {
                            (1f - (q - 0.45f) / 0.13f).coerceIn(0f, 1f)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlayerMetadata(
                        title = state.title,
                        artist = state.artist,
                        state = state,
                        onAction = onAction,
                        onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
                        isVisible = isVisible,
                        enabled = isMainControlsInteractive,
                        likeFractionProvider = queueFractionProvider,
                        likeEnabled = isLikeInteractive,
                    )
                }
                Spacer(modifier = Modifier.height(metadataSeekbarGap))
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
                    isVisible = isVisible,
                    enabled = isMainControlsInteractive,
                    codecFractionProvider = queueFractionProvider,
                )
                Spacer(modifier = Modifier.height(seekbarTransportGap))

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PlayerTransportControls(
                        isPlaying = state.isPlaying,
                        vibrantColor = Color(state.vibrantColor),
                        slideOffset = slideOffset,
                        onAction = onAction,
                        isLarge = true,
                        state = state,
                        enabled = isMainControlsInteractive,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val q = queueFractionProvider().coerceIn(0f, 1f)
                    val p = (q / 0.45f).coerceIn(0f, 1f)
                    alpha = (1f - p).coerceIn(0f, 1f)
                }
        ) {
            PlayerBottomBar(
                state = state,
                onAction = onAction,
                onOpenQueue = onOpenQueue,
                enabled = isBottomBarInteractive,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
