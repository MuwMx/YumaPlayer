package moe.rukamori.archivetune.ui.player.player_0

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.TextStyle
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.player.player_0.buttons.MiniPlayerButtons
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scroll.AutoScrollingTextOnDemand
import moe.rukamori.archivetune.ui.theme.SoftTextShadow
import coil3.compose.AsyncImage

val MiniPlayerHeight = 64.dp
@Composable
internal fun MiniPlayerContentInternal(
    state: PlayerUiState,
    expansionFractionProvider: () -> Float,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    onMediaAreaClick: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = Color(state.gradientColor),
        animationSpec = tween(600),
        label = "MiniPlayerDynamicBackground"
    )

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .background(animatedBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onMediaAreaClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ==========================================
        // 1. ЛЕВАЯ ЧАСТЬ: Обложка
        // ==========================================
        val albumArtModifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                scaleX = lerp(1.07f, 1f, fraction)
                scaleY = lerp(1.07f, 1f, fraction)
                rotationZ = rotation.value
            }
            .clip(CircleShape)

        Crossfade(
    targetState = state.coverUrl,
    animationSpec = tween(500),
    label = "MiniPlayerCoverCrossfade"
) { url ->
    if (url.isNotEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = "Mini Album Art",
            modifier = albumArtModifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = state.placeholderResId),
            contentDescription = "Mini Placeholder",
            modifier = albumArtModifier,
            contentScale = ContentScale.Crop
        )
    }
}

        // ==========================================
        // 2. ЦЕНТРАЛЬНАЯ ЧАСТЬ: Бегущий текст по канонам SRP
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Название трека
            AutoScrollingTextOnDemand(
                text = state.title,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = SoftTextShadow
                ),
                gradientEdgeColor = animatedBgColor, // Края растворяются ровно в текущий цвет мини-плеера
                canScroll = expansionFractionProvider() < 0.01f // Бежит только когда большая шторка закрыта
            )

            // Исполнитель
            AutoScrollingTextOnDemand(
                text = state.artist,
                style = TextStyle(
                    color = Color(0xE6FFFFFF),
                    fontSize = 12.sp,
                    shadow = SoftTextShadow
                ),
                gradientEdgeColor = animatedBgColor,
                canScroll = expansionFractionProvider() < 0.01f
            )
        }

        // ==========================================
        // 3. ПРАВАЯ ЧАСТЬ: Кнопки управления
        // ==========================================
        MiniPlayerButtons(
            state = state,
            onAction = onAction
        )
    }
}
