package moe.rukamori.archivetune.ui.player.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.player.player_0.UnifiedPlayerSheetV2
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sheets.PlayerArtistChoiceSheet

@Composable
internal fun ScopedPlayerSheet(
    playerViewModel: PlayerViewModel,
    playerConnection: PlayerConnection?,
    navController: NavController,
    bottomNavigationBarHeight: Dp,
    hazeState: HazeState?,
    pureBlack: Boolean,
    blurRadius: Float,
    useRail: Boolean = false,
    dropState: MiniPlayerDropState = MiniPlayerDropState.Default,
    onExpansionFractionChanged: (Float) -> Unit,
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()

    var showArtistChoiceSheet by rememberSaveable { mutableStateOf(false) }
    val currentMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(playerConnection?.service?.currentMediaMetadata?.value) }
    val artists = remember(currentMetadata?.artists) {
        currentMetadata?.artists.orEmpty().filter { !it.id.isNullOrBlank() }
    }

    UnifiedPlayerSheetV2(
        state = uiState,
        queueState = queueState,
        playbackProgress = playerViewModel.playbackProgress,
        onAction = { action ->
            when (action) {
                is PlayerAction.StartRadio -> {
                    playerConnection?.startRadioSeamlessly()
                }
                is PlayerAction.OpenArtist -> {
                    if (artists.size > 1) {
                        showArtistChoiceSheet = true
                    } else {
                        artists.firstOrNull()?.id?.let { artistId ->
                            playerViewModel.requestSheetCollapse()
                            navController.navigate("artist/$artistId")
                        }
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
        useRail = useRail,
        hazeState = hazeState,
        pureBlack = pureBlack,
        blurRadius = blurRadius,
        dropState = dropState,
        onExpansionFractionChanged = onExpansionFractionChanged,
    )

    PlayerArtistChoiceSheet(
        isVisible = showArtistChoiceSheet,
        artists = artists,
        onArtistSelect = { id ->
            playerViewModel.requestSheetCollapse()
            navController.navigate("artist/$id")
        },
        onDismiss = { showArtistChoiceSheet = false },
    )
}
