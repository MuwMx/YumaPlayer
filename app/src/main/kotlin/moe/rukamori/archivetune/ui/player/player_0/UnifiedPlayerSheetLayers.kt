package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsColumn
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsHeader
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.ActiveDragSheet
import moe.rukamori.archivetune.ui.player.player_0.scoped.FullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.player.queue_0.QueueScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow
import moe.rukamori.archivetune.ui.theme.glassBorder
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun UnifiedPlayerSheetLayers(
    state: PlayerUiState,
    queueState: QueueUiState,
    updateState: UpdateState,
    expansionFractionProvider: () -> Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    progressMsProvider: () -> Long,
    fullPlayerVisualState: FullPlayerVisualState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onCloseQueueClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onMoreLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    dragHandler: SheetVerticalDragGestureHandler? = null
) {
    val density = LocalDensity.current.density

    val lyricsListState = rememberLazyListState()
    val canDragLyrics by remember(lyricsListState) {
        derivedStateOf {
            lyricsListState.firstVisibleItemIndex == 0 && lyricsListState.firstVisibleItemScrollOffset == 0
        }
    }
    val lyricsNestedScrollConnection = remember(dragHandler, canDragLyrics) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragLyrics },
            targetSheet = ActiveDragSheet.LYRICS
        )
    }

    var isQueueReordering by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val canDragQueue by remember(queueListState, isQueueReordering) {
        derivedStateOf {
            !isQueueReordering && queueListState.firstVisibleItemIndex == 0 && queueListState.firstVisibleItemScrollOffset == 0
        }
    }
    val queueNestedScrollConnection = remember(dragHandler, canDragQueue) {
        dragHandler?.createNestedScrollConnection(
            canDragProvider = { canDragQueue },
            targetSheet = ActiveDragSheet.QUEUE
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = expansionFractionProvider()
                }
        ) {
            moe.rukamori.archivetune.ui.player.player_0.PlayerBackgroundLayers(
                state = state,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                onColorsExtracted = { vibrant, darkMuted, gradient ->
                    onAction(PlayerAction.UpdateColors(vibrant, darkMuted, gradient))
                },
            )
        }

        val isMiniPlayerVisible by remember {
            derivedStateOf { expansionFractionProvider() < 0.05f }
        }

        if (expansionFractionProvider() < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = expansionFractionProvider()
                        alpha = (1f - (fraction / 0.3f)).coerceIn(0f, 1f)
                    }
            ) {
                MiniPlayerContentInternal(
                    state = state,
                    expansionFractionProvider = expansionFractionProvider,
                    onAction = onAction,
                    onMediaAreaClick = onExpandClick,
                    isVisible = isMiniPlayerVisible
                )
            }
        }

        val hasTrack by remember(state.title) {
            derivedStateOf { state.title.isNotEmpty() }
        }

        if (hasTrack) {
            val isFullPlayerVisible by remember {
                derivedStateOf {
                    expansionFractionProvider() > 0.005f && maxOf(lyricsFractionProvider(), queueFractionProvider()) < 1f
                }
            }
            val isLyricsVisible by remember {
                derivedStateOf { lyricsFractionProvider() > 0.05f }
            }
            val isQueueVisible by remember {
                derivedStateOf { queueFractionProvider() > 0.05f }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val maxFraction = maxOf(lyricsFractionProvider(), queueFractionProvider())
                        val expansionFraction = expansionFractionProvider()
                        val baseAlpha = if (expansionFraction < 0.005f) 0f else fullPlayerVisualState.contentAlpha
                        alpha = baseAlpha * (1f - maxFraction)
                        translationY = fullPlayerVisualState.translationY - (200f * density * maxFraction)
                    }
            ) {
                FullPlayer(
                    state = state,
                    progressMsProvider = progressMsProvider,
                    updateState = updateState,
                    slideOffset = expansionFractionProvider,
                    density = density,
                    onCollapseClick = onCollapseClick,
                    onAction = onAction,
                    onSeek = onSeek,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onImmersiveChanged = onImmersiveChanged,
                    onOpenSettingsMenu = onOpenSettingsMenu,
                    onOpenQueue = onOpenQueue,
                    onSeekStarted = onSeekStarted,
                    lyricsFractionProvider = lyricsFractionProvider,
                    queueFractionProvider = queueFractionProvider,
                    isVisible = isFullPlayerVisible
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = lyricsFractionProvider()
                        alpha = fraction
                        translationY = if (fraction <= 0f) size.height else (1f - fraction) * (200f * density)
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LyricsHeader(
                        state = state,
                        animateProgressProvider = lyricsFractionProvider,
                        onCloseClick = onCloseLyricsClick,
                        onMoreClick = onMoreLyricsClick,
                        isVisible = isLyricsVisible
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (lyricsNestedScrollConnection != null) {
                                    Modifier.nestedScroll(lyricsNestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .sheetBackground(state)
                    ) {
                        LyricsColumn(
                            state = state,
                            animateProgressProvider = lyricsFractionProvider,
                            progressMsProvider = progressMsProvider,
                            onCloseClick = onCloseLyricsClick,
                            onMoreClick = onMoreLyricsClick,
                            onSearchClick = onSearchLyricsClick,
                            lazyListState = lyricsListState,
                            onAction = onAction,
                            onLineClick = { timeMs -> onSeek(timeMs.toFloat()) },
                            onSeek = onSeek,
                            onSeekStarted = onSeekStarted
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = queueFractionProvider()
                        alpha = fraction
                        translationY = if (fraction <= 0f) size.height else (1f - fraction) * (200f * density)
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    QueueSheetHeader(
                        queueState = queueState,
                        queueFractionProvider = queueFractionProvider,
                        onCloseClick = onCloseQueueClick,
                        onMoreClick = onMoreLyricsClick,
                        state = state,
                        isVisible = isQueueVisible
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (queueNestedScrollConnection != null && !isQueueReordering) {
                                    Modifier.nestedScroll(queueNestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .sheetBackground(state)
                    ) {
                        QueueScreen(
                            state = queueState,
                            onAction = onAction,
                            lazyListState = queueListState,
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                            ),
                            queueFractionProvider = queueFractionProvider,
                            onReorderStateChange = { isQueueReordering = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSheetHeader(
    queueState: QueueUiState,
    queueFractionProvider: () -> Float,
    onCloseClick: () -> Unit,
    onMoreClick: () -> Unit,
    state: PlayerUiState,
    isVisible: Boolean
) {
    val closeInteractionSource = remember { MutableInteractionSource() }
    val closePressed by closeInteractionSource.collectIsPressedAsState()
    val closeScale by androidx.compose.animation.core.animateFloatAsState(if (closePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val moreInteractionSource = remember { MutableInteractionSource() }
    val morePressed by moreInteractionSource.collectIsPressedAsState()
    val moreScale by androidx.compose.animation.core.animateFloatAsState(if (morePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val songCount = queueState.songCount.takeIf { it != 0 } ?: queueState.queueWindows.size
    val queueDurationMs = queueState.queueDurationMs.takeIf { it != 0L } ?: queueState.queueWindows.sumOf { (it.mediaItem.metadata?.duration ?: 0).toLong() } * 1000L
    val queueTitle = queueState.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.queue)
    val subtitle = pluralStringResource(R.plurals.n_song, songCount, songCount) + "  •  " + makeTimeString(queueDurationMs)

    val capsuleShape = RoundedCornerShape(24.dp)
    val capsuleColor = if (state.isBlurBackgroundEnabled) Color.Black else Color(state.darkMutedColor)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarTop > 0.dp) statusBarTop + 8.dp else 44.dp

    Box(
        modifier = Modifier
            .padding(top = topPadding, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 12.dp, shape = capsuleShape, clip = false)
            .graphicsLayer {
                val progress = queueFractionProvider()
                alpha = progress
                scaleX = 0.8f + (0.2f * progress)
                scaleY = 0.8f + (0.2f * progress)
            }
            .background(capsuleColor, capsuleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(capsuleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = closeInteractionSource, indication = null) { onCloseClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_collapse), contentDescription = "Collapse", modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = queueTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(shadow = SoftTextShadow)
                )
                Text(
                    text = subtitle,
                    color = Color(0xD9FFFFFF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(shadow = SoftTextShadow)
                )
            }
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = moreInteractionSource, indication = null) { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_more), contentDescription = "More", modifier = Modifier.size(20.dp))
            }
        }
        if (state.isBlurBackgroundEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)), capsuleShape)
            )
        }
    }
}

@Composable
private fun Modifier.sheetBackground(state: PlayerUiState): Modifier {
    val animatedDarkMuted by animateColorAsState(
        targetValue = Color(state.darkMutedColor),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "SheetDarkMutedAnimation",
    )

    val cardBackgroundBrush = remember(animatedDarkMuted) {
        val startColor = lerp(animatedDarkMuted, Color.Black, 0.7f)
        val midColor = animatedDarkMuted
        val endColor = Color(0xFF121212)

        Brush.verticalGradient(
            0.0f to startColor,
            0.2f to midColor,
            1.0f to endColor,
        )
    }

    val cardShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    return this
        .glassBorder(
            shape = cardShape,
            strokeWidth = SettingsDimensions.GlassBorderThickness,
            topAlpha = 0.20f,
            bottomAlpha = 0.04f,
        )
        .clip(cardShape)
        .background(
            if (state.isBlurBackgroundEnabled || state.isImmersiveEnabled) {
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.2f)))
            } else {
                cardBackgroundBrush
            }
        )
}

