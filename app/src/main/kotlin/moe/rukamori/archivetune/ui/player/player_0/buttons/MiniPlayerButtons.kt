package moe.rukamori.archivetune.ui.player.player_0.buttons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.utils.bounceClick
import moe.rukamori.archivetune.ui.state.PlayerUiState

// ─── Design tokens (tweak everything from here) ──────────────────────────────
private val SideBoxNormal   = 36.dp
private val SideBoxLarge    = 56.dp
private val SideIconNormal  = 24.dp
private val SideIconLarge   = 32.dp
private val CenterBoxNormal = 40.dp
private val CenterBoxLarge  = 72.dp
private val CenterIconNormal = 30.dp
private val CenterIconLarge  = 40.dp
private val PlayOffsetLarge  = 2.dp

private val SideBgAlpha    = 0.08f
private val SideBorderAlpha = 0.15f
private val CenterBgAlpha  = 0.12f
private val CenterBorderAlpha = 0.25f
private val ButtonSpacing  = 8.dp

private val PressedScale   = 0.88f   // heavier shrink for small buttons
private val PressedAlpha   = 0.65f
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniPlayerButtons(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
) {
    val sideBoxSize   : Dp = if (isLarge) SideBoxLarge   else SideBoxNormal
    val sideIconSize  : Dp = if (isLarge) SideIconLarge  else SideIconNormal
    val centerBoxSize : Dp = if (isLarge) CenterBoxLarge else CenterBoxNormal
    val centerIconSize: Dp = if (isLarge) CenterIconLarge else CenterIconNormal
    val playOffset    : Dp = if (!state.isPlaying && isLarge) PlayOffsetLarge else 0.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
    ) {
        // ── Previous ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(sideBoxSize)
                .background(Color.White.copy(alpha = SideBgAlpha), CircleShape)
                .border(1.dp, Color.White.copy(alpha = SideBorderAlpha), CircleShape)
                .clip(CircleShape)
                .bounceClick(pressedScale = PressedScale) {
                    onAction(PlayerAction.Previous)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(Icons.Rounded.SkipPrevious),
                contentDescription = "Prev",
                modifier = Modifier.size(sideIconSize),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }

        // ── Play / Pause ──────────────────────────────────────────────────────
        val playPauseIcon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
        Box(
            modifier = Modifier
                .size(centerBoxSize)
                .background(Color.White.copy(alpha = CenterBgAlpha), CircleShape)
                .border(1.dp, Color.White.copy(alpha = CenterBorderAlpha), CircleShape)
                .clip(CircleShape)
                .bounceClick(pressedScale = PressedScale) {
                    onAction(PlayerAction.PlayPause)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(playPauseIcon),
                contentDescription = "Play/Pause",
                modifier = Modifier
                    .size(centerIconSize)
                    .offset(x = playOffset),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }

        // ── Next ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(sideBoxSize)
                .background(Color.White.copy(alpha = SideBgAlpha), CircleShape)
                .border(1.dp, Color.White.copy(alpha = SideBorderAlpha), CircleShape)
                .clip(CircleShape)
                .bounceClick(pressedScale = PressedScale) {
                    onAction(PlayerAction.Next)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberVectorPainter(Icons.Rounded.SkipNext),
                contentDescription = "Next",
                modifier = Modifier.size(sideIconSize),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
    }
}
