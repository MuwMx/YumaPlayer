package moe.rukamori.archivetune.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import timber.log.Timber
import java.util.IdentityHashMap

internal class DualForwardingPlayer(
    private val playerA: Player,
) : ForwardingPlayer(playerA) {
    var activePlayer: Player = playerA
        private set

    fun attachPlayer(newPlayer: Player) {
        if (activePlayer === newPlayer) return
        val previous = activePlayer
        migrateListeners(from = previous, to = newPlayer)
        setWrappedPlayer(newPlayer)
        activePlayer = newPlayer
    }

    private fun setWrappedPlayer(newPlayer: Player) {
        playerField.set(this, newPlayer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun migrateListeners(from: Player, to: Player) {
        val map =
            runCatching {
                listenersField.get(this) as? IdentityHashMap<Player.Listener, Player.Listener>
            }.getOrNull() ?: return

        synchronized(map) {
            for (forwardingListener in map.values) {
                runCatching { from.removeListener(forwardingListener) }
                runCatching { to.addListener(forwardingListener) }
            }
        }
    }

    companion object {
        private val playerField =
            ForwardingPlayer::class.java.getDeclaredField("player").apply {
                isAccessible = true
            }

        private val listenersField =
            ForwardingPlayer::class.java.getDeclaredField("listeners").apply {
                isAccessible = true
            }
    }
}

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

    fun reset() {
        playerA = PlayerRole.MASTER
        playerB = PlayerRole.STANDBY
    }
}

internal fun shouldUseLegacyPath(durationMs: Long): Boolean = durationMs <= 0L

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

