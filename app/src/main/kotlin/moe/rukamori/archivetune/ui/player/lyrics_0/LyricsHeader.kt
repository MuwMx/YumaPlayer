package moe.rukamori.archivetune.ui.player.lyrics_0

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.state.PlayerUiState

import moe.rukamori.archivetune.ui.theme.SoftTextShadow
import coil3.compose.AsyncImage

@Composable
fun LyricsHeader(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    onCloseClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    // Пружинные отскоки кнопок
    val closeInteractionSource = remember { MutableInteractionSource() }
    val closePressed by closeInteractionSource.collectIsPressedAsState()
    val closeScale by animateFloatAsState(if (closePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val moreInteractionSource = remember { MutableInteractionSource() }
    val morePressed by moreInteractionSource.collectIsPressedAsState()
    val moreScale by animateFloatAsState(if (morePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    // Изолированная анимация вращения пластинки
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying, isVisible) {
        if (state.isPlaying && isVisible) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
                )
            }
        }
    }

    val capsuleShape = RoundedCornerShape(24.dp)
    val capsuleColor = if (state.isBlurBackgroundEnabled) Color.Black else Color(state.darkMutedColor)

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarTop > 0.dp) statusBarTop + 8.dp else 44.dp

    Box(
        modifier = modifier
            .padding(top = topPadding, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(elevation = 12.dp, shape = capsuleShape, clip = false)
            .graphicsLayer {
                val progress = animateProgressProvider()
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
            // Кнопка сворачивания
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

            // Центральная часть: Пластинка + Бегущий текст
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val albumArtModifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = rotation.value }
                    .clip(CircleShape)

                if (state.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = state.coverUrl,
                        contentDescription = null,
                        modifier = albumArtModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = state.placeholderResId),
                        contentDescription = null,
                        modifier = albumArtModifier,
                        contentScale = ContentScale.Crop
                    )
                }

                // ВНЕДРЕНИЕ ИМБЫ: Текст теперь полностью адаптивный
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Название трека
                    MarqueeText(
                        text = state.title,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = SoftTextShadow
                        ),
                        maxLines = 1,
                        modifier = Modifier,
                        isVisible = isVisible
                    )

                    // Исполнитель
                    MarqueeText(
                        text = state.artist,
                        style = TextStyle(
                            color = Color(0xD9FFFFFF),
                            fontSize = 12.sp,
                            shadow = SoftTextShadow
                        ),
                        maxLines = 1,
                        modifier = Modifier,
                        isVisible = isVisible
                    )
                }
            }

            // Кнопка меню
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
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), capsuleShape)
            )
        }
    }
}
