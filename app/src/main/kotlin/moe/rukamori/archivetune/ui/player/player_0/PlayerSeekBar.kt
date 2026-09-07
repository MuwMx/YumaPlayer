package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
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
    var progressMs by remember { mutableLongStateOf(progressProvider()) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var localSeekTarget by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.trackUrl) {
        progressMs = 0L
        sliderPosition = 0f
        localSeekTarget = null
        isDragging = false
    }

    LaunchedEffect(isVisible, state.trackUrl) {
        if (!isVisible) {
            return@LaunchedEffect
        }
        progressMs = progressProvider()
        while (isActive) {
            val current = progressProvider()
            if (progressMs != current) {
                progressMs = current
            }
            delay(250)
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

    LaunchedEffect(progressMs) {
        localSeekTarget?.let { target ->
            if (abs(progressMs - target) < 1500f) {
                localSeekTarget = null
            }
        }
    }

    val maxRange = maxOf(1f, durationMs.toFloat())
    val baseProgress = when {
        durationMs == 0L -> 0f
        isDragging -> sliderPosition
        localSeekTarget != null -> localSeekTarget!!
        else -> progressMs.toFloat()
    }

    val shouldSnap = isDragging || baseProgress <= 500f
    val animatedProgress by animateFloatAsState(
        targetValue = baseProgress.coerceIn(0f, maxRange),
        animationSpec = if (shouldSnap) snap() else tween(durationMillis = 250, easing = LinearEasing),
        label = "SliderLineFluidAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        val progressFractionProvider = {
            (animatedProgress / maxRange).coerceIn(0f, 1f)
        }

        Slider(
            value = baseProgress.coerceIn(0f, maxRange),
            onValueChange = {
                isDragging = true
                localSeekTarget = null
                sliderPosition = it
                onSeekStarted()
            },
            onValueChangeFinished = {
                localSeekTarget = sliderPosition
                isDragging = false
                onSeek(sliderPosition)
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
                            val fraction = progressFractionProvider()
                            val fillWidth = size.width * fraction
                            drawRoundRect(
                                color = if (isInteracting) animatedAccentColor else Color.White,
                                size = Size(fillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
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
                val displayMs = if (isDragging) {
                    sliderPosition.toLong()
                } else {
                    progressMs
                }
                TimeUtils.formatMs(displayMs.coerceAtLeast(0L))
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
                AnimatedVisibility(
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

                AnimatedVisibility(
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