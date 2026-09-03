package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction

import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

@Composable
fun PlayerMetadata(
    title: String,
    artist: String,
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val gradientEdgeColor = Color.Black
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Клик по треку -> Открыть альбом
            var lastTrackClickTime by remember { mutableLongStateOf(0L) }
            val trackInteraction = remember { MutableInteractionSource() }
            val isTrackPressed by trackInteraction.collectIsPressedAsState()
            val trackScale by animateFloatAsState(
                targetValue = if (isTrackPressed) 0.97f else 1f,
                animationSpec = tween(120),
                label = "TrackClickBounce"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = trackScale
                        scaleY = trackScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = trackInteraction,
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastTrackClickTime > 450L) {
                            lastTrackClickTime = now
                            onAction(PlayerAction.OpenAlbum)
                        }
                    }
            ) {
                MarqueeText(
                    text = title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        shadow = SoftTextShadow
                    ),
                    maxLines = 1,
                    modifier = Modifier,
                    isVisible = isVisible
                )
            }

            // Клик по артисту -> Открыть артиста
            var lastArtistClickTime by remember { mutableLongStateOf(0L) }
            val artistInteraction = remember { MutableInteractionSource() }
            val isArtistPressed by artistInteraction.collectIsPressedAsState()
            val artistScale by animateFloatAsState(
                targetValue = if (isArtistPressed) 0.97f else 1f,
                animationSpec = tween(120),
                label = "ArtistClickBounce"
            )

            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .graphicsLayer {
                        scaleX = artistScale
                        scaleY = artistScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = artistInteraction,
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastArtistClickTime > 450L) {
                            lastArtistClickTime = now
                            onAction(PlayerAction.OpenArtist)
                        }
                    }
            ) {
                MarqueeText(
                    text = artist,
                    style = TextStyle(
                        color = Color(0xF2FFFFFF),
                        fontSize = 16.sp,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        shadow = SoftTextShadow
                    ),
                    maxLines = 1,
                    modifier = Modifier,
                    isVisible = isVisible
                )
            }
        }

        // Кнопка лайка
        var lastLikeClickTime by remember { mutableLongStateOf(0L) }

        val heartColor by animateColorAsState(
            targetValue = if (state.isLiked) Color(state.vibrantColor) else Color.White.copy(alpha = 0.6f),
            animationSpec = tween(300),
            label = "MetadataHeartColor"
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.85f else 1f,
            label = "LikeBounce"
        )

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastLikeClickTime > 450L) {
                        lastLikeClickTime = now
                        onAction(PlayerAction.Like)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = if (state.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline),
                contentDescription = null,
                tint = heartColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
