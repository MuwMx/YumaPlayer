package moe.rukamori.archivetune

import android.content.Intent
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.MiniPlayerLastAnchorKey
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.COLLAPSED_ANCHOR
import moe.rukamori.archivetune.ui.component.DISMISSED_ANCHOR
import moe.rukamori.archivetune.ui.component.EXPANDED_ANCHOR
import moe.rukamori.archivetune.ui.state.PlayerEvent
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun PlayerOverlayEffects(
    playerViewModel: PlayerViewModel,
    playerConnection: PlayerConnection?,
    navController: NavController,
    sheetState: BottomSheetState,
    targetWindow: Window?,
    aodModeEnabled: Boolean,
    aodModeLaunchRequestCount: Int,
    onResetAodLaunchRequestCount: () -> Unit,
    isYearInMusic: Boolean,
) {
    val context = LocalContext.current

    LaunchedEffect(playerViewModel) {
        playerViewModel.event.collect { event ->
            when (event) {
                is PlayerEvent.ShareTrack -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.url)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
                is PlayerEvent.Navigate -> {
                    navController.navigate(event.route) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    LaunchedEffect(aodModeLaunchRequestCount, playerConnection) {
        val launchRequestCount = aodModeLaunchRequestCount
        if (launchRequestCount == 0) return@LaunchedEffect
        val connection = playerConnection ?: return@LaunchedEffect
        if (!awaitRestorablePlayback(connection)) return@LaunchedEffect
        if (!sheetState.isExpandedOrExpanding) {
            sheetState.expandSoft()
        }
        connection.aodModeEnabled.value = true
        if (aodModeLaunchRequestCount == launchRequestCount) {
            onResetAodLaunchRequestCount()
        }
    }

    LaunchedEffect(aodModeEnabled) {
        targetWindow?.let { win ->
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            if (aodModeEnabled) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val miniPlayerAnchor by remember {
        derivedStateOf {
            when {
                sheetState.isExpanded -> EXPANDED_ANCHOR
                sheetState.isDismissed -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            }
        }
    }

    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) =
        rememberPreference(
            MiniPlayerLastAnchorKey,
            defaultValue = COLLAPSED_ANCHOR,
        )

    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
        mutableStateOf(false)
    }

    LaunchedEffect(miniPlayerAnchor, isYearInMusic, miniPlayerAnchorPersistenceEnabled) {
        if (!isYearInMusic && miniPlayerAnchorPersistenceEnabled) {
            setSavedMiniPlayerAnchor(miniPlayerAnchor)
        }
    }

    var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableStateOf(-1) }

    LaunchedEffect(isYearInMusic, playerConnection) {
        val connection = playerConnection ?: return@LaunchedEffect

        if (isYearInMusic) {
            if (yearInMusicSavedPlayerAnchor == -1) {
                yearInMusicSavedPlayerAnchor =
                    when {
                        sheetState.isExpanded -> EXPANDED_ANCHOR
                        sheetState.isCollapsed -> COLLAPSED_ANCHOR
                        sheetState.isDismissed -> DISMISSED_ANCHOR
                        else -> COLLAPSED_ANCHOR
                    }
            }

            if (!sheetState.isDismissed) {
                sheetState.dismiss()
            }
        } else if (yearInMusicSavedPlayerAnchor != -1) {
            val anchorToRestore = yearInMusicSavedPlayerAnchor
            yearInMusicSavedPlayerAnchor = -1

            if (!awaitRestorablePlayback(connection)) {
                sheetState.dismiss()
            } else {
                when (anchorToRestore) {
                    EXPANDED_ANCHOR -> sheetState.expandSoft()
                    COLLAPSED_ANCHOR -> sheetState.collapseSoft()
                    DISMISSED_ANCHOR -> sheetState.collapseSoft()
                    else -> sheetState.collapseSoft()
                }
            }
        }
    }

    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusic) {
        if (restoredMiniPlayerAnchor) return@LaunchedEffect
        val connection = playerConnection ?: return@LaunchedEffect
        connection.queueRestoreCompleted.first { it }
        if (!awaitRestorablePlayback(connection)) {
            if (!sheetState.isDismissed) {
                sheetState.dismiss()
            }
        } else {
            if (!isYearInMusic) {
                when (savedMiniPlayerAnchor) {
                    EXPANDED_ANCHOR -> sheetState.expandSoft()
                    COLLAPSED_ANCHOR -> sheetState.collapseSoft()
                    DISMISSED_ANCHOR -> sheetState.collapseSoft()
                    else -> sheetState.collapseSoft()
                }
            }
        }
        restoredMiniPlayerAnchor = true
        miniPlayerAnchorPersistenceEnabled = true
    }

    val currentPlayerBottomSheetState = rememberUpdatedState(sheetState)
    val currentIsYearInMusicScreen = rememberUpdatedState(isYearInMusic)

    DisposableEffect(playerConnection) {
        val player =
            playerConnection?.player ?: return@DisposableEffect onDispose { }
        val listener =
            object : Player.Listener {
                private fun collapseDismissedMiniPlayerForActivePlayback() {
                    if (
                        player.mediaItemCount > 0 &&
                        player.currentMediaItem != null &&
                        player.playWhenReady &&
                        player.playbackState != Player.STATE_IDLE &&
                        player.playbackState != Player.STATE_ENDED &&
                        currentPlayerBottomSheetState.value.isDismissed &&
                        !currentIsYearInMusicScreen.value
                    ) {
                        currentPlayerBottomSheetState.value.collapseSoft()
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    collapseDismissedMiniPlayerForActivePlayback()
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
}
