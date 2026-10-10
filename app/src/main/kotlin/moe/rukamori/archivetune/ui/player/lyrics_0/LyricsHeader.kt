package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.player.player_0.CapsuleDefaults
import moe.rukamori.archivetune.ui.player.player_0.OverlayCapsuleShell
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

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

    OverlayCapsuleShell(
        isBlurBackgroundEnabled = state.isBlurBackgroundEnabled,
        darkMutedColor = state.darkMutedColor,
        modifier = modifier,
        progressProvider = animateProgressProvider,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(CapsuleDefaults.Shape)
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
                Image(painter = painterResource(id = R.drawable.ic_collapse), contentDescription = stringResource(R.string.collapse), modifier = Modifier.size(20.dp))
            }

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

                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
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

            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = moreInteractionSource, indication = null) { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_more), contentDescription = stringResource(R.string.more_label), modifier = Modifier.size(20.dp))
            }
        }
    }
}
