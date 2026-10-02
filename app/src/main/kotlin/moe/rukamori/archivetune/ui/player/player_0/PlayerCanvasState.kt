/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.player.resolveCanvasArtworkForPlayback
import moe.rukamori.archivetune.utils.rememberPreference

@Stable
class PlayerCanvasState internal constructor(
    val isCanvasEnabled: Boolean,
    artwork: CanvasArtwork?,
) {
    var artwork by mutableStateOf(artwork)
        internal set

    val primaryUrl: String?
        get() = artwork?.preferredAnimationUrl

    val fallbackUrl: String?
        get() = artwork?.let { it.videoUrl.takeIf { url -> url != it.preferredAnimationUrl } }
}

@Composable
fun rememberPlayerCanvasState(
    mediaId: String?,
    songTitle: String?,
    artistName: String?,
): PlayerCanvasState {
    val (isCanvasEnabled) = rememberPreference(ArchiveTuneCanvasKey, defaultValue = false)
    val canvasState = remember(mediaId, isCanvasEnabled) {
        PlayerCanvasState(isCanvasEnabled, null)
    }

    LaunchedEffect(isCanvasEnabled, mediaId, songTitle, artistName) {
        canvasState.artwork = null
        if (!isCanvasEnabled || mediaId.isNullOrBlank()) {
            return@LaunchedEffect
        }

        CanvasArtworkPlaybackCache.get(mediaId)?.let { cached ->
            canvasState.artwork = cached
            return@LaunchedEffect
        }

        val requestedMediaId = mediaId
        val resolved =
            resolveCanvasArtworkForPlayback(
                mediaId = mediaId,
                songTitleRaw = songTitle ?: "",
                artistNameRaw = artistName ?: "",
                storefront = "us",
                requireVertical = false,
                allowNetwork = true,
            )
        if (mediaId == requestedMediaId) {
            canvasState.artwork = resolved
        }
    }

    return canvasState
}
