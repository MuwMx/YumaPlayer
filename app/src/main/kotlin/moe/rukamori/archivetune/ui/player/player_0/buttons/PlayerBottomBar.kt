package moe.rukamori.archivetune.ui.player.player_0.buttons

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.state.PlayerUiState

private val ButtonClickAreaSize = 48.dp
private val BottomBarIconSize = 28.dp
private val ButtonSpacing= 120.dp

@Composable
fun PlayerBottomBar(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: ColorScheme = MaterialTheme.colorScheme
) {
    val inactiveButtonColor = Color.White.copy(alpha = 0.75f)
    val activeColor = Color.White

    val isLyricsActive = state.isLyricsVisible
    val lyricsColor = if (isLyricsActive) activeColor else inactiveButtonColor
    val queueColor = inactiveButtonColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiryIconButton(
            iconRes = R.drawable.lyrics,
            tint = lyricsColor,
            size = BottomBarIconSize,
            enabled = enabled
        ) {
            onAction(PlayerAction.Lyrics)
        }
        AiryIconButton(
            iconRes = R.drawable.queue_music,
            tint = queueColor,
            size = BottomBarIconSize,
            enabled = enabled
        ) {
            onOpenQueue()
        }
    }
}

@Composable
private fun AiryIconButton(
    iconRes: Int,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "AiryButtonBounce"
    )

    Column(
        modifier = Modifier
            .size(ButtonClickAreaSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
