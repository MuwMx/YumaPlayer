package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import timber.log.Timber

typealias DualForwardingPlayer = moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
typealias PlayerRole = moe.rukamori.archivetune.audiodsp.PlayerRole
typealias DualPlayerRoleHolder = moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder

internal fun shouldUseLegacyPath(durationMs: Long): Boolean =
    moe.rukamori.archivetune.audiodsp.shouldUseLegacyPath(durationMs)

internal fun MusicService.prepareNext(target: MusicService.CrossfadeTarget): ExoPlayer? {
    val existingPlayer = secondaryCrossfadePlayer
    if (existingPlayer != null && secondaryCrossfadeTarget == target) {
        existingPlayer.playWhenReady = false
        existingPlayer.seekTo(target.index, 0L)
        return existingPlayer
    }

    releaseSecondaryCrossfadePlayer()

    val targetItem =
        runCatching { player.getMediaItemAt(target.index) }
            .getOrNull()
            ?.takeIf { it.mediaId == target.mediaId }
            ?: return null

    return runCatching {
        val secondaryPlayer =
            reserveCrossfadePlayer?.also {
                it.addListener(secondaryCrossfadeListener)
                it.setOffloadEnabled(false)
                it.skipSilenceEnabled = localPlayer.skipSilenceEnabled
            } ?: createSecondaryCrossfadePlayer()
        reserveCrossfadePlayer = null
        secondaryPlayer.also {
            secondaryCrossfadePlayer = it
            secondaryCrossfadeTarget = target
            val queueItems =
                (0 until player.mediaItemCount).mapNotNull { index ->
                    runCatching { player.getMediaItemAt(index) }.getOrNull()
                }
            it.setMediaItems(queueItems, target.index.coerceIn(0, (queueItems.size - 1).coerceAtLeast(0)), 0L)
            it.playbackParameters = player.playbackParameters
            it.volume = 0f
            it.playWhenReady = false
            it.pauseAtEndOfMediaItems = true
            it.prepare()
        }
    }.onFailure { error ->
        Timber.tag(MusicService.TAG).w(error, "Failed to prepare crossfade player")
        releaseSecondaryCrossfadePlayer()
    }.getOrNull()
}
