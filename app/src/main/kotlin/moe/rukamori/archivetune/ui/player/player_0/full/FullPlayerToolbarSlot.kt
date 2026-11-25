/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0.full

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerToolbar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
internal fun FullPlayerToolbarSlot(
    state: PlayerUiState,
    updateState: UpdateState,
    rootCoordinates: LayoutCoordinates?,
    queueFractionProvider: () -> Float,
    onCollapseClick: () -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onPositionedInRoot: (top: Float, bottom: Float) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (PlayerAction) -> Unit = {},
) {
    val isToolbarInteractive by remember(queueFractionProvider) {
        derivedStateOf { queueFractionProvider() < 0.94f }
    }

    PlayerToolbar(
        state = state,
        onCollapseClick = onCollapseClick,
        onBackgroundStyleChanged = onBackgroundStyleChanged,
        onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
        onTimerBadgeClick = { onOpenSettingsMenu(PlayerMenuScreen.SLEEP_TIMER) },
        onAction = onAction,
        hasUpdate = updateState is UpdateState.SoftUpdate,
        enabled = isToolbarInteractive,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
            .onGloballyPositioned { coords ->
                if (rootCoordinates != null && rootCoordinates.isAttached && coords.isAttached) {
                    val top = rootCoordinates.localPositionOf(coords, Offset.Zero).y
                    val bottom = rootCoordinates.localPositionOf(
                        coords,
                        Offset(0f, coords.size.height.toFloat())
                    ).y
                    onPositionedInRoot(top, bottom)
                }
            }
            .graphicsLayer {
                val queueFraction = queueFractionProvider().coerceIn(0f, 1f)
                alpha = (1f - (queueFraction - 0.75f) / (0.95f - 0.75f)).coerceIn(0f, 1f)
            }
    )
}
