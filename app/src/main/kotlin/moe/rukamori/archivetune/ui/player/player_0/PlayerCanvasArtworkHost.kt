package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.ui.AspectRatioFrameLayout
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun PlayerCanvasArtworkHost(
    state: PlayerUiState,
    immersiveTransitionAlpha: Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val (isCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
    var canvasArtwork by remember(state.trackUrl) { mutableStateOf<CanvasArtwork?>(null) }

    LaunchedEffect(isCanvasEnabled, state.trackUrl, state.title, state.artist) {
        canvasArtwork = null
        if (!isCanvasEnabled || state.trackUrl.isBlank()) {
            return@LaunchedEffect
        }
        CanvasArtworkPlaybackCache.get(state.trackUrl)?.let {
            canvasArtwork = it
            return@LaunchedEffect
        }
        val requestedTrackUrl = state.trackUrl
        val resolved = resolveCanvasArtworkForPlayback(
            mediaId = requestedTrackUrl,
            songTitleRaw = state.title,
            artistNameRaw = state.artist,
            storefront = "us",
            requireVertical = false,
            allowNetwork = true,
        )
        if (state.trackUrl == requestedTrackUrl) {
            canvasArtwork = resolved
        }
    }

    val canPlayCanvas by remember {
        derivedStateOf {
            state.isPlaying &&
                    lyricsFractionProvider() < 0.05f &&
                    queueFractionProvider() < 0.05f &&
                    (!state.isImmersiveEnabled || immersiveTransitionAlpha > 0.05f)
        }
    }

    if (isCanvasEnabled && canvasArtwork != null) {
        key(state.trackUrl) {
            CanvasArtworkPlayer(
                primaryUrl = canvasArtwork?.preferredAnimationUrl,
                fallbackUrl = canvasArtwork?.fallbackUrl,
                isPlaying = canPlayCanvas,
                modifier = modifier,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
        }
    }
}

private val CanvasArtwork.fallbackUrl: String?
    get() = videoUrl.takeIf { it != preferredAnimationUrl }
