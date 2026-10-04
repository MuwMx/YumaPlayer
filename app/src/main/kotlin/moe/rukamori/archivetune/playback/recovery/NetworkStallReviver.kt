/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback.recovery

import android.net.Network
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.isLocalMediaId

class NetworkStallReviver(
    private val scope: CoroutineScope,
    private val playerActions: PlayerActions,
    private val networkState: NetworkState,
) {
    interface PlayerActions {
        val currentMediaItem: MediaItem?
        val currentMediaItemIndex: Int
        val currentPosition: Long
        val playWhenReady: Boolean
        val playbackState: Int
        val isPlaying: Boolean
        val playbackSuppressionReason: Int
        val isCrossfading: Boolean
        fun prepare()
        fun play()
        fun stop()
        fun seekTo(mediaItemIndex: Int, positionMs: Long)
        fun seekTo(positionMs: Long)
        fun evictMediaConnectionPools()
        fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)
    }

    fun interface NetworkState {
        fun isNetworkConnected(): Boolean
    }

    val waitingForNetworkConnection = MutableStateFlow(false)
    private var networkRecoveryGeneration: Long = 0L
    private var lastRevivedNetworkGeneration: Long = -1L
    private var lastForceReviveTimeMs: Long = 0L
    private var lastActiveNetwork: Network? = null
    internal var networkStallRecoveryJob: Job? = null

    fun cancelNetworkStallRecovery() {
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
    }

    fun onNetworkStatusChanged(isConnected: Boolean, currentNetwork: Network?) {
        if (!isConnected || currentNetwork == null) {
            networkStallRecoveryJob?.cancel()
            networkStallRecoveryJob = null
            lastActiveNetwork = null
            networkRecoveryGeneration++
            return
        }

        val isNewNetwork = currentNetwork != lastActiveNetwork
        if (isNewNetwork) {
            lastActiveNetwork = currentNetwork
            networkRecoveryGeneration++
            playerActions.evictMediaConnectionPools()
        }

        val currentGen = networkRecoveryGeneration

        if (waitingForNetworkConnection.value) {
            waitingForNetworkConnection.value = false
            if (playerActions.currentMediaItem != null && playerActions.playWhenReady &&
                playerActions.playbackState == Player.STATE_IDLE
            ) {
                lastRevivedNetworkGeneration = currentGen
                playerActions.evictMediaConnectionPools()
                playerActions.prepare()
                playerActions.play()
                return
            }
        }

        if (currentGen == lastRevivedNetworkGeneration) {
            return
        }

        val currentItem = playerActions.currentMediaItem
        if (currentItem != null && playerActions.playWhenReady && !currentItem.mediaId.isLocalMediaId()) {
            val state = playerActions.playbackState
            if (state == Player.STATE_BUFFERING || state == Player.STATE_READY) {
                scheduleNetworkStallRecovery(currentGen)
            }
        }
    }

    fun waitOnNetworkError() {
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
        waitingForNetworkConnection.value = true
    }

    private fun scheduleNetworkStallRecovery(gen: Long) {
        networkStallRecoveryJob?.cancel()
        val initialPos = playerActions.currentPosition
        val initialIndex = playerActions.currentMediaItemIndex
        val initialMediaId = playerActions.currentMediaItem?.mediaId ?: return

        networkStallRecoveryJob =
            scope.launch {
                delay(NETWORK_STALL_WINDOW_MS)
                if (gen != networkRecoveryGeneration || gen == lastRevivedNetworkGeneration) return@launch
                if (!networkState.isNetworkConnected()) return@launch
                if (!playerActions.playWhenReady) return@launch
                if (playerActions.currentMediaItemIndex != initialIndex || playerActions.currentMediaItem?.mediaId != initialMediaId) return@launch

                val currentState = playerActions.playbackState
                val currentPos = playerActions.currentPosition
                val isStalled =
                    when (currentState) {
                        Player.STATE_BUFFERING -> true
                        Player.STATE_READY -> currentPos <= initialPos && !playerActions.isPlaying &&
                            playerActions.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
                        else -> false
                    }

                if (isStalled) {
                    revivePlaybackFromStall(gen)
                }
            }
    }

    fun forceRevivePlayback(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastForceReviveTimeMs < FORCE_REVIVE_DEBOUNCE_MS) return false
        lastForceReviveTimeMs = now
        networkRecoveryGeneration++
        networkStallRecoveryJob?.cancel()
        networkStallRecoveryJob = null
        revivePlaybackFromStall()
        return true
    }

    fun revivePlaybackFromStall(gen: Long = networkRecoveryGeneration) {
        lastRevivedNetworkGeneration = gen
        if (playerActions.currentMediaItem == null) return
        if (playerActions.isCrossfading) {
            playerActions.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        }
        playerActions.evictMediaConnectionPools()
        val mediaItemIndex = playerActions.currentMediaItemIndex
        val resumePosition = playerActions.currentPosition.coerceAtLeast(0L)
        playerActions.stop()
        playerActions.prepare()
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex >= 0) {
            playerActions.seekTo(mediaItemIndex, resumePosition)
        } else {
            playerActions.seekTo(resumePosition)
        }
        playerActions.evictMediaConnectionPools()
        playerActions.play()
    }

    private companion object {
        const val NETWORK_STALL_WINDOW_MS = 3_000L
        const val FORCE_REVIVE_DEBOUNCE_MS = 2_000L
    }
}
