package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget

internal fun MusicService.prepareNext(target: CrossfadeTarget): ExoPlayer? {
    val success = controller.prepareNext(target)
    return if (success) transitionDeck?.player as? ExoPlayer else null
}
