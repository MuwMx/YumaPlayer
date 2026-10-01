package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.player.player_0.buttons.MiniPlayerButtons
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiniPlayerContentInternal(
    state: PlayerUiState,
    expansionFractionProvider: () -> Float,
    progressMsProvider: () -> Long,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    onMediaAreaClick: () -> Unit,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(state.isPlaying, isVisible) {
        if (state.isPlaying && isVisible) {
            while (true) {
                val currentDegree = rotation.value % 360f
                rotation.snapTo(currentDegree)
                rotation.animateTo(
                    targetValue = currentDegree + 360f,
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
                indication = null,
                onClick = onMediaAreaClick
            )
            .padding(start = 28.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator(
                progress = {
                    val duration = state.durationMs
                    if (duration > 0L) {
                        (progressMsProvider().toFloat() / duration).coerceIn(0f, 1f)
                    } else 0f
                },
                modifier = Modifier.fillMaxSize(),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .aspectRatio(1f)
                    .graphicsLayer { rotationZ = rotation.value }
                    .clip(CircleShape)
            ) {
                val targetUrl = state.coverUrl.takeIf { it.isNotBlank() }
                Crossfade(
                    targetState = targetUrl,
                    animationSpec = tween(350),
                    label = "MiniCoverCrossfade"
                ) { url ->
                    if (url == null) {
                        Image(
                            painter = painterResource(id = state.placeholderResId),
                            contentDescription = stringResource(R.string.mini_album_art),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val request = remember(url) {
                            ImageRequest.Builder(context)
                                .data(url)
                                .size(96)
                                .allowHardware(true)
                                .memoryCacheKey("mini:$url")
                                .diskCacheKey("mini:$url")
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp),
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
                isVisible = isVisible
            )

            MarqueeText(
                text = state.artist,
                style = TextStyle(
                    color = Color(0xE6FFFFFF),
                    fontSize = 12.sp,
                    shadow = SoftTextShadow
                ),
                maxLines = 1,
                isVisible = isVisible
            )
        }

        MiniPlayerButtons(
            state = state,
            onAction = onAction
        )
    }
}