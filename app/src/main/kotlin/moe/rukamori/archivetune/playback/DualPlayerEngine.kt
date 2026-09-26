package moe.rukamori.archivetune.playback

import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

internal enum class PlayerRole {
    MASTER,
    STANDBY,
}

internal data class DualPlayerRoleHolder(
    var playerA: PlayerRole = PlayerRole.MASTER,
    var playerB: PlayerRole = PlayerRole.STANDBY,
) {
    fun swap() {
        val temp = playerA
        playerA = playerB
        playerB = temp
    }
}

internal fun shouldUseLegacyPath(durationMs: Long): Boolean = durationMs <= 0L

internal fun MusicService.prepareNext(target: MusicService.CrossfadeTarget): ExoPlayer? {
    val existingPlayer = secondaryCrossfadePlayer
    if (existingPlayer != null && secondaryCrossfadeTarget == target) {
        return existingPlayer
    }

    releaseSecondaryCrossfadePlayer()

    val targetItem =
        runCatching { player.getMediaItemAt(target.index) }
            .getOrNull()
            ?.takeIf { it.mediaId == target.mediaId }
            ?: return null

    return runCatching {
        createSecondaryCrossfadePlayer().also { secondaryPlayer ->
            secondaryCrossfadePlayer = secondaryPlayer
            secondaryCrossfadeTarget = target
            secondaryPlayer.setMediaItem(targetItem)
            secondaryPlayer.playbackParameters = player.playbackParameters
            secondaryPlayer.volume = 0f
            secondaryPlayer.pauseAtEndOfMediaItems = true
            secondaryPlayer.prepare()
        }
    }.onFailure { error ->
        Timber.tag(MusicService.TAG).w(error, "Failed to prepare crossfade player")
        releaseSecondaryCrossfadePlayer()
    }.getOrNull()
}

