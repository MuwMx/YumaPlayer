package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import moe.rukamori.archivetune.utils.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    progressMs: Long,
    durationMs: Long,
    animatedAccentColor: Color,
    slideOffset: () -> Float,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit
) {
    var sliderPosition by remember { mutableStateOf(0f) }
    val isDragging = remember { mutableStateOf(false) }
    var localSeekTarget by remember { mutableStateOf<Float?>(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 7.dp else 4.dp,
        animationSpec = tween(durationMillis = 250),
        label = "TrackHeightAnimation"
    )

    LaunchedEffect(progressMs) {
        localSeekTarget?.let { target ->
            if (abs(progressMs - target) < 2000) {
                localSeekTarget = null
            }
        }
    }

    val maxRange = maxOf(1f, durationMs.toFloat())
    val baseProgress = when {
        durationMs == 0L -> 0f
        isDragging.value -> sliderPosition
        localSeekTarget != null -> localSeekTarget!!
        else -> progressMs.toFloat()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = baseProgress.coerceIn(0f, maxRange),
        animationSpec = if (isDragging.value) snap() else tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "SliderLineFluidAnimation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // Схлопывает сикбар по ширине, выравнивая его с метадатой и обложкой
    ) {
        Slider(
            value = baseProgress.coerceIn(0f, maxRange),
            onValueChange = {
                isDragging.value = true
                localSeekTarget = null
                sliderPosition = it
                onSeekStarted()
            },
            onValueChangeFinished = {
                localSeekTarget = sliderPosition
                isDragging.value = false
                onSeek(sliderPosition)
            },
            valueRange = 0f..maxRange,
            interactionSource = interactionSource,
            track = { _ ->
                val fraction = (animatedProgress / maxRange).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (isInteracting) animatedAccentColor else Color.White)
                    )
                }
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

        // ТЕКУЩЕЕ ВРЕМЯ: Меняется постоянно, оставляем derivedStateOf для фильтрации частоты кадров
        val currentSecText by remember {
            derivedStateOf {
                TimeUtils.formatMs(animatedProgress.coerceAtLeast(0f).toLong())
            }
        }

        // КОНЕЧНОЕ ВРЕМЯ: Меняется раз в песню. derivedStateOf НЕ НУЖЕН.
        // Просто вешаем обычный remember на ключ durationMs
        val durationSecText = remember(durationMs) {
            TimeUtils.formatMs(durationMs)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp) // Оставили только небольшой зазор сверху, чтобы текст не прилипал к линии
                .graphicsLayer {
                    val offset = slideOffset()
                    alpha = if (offset > 0.5f) ((offset - 0.5f) / 0.5f) else 0f
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentSecText,
                color = Color(0x80FFFFFF),
                fontFamily = GoogleSans,
                fontSize = 12.sp
            )
            Text(
                text = durationSecText,
                color = Color(0x80FFFFFF),
                fontFamily = GoogleSans,
                fontSize = 12.sp
            )
        }
    }
}

