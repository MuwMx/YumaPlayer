package moe.rukamori.archivetune.ui.player.player_0

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.TextStyle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.player.player_0.buttons.MiniPlayerButtons
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction

import moe.rukamori.archivetune.ui.theme.SoftTextShadow
import coil3.compose.AsyncImage
import coil3.request.crossfade

val MiniPlayerHeight = 64.dp
@Composable
internal fun MiniPlayerContentInternal(
    state: PlayerUiState,
    expansionFractionProvider: () -> Float,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    onMediaAreaClick: () -> Unit,
    isVisible: Boolean = true
) {
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
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

        val context = androidx.compose.ui.platform.LocalContext.current

        Box(modifier = albumArtModifier) {
            androidx.compose.animation.Crossfade(
                targetState = state.coverUrl.takeIf { it.isNotEmpty() },
                animationSpec = tween(500),
                label = "MiniCoverCrossfade"
            ) { targetUrl ->
                if (targetUrl == null) {
                    Image(
                        painter = painterResource(id = state.placeholderResId),
                        contentDescription = stringResource(R.string.mini_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val request = remember(targetUrl) {
                        coil3.request.ImageRequest.Builder(context)
                            .data(targetUrl)
                            .crossfade(500)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = stringResource(R.string.mini_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = state.placeholderResId)
                    )
                }
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
                    color = Color(0xE6FFFFFF),
                    fontSize = 12.sp,
                    shadow = SoftTextShadow
                ),
                maxLines = 1,
                modifier = Modifier,
                isVisible = isVisible
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
