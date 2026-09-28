package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import timber.log.Timber

typealias DualForwardingPlayer = moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
typealias PlayerRole = moe.rukamori.archivetune.audiodsp.PlayerRole
typealias DualPlayerRoleHolder = moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder

internal fun shouldUseLegacyPath(durationMs: Long): Boolean =
    moe.rukamori.archivetune.audiodsp.shouldUseLegacyPath(durationMs)

internal fun MusicService.prepareNext(target: CrossfadeTarget): ExoPlayer? {
    val success = controller.prepareNext(target)
    return if (success) transitionDeck?.player as? ExoPlayer else null
}
