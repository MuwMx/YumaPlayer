package moe.rukamori.archivetune.playback.engine

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.playback.MusicService

internal fun MusicService.prepareNext(target: CrossfadeTarget): ExoPlayer? {
    val success = controller.prepareNext(target)
    return if (success) transitionDeck?.player as? ExoPlayer else null
}
