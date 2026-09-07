package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import moe.rukamori.archivetune.ui.player.player_0.buttons.SleepTimerTopBadge
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.TimeUtils
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    state: PlayerUiState,
    progressProvider: () -> Long,
    durationMs: Long,
    vibrantColor: Color,
    slideOffset: () -> Float,
    showCodecInfo: Boolean = false,
    codecInfo: String = "",
    sleepTimerRemainingSeconds: Int? = null,
    onOpenSleepTimer: () -> Unit = {},
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    isVisible: Boolean = true,
) {
    val maxRange = maxOf(1f, durationMs.toFloat())
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var seekHoldTargetFraction by remember { mutableStateOf<Float?>(null) }
    var seekHoldStartTime by remember { mutableLongStateOf(0L) }

    val rawProgressProvider = remember(progressProvider) {
        derivedStateOf { progressProvider() }
    }

    val renderedProgress = remember {
        val initialFraction = if (durationMs > 0L) {
            (progressProvider().toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        mutableFloatStateOf(initialFraction)
    }

    LaunchedEffect(state.trackUrl, state.title, state.artist) {
        seekHoldTargetFraction = null
        seekHoldStartTime = 0L
        isDragging = false
        dragPositionMs = 0f
        val currentMs = progressProvider()
        val fraction = if (durationMs > 0L) {
            (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
        renderedProgress.floatValue = fraction
    }

    LaunchedEffect(isVisible, state.isPlaying, state.trackUrl, durationMs) {
        if (!isVisible) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val now = System.currentTimeMillis()
                val currentSeekHold = seekHoldTargetFraction
                if (currentSeekHold != null) {
                    if (now - seekHoldStartTime > 4500L) {
                        seekHoldTargetFraction = null
                    }
                }

                val currentMs = rawProgressProvider.value
                val totalMs = durationMs.toFloat()
                val currentFraction = if (totalMs > 0f) {
                    (currentMs.toFloat() / totalMs).coerceIn(0f, 1f)
                } else 0f

                if (currentSeekHold != null) {
                    if (abs(currentFraction - currentSeekHold) < 0.04f) {
                        seekHoldTargetFraction = null
                    }
                }

                val targetFraction = when {
                    isDragging -> (dragPositionMs / maxRange).coerceIn(0f, 1f)
                    seekHoldTargetFraction != null -> seekHoldTargetFraction!!
                    else -> currentFraction
                }

                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameTimeNanos
                    renderedProgress.floatValue = targetFraction
                    return@withFrameNanos
                }

                val dtSec = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.25f)
                lastFrameNanos = frameTimeNanos

                val current = renderedProgress.floatValue
                val delta = targetFraction - current

                if (abs(delta) > 0.1f || isDragging) {
                    renderedProgress.floatValue = targetFraction
                } else {
                    val factor = (dtSec * 10f).coerceIn(0f, 1f)
                    renderedProgress.floatValue = current + delta * factor
                }
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    val animatedAccentColor by animateColorAsState(
        targetValue = vibrantColor,
        animationSpec = tween(500),
        label = "AccentPaletteColor"
    )

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 7.dp else 4.dp,
        animationSpec = tween(durationMillis = 250),
        label = "TrackHeightAnimation"
    )

    val coarseSeconds by remember(durationMs) {
        derivedStateOf {
            val ms = when {
                isDragging -> dragPositionMs.toLong()
                seekHoldTargetFraction != null -> (seekHoldTargetFraction!! * durationMs).toLong()
                else -> rawProgressProvider.value
            }
            (ms.coerceIn(0L, durationMs) / 1000L)
        }
    }

    val sliderValue by remember(durationMs) {
        derivedStateOf {
            if (isDragging) {
                dragPositionMs.coerceIn(0f, maxRange)
            } else {
                (coarseSeconds * 1000f).coerceIn(0f, maxRange)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                seekHoldTargetFraction = null
                dragPositionMs = it
                onSeekStarted()
            },
            onValueChangeFinished = {
                val targetMs = dragPositionMs
                val targetFraction = (targetMs / maxRange).coerceIn(0f, 1f)
                seekHoldTargetFraction = targetFraction
                seekHoldStartTime = System.currentTimeMillis()
                isDragging = false
                onSeek(targetMs)
            },
            valueRange = 0f..maxRange,
            interactionSource = interactionSource,
            track = { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .drawBehind {
                            val fraction = renderedProgress.floatValue.coerceIn(0f, 1f)
                            val fillWidth = size.width * fraction
                            drawRoundRect(
                                color = if (isInteracting) animatedAccentColor else Color.White,
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(
                                    x = size.height / 2f,
                                    y = size.height / 2f
                                )
                            )
                        }
                )
            },
            thumb = { Box(modifier = Modifier.size(0.dp)) },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val offset = slideOffset()
                    alpha = if (offset > 0.5f) ((offset - 0.5f) / 0.5f) else 0f
                }
        )

        val currentSecText by remember {
            derivedStateOf {
                TimeUtils.formatMs(coarseSeconds * 1000L)
            }
        }

        val durationSecText = remember(durationMs) {
            TimeUtils.formatMs(durationMs)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .graphicsLayer {
                    val offset = slideOffset()
                    alpha = if (offset > 0.5f) ((offset - 0.5f) / 0.5f) else 0f
                }
        ) {
            Text(
                text = currentSecText,
                color = Color(0x80FFFFFF),
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showCodecInfo && codecInfo.isNotEmpty(),
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300))
                ) {
                    Text(
                        text = codecInfo,
                        color = Color(0x80FFFFFF),
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color(0x1AFFFFFF), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isImmersiveEnabled && sleepTimerRemainingSeconds != null,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300))
                ) {
                    SleepTimerTopBadge(
                        state = state,
                        onClick = onOpenSleepTimer
                    )
                }
            }

            Text(
                text = durationSecText,
                color = Color(0x80FFFFFF),
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

