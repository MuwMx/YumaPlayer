/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private val CanvasPlaybackStallCheckInterval = 1.seconds
private val CanvasPlaybackStallTimeout = 5.seconds
private const val CanvasPlaybackLogTag = "PlayerCanvas"

@Stable
internal class CanvasPlaybackController(
    val player: Player? = null,
    initialUrl: String? = null,
) {
    var currentUrl: String? by mutableStateOf(initialUrl)
        private set

    var isVideoReady: Boolean by mutableStateOf(false)
        private set

    var hasFailed: Boolean by mutableStateOf(false)
        private set

    var onReadyChange: (Boolean) -> Unit = {}

    private val playerId: String = player?.let { Integer.toHexString(System.identityHashCode(it)) } ?: "none"

    fun updateTargets(primary: String?, fallback: String?) {
        val target = primary ?: fallback
        if (target != null && target != currentUrl) {
            currentUrl = target
            isVideoReady = false
            hasFailed = false
            onReadyChange(false)
        }
    }

    fun syncPlayback(
        isPlaying: Boolean,
        isStarted: Boolean,
        lifecycleStateName: String = "",
    ) {
        val activePlayer = player ?: return
        Timber.tag(CanvasPlaybackLogTag).d("Canvas playback sync: player=$playerId isPlaying=$isPlaying lifecycle=$lifecycleStateName failed=$hasFailed")
        activePlayer.setCanvasPlayback(
            isPlaying = isPlaying && !hasFailed,
            isStarted = isStarted,
        )
    }

    fun prepareMedia(
        primary: String?,
        fallback: String?,
        isPlaying: Boolean,
        isStarted: Boolean,
    ) {
        val url = currentUrl ?: return
        val normalized = url.trim()
        val activePlayer = player ?: return
        if (activePlayer.currentMediaItem?.localConfiguration?.uri?.toString() == normalized) return
        hasFailed = false
        isVideoReady = false
        onReadyChange(false)
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && url == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && url == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        Timber.tag(CanvasPlaybackLogTag).d("Preparing mediaItem: player=$playerId url=$normalized, mimeType=$mimeType, isPlaying=$isPlaying")
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
        activePlayer.setCanvasPlayback(
            isPlaying = isPlaying,
            isStarted = isStarted,
        )
    }

    fun attachListener(
        primary: String?,
        fallback: String?,
        isPlayingProvider: () -> Boolean,
        isStartedProvider: () -> Boolean,
    ): () -> Unit {
        val activePlayer = player ?: return {}
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    handlePlayerError(error, primary, fallback)
                }

                override fun onRenderedFirstFrame() {
                    handleFirstFrameRendered(
                        isPlaying = isPlayingProvider(),
                        isStarted = isStartedProvider(),
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "$playbackState"
                    }
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas player playbackState: $stateName, player=$playerId playWhenReady=${activePlayer.playWhenReady}, isPlaying=${activePlayer.isPlaying}")
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onPlayWhenReadyChanged: player=$playerId playWhenReady=$playWhenReady, reason=$reason")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onIsPlayingChanged: player=$playerId isPlaying=$isPlaying")
                }
            }
        activePlayer.addListener(listener)
        return { activePlayer.removeListener(listener) }
    }

    internal fun handlePlayerError(
        error: androidx.media3.common.PlaybackException?,
        primary: String?,
        fallback: String?,
    ) {
        Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed on $currentUrl")
        val next =
            when (currentUrl) {
                primary -> fallback?.takeIf { it != currentUrl }
                else -> null
            }
        if (!next.isNullOrBlank()) {
            Timber.tag(CanvasPlaybackLogTag).i("Switching canvas to fallback URL: $next")
            currentUrl = next
            isVideoReady = false
            hasFailed = false
            onReadyChange(false)
        } else {
            Timber.tag(CanvasPlaybackLogTag).e("All canvas URLs failed, stopping canvas player")
            hasFailed = true
            isVideoReady = false
            onReadyChange(false)
            player?.stop()
        }
    }

    internal fun handleFirstFrameRendered(
        isPlaying: Boolean,
        isStarted: Boolean,
    ) {
        Timber.tag(CanvasPlaybackLogTag).i("First video frame rendered on Canvas! player=$playerId isVideoReady = true")
        isVideoReady = true
        if (isPlaying && !hasFailed) {
            player?.setCanvasPlayback(
                isPlaying = true,
                isStarted = isStarted,
            )
        }
    }

    suspend fun monitorPlaybackStall(
        primary: String?,
        fallback: String?,
        isPlaying: Boolean,
        isStartedProvider: () -> Boolean,
    ) {
        val activePlayer = player ?: return
        val activeCurrentUrl = currentUrl ?: return
        if (!isPlaying || fallback.isNullOrBlank() || activeCurrentUrl != primary || activeCurrentUrl.startsWith("file://") || hasFailed) {
            return
        }

        var lastPosition = activePlayer.currentPosition
        var stalledFor = kotlin.time.Duration.ZERO

        while (coroutineContext.isActive && currentUrl == primary && !hasFailed) {
            delay(CanvasPlaybackStallCheckInterval)

            if (!isStartedProvider()) {
                stalledFor = kotlin.time.Duration.ZERO
                lastPosition = activePlayer.currentPosition
                continue
            }

            val currentPosition = activePlayer.currentPosition
            val playbackState = activePlayer.playbackState
            val positionAdvanced = currentPosition != lastPosition
            val isActivelyRendering =
                playbackState == Player.STATE_READY &&
                        activePlayer.isPlaying &&
                        positionAdvanced

            stalledFor =
                if (isActivelyRendering) {
                    kotlin.time.Duration.ZERO
                } else {
                    stalledFor + CanvasPlaybackStallCheckInterval
                }

            if (stalledFor >= CanvasPlaybackStallTimeout) {
                currentUrl = fallback
                isVideoReady = false
                hasFailed = false
                onReadyChange(false)
                return
            }

            lastPosition = currentPosition
        }
    }

    fun release() {
        player?.release()
    }
}

internal fun Player.setCanvasPlayback(
    isPlaying: Boolean,
    isStarted: Boolean = true,
) {
    if (isPlaying && isStarted) {
        play()
    } else {
        pause()
    }
}
