/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.carousel.HeroCarousel
import moe.rukamori.archivetune.ui.component.carousel.HeroItem
import moe.rukamori.archivetune.ui.component.carousel.HeroRollEffects
import moe.rukamori.archivetune.ui.component.carousel.HeroRollPhase
import moe.rukamori.archivetune.ui.component.carousel.rememberHeroRollController
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.utils.LocalPlayerIsExpanded

@Composable
internal fun QuickPicksHeroContent(
    distinctQuickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val animationsDisabled = LocalAnimationsDisabled.current
    val isPlayerExpanded = LocalPlayerIsExpanded.current

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var isSectionInViewport by remember { mutableStateOf(true) }

    val isCarouselActive = isLifecycleResumed && !isPlayerExpanded && isSectionInViewport

    val heroItems = remember(distinctQuickPicks) {
        distinctQuickPicks.map { song ->
            HeroItem(
                id = song.id,
                title = song.song.title,
                artist = song.artists.joinToString(", ") { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
            )
        }
    }
    val songMap = remember(distinctQuickPicks) {
        distinctQuickPicks.associateBy { it.id }
    }

    val rollController = rememberHeroRollController()

    HeroRollEffects(
        controller = rollController,
        items = heroItems,
        isCarouselActive = isCarouselActive,
        animationsDisabled = animationsDisabled,
    )

    fun playTrack(id: String, toggleIfCurrent: Boolean = true) {
        val song = songMap[id] ?: return
        if (id == mediaMetadata?.id) {
            if (toggleIfCurrent) {
                playerConnection.player.togglePlayPause()
            } else if (!playerConnection.player.playWhenReady) {
                playerConnection.player.play()
            }
        } else {
            playerConnection.playQueue(
                if (song.song.isLocal) {
                    ListQueue(items = listOf(song.toMediaItem()))
                } else {
                    YouTubeQueue.radio(song.toMediaMetadata())
                }
            )
        }
    }

    val currentPlayTrack by rememberUpdatedState(::playTrack)

    LaunchedEffect(rollController) {
        var lastHandledToken = rollController.state.completionToken
        snapshotFlow { rollController.state.completionToken }
            .collect { token ->
                if (token > lastHandledToken) {
                    lastHandledToken = token
                    val winner = rollController.state.result
                    if (rollController.state.phase == HeroRollPhase.Landed && winner != null) {
                        currentPlayTrack(winner.id, false)
                    }
                }
            }
    }

    fun showTrackMenu(id: String) {
        val song = songMap[id] ?: return
        haptics.longPress()
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val visible = bounds.bottom > 0 && bounds.top < screenHeightPx
                if (isSectionInViewport != visible) {
                    isSectionInViewport = visible
                }
            }
    ) {
        val heroHeight = HomeLayoutConfig.heroCarouselHeight(maxWidth)

        HeroCarousel(
            items = heroItems,
            rollCandidates = heroItems,
            rollState = rollController.state,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            isCarouselActive = isCarouselActive,
            onTrackClick = ::playTrack,
            onTrackLongClick = ::showTrackMenu,
            onRoll = { rollController.roll(isCarouselActive, animationsDisabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
        )
    }
}
