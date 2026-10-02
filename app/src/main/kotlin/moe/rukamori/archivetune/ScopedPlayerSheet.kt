package moe.rukamori.archivetune

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.player.player_0.UnifiedPlayerSheetV2
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction

@Composable
internal fun ScopedPlayerSheet(
    playerViewModel: PlayerViewModel,
    playerConnection: PlayerConnection?,
    navController: NavController,
    bottomNavigationBarHeight: Dp,
    hazeState: HazeState?,
    pureBlack: Boolean,
    blurRadius: Float,
    dropState: MiniPlayerDropState = MiniPlayerDropState.Default,
    onExpansionFractionChanged: (Float) -> Unit,
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
    UnifiedPlayerSheetV2(
        state = uiState,
        queueState = queueState,
        progressMsProvider = playerViewModel.progressMsProvider,
        onAction = { action ->
            when (action) {
                is PlayerAction.StartRadio -> {
                    playerConnection?.startRadioSeamlessly()
                }
                is PlayerAction.OpenArtist -> {
                    playerConnection?.service?.currentMediaMetadata?.value?.artists?.firstOrNull()?.id?.let { artistId ->
                        playerViewModel.requestSheetCollapse()
                        navController.navigate("artist/$artistId")
                    }
                }
                is PlayerAction.OpenAlbum -> {
                    playerConnection?.service?.currentMediaMetadata?.value?.album?.id?.let { albumId ->
                        playerViewModel.requestSheetCollapse()
                        navController.navigate("album/$albumId")
                    }
                }
                else -> playerViewModel.handleAction(action)
            }
        },
        onLyricsClick = { playerViewModel.setLyricsVisible(true) },
        onCloseLyricsClick = { playerViewModel.setLyricsVisible(false) },
        onOpenQueue = { playerViewModel.setQueueVisible(true) },
        onCloseQueueClick = { playerViewModel.setQueueVisible(false) },
        onSearchLyricsClick = { playerViewModel.fetchLyrics() },
        onSeek = { position -> playerViewModel.seekTo(position.toLong()) },
        onSeekStarted = { playerViewModel.onSeekStarted() },
        onBackgroundStyleChanged = { playerViewModel.setBlurBackgroundEnabled(it) },
        onImmersiveChanged = { playerViewModel.setImmersiveEnabled(it) },

        bottomBarHeight = bottomNavigationBarHeight,
        hazeState = hazeState,
        pureBlack = pureBlack,
        blurRadius = blurRadius,
        dropState = dropState,
        onExpansionFractionChanged = onExpansionFractionChanged,
    )
}
