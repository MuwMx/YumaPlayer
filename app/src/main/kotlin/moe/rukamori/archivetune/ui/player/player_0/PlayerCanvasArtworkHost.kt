/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.ui.AspectRatioFrameLayout
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
internal fun PlayerCanvasArtworkHost(
    state: PlayerUiState,
    canvasState: PlayerCanvasState,
    immersiveTransitionAlpha: Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val canPlayCanvas by remember {
        derivedStateOf {
            state.isPlaying &&
                lyricsFractionProvider() < 0.05f &&
                queueFractionProvider() < 0.05f &&
                (!state.isImmersiveEnabled || immersiveTransitionAlpha > 0.05f)
        }
    }

    if (canvasState.isCanvasEnabled && canvasState.artwork != null) {
        key(state.trackUrl) {
            CanvasArtworkPlayer(
                primaryUrl = canvasState.primaryUrl,
                fallbackUrl = canvasState.fallbackUrl,
                isPlaying = canPlayCanvas,
                modifier = modifier,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            )
        }
    }
}
