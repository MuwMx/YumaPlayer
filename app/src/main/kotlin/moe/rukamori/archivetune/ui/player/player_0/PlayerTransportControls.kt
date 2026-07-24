package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.transparentIconShadow
import moe.rukamori.archivetune.ui.utils.bounceClick

private val CapsuleHorizontalPad  = 20.dp
private val CapsuleHeight         = 96.dp
private val CapsulePadHorizontal  = 32.dp
private val CapsuleBorderWidth    = 1.dp
private val CapsuleBgAlpha        = 0.10f
private val CapsuleBorderAlpha    = 0.10f

private val SideButtonSize        = 48.dp
private val SideIconSize          = 36.dp
private val SideShadowAlpha       = 0.1f
private val SideShadowRadius      = 15.dp

private val CenterButtonSize      = 74.dp
private val CenterIconSize        = 54.dp
private val CenterShadowAlpha     = 0.3f
private val CenterShadowRadius    = 45.dp
private val CenterIconDark        = Color(0xFF121212)

@Composable
fun PlayerTransportControls(
    state: PlayerUiState,
    isPlaying: Boolean,
    animatedAccentColor: Color,
    slideOffset: () -> Float,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CapsuleHorizontalPad)
            .graphicsLayer {
                val offset = slideOffset()
                translationY = 80f * (1f - offset)
                alpha = if (offset > 0.3f) ((offset - 0.3f) / 0.7f) else 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CapsuleHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = CapsuleBgAlpha))
                .border(BorderStroke(CapsuleBorderWidth, Color.White.copy(alpha = CapsuleBorderAlpha)), CircleShape)
                .padding(horizontal = CapsulePadHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playPauseIcon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

            // Previous
            Box(
                modifier = Modifier
                    .bounceClick(pressedScale = 0.90f) {
                        onAction(PlayerAction.Previous)
                    }
                    .size(SideButtonSize)
                    .transparentIconShadow(alpha = SideShadowAlpha, shadowRadius = SideShadowRadius)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(Icons.Rounded.SkipPrevious),
                    contentDescription = "Previous Track",
                    modifier = Modifier.size(SideIconSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }

            // Play / Pause
            Box(
                modifier = Modifier
                    .bounceClick(pressedScale = 0.92f) { onAction(PlayerAction.PlayPause) }
                    .size(CenterButtonSize)
                    .background(animatedAccentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(CenterIconSize - 12.dp),
                        color = CenterIconDark,
                    )
                } else {
                    Image(
                        painter = rememberVectorPainter(playPauseIcon),
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(CenterIconSize),
                        colorFilter = ColorFilter.tint(CenterIconDark),
                    )
                }
            }

            // Next
            Box(
                modifier = Modifier
                    .bounceClick(pressedScale = 0.90f) {
                        onAction(PlayerAction.Next)
                    }
                    .size(SideButtonSize)
                    .transparentIconShadow(alpha = SideShadowAlpha, shadowRadius = SideShadowRadius)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = rememberVectorPainter(Icons.Rounded.SkipNext),
                    contentDescription = "Next Track",
                    modifier = Modifier.size(SideIconSize),
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
}
