package moe.rukamori.archivetune.ui.player.host

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.ui.PlayerViewModel

@Composable
internal fun PlayerBackHandler(
    playerViewModel: PlayerViewModel,
    playerExpansionAnimatable: Animatable<Float, AnimationVector1D>,
) {
    val isPlayerLyricsVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isLyricsVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val isPlayerQueueVisible by remember(playerViewModel) {
        playerViewModel.uiState
            .map { it.isQueueVisible }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    BackHandler(enabled = playerExpansionAnimatable.value > 0.5f) {
        when {
            isPlayerLyricsVisible -> {
                playerViewModel.setLyricsVisible(false)
            }
            isPlayerQueueVisible -> {
                playerViewModel.setQueueVisible(false)
            }
            else -> {
                playerViewModel.requestSheetCollapse()
            }
        }
    }
}
