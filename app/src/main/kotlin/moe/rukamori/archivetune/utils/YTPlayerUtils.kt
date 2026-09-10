/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse

/**
 * Playback facade coordinating URL caching, stream resolution, and error mapping:
 * - [StreamUrlCache]: stream URL and PoToken caching, expiration and client failure tracking
 * - [PlaybackDataResolver]: candidate resolution, client ordering, format selection and probing
 * - [PlaybackErrorMapper]: playback error classification and recovery
 */
object YTPlayerUtils {
    const val STREAM_URL_EXPIRY_SAFETY_MS = StreamUrlCache.STREAM_URL_EXPIRY_SAFETY_MS

    class LoginRequiredForPlaybackException(
        val videoId: String,
        val targetUrl: String,
        reason: String?,
    ) : IllegalStateException(reason)

    class InvalidPlaybackLoginContextException(
        val videoId: String,
        val targetUrl: String,
        cause: Throwable,
    ) : IllegalStateException("Invalid YouTube Music playback login context", cause)

    class BotDetectionPlaybackException(
        val videoId: String,
        val clients: Set<String>,
    ) : IllegalStateException("YouTube playback bot detection blocked all stream clients")

    class BadStreamPlayerResponseException(
        val videoId: String,
    ) : IllegalStateException("YouTube playback stream clients returned no playable response")

    fun clearPlaybackAuthCaches() {
        StreamUrlCache.clearPlaybackAuthCaches()
    }

    suspend fun recoverFromBadStreamPlayerResponse(videoId: String) {
        PlaybackDataResolver.recoverFromBadStreamPlayerResponse(videoId)
    }

    internal fun shouldPreferWebRemixForLoggedInPlayback(
        preferredStreamClient: PlayerStreamClient,
        isLoggedIn: Boolean,
        webClientPoTokenEnabled: Boolean,
        hasPlayerPoToken: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        PlaybackDataResolver.shouldPreferWebRemixForLoggedInPlayback(
            preferredStreamClient = preferredStreamClient,
            isLoggedIn = isLoggedIn,
            webClientPoTokenEnabled = webClientPoTokenEnabled,
            hasPlayerPoToken = hasPlayerPoToken,
            hasGvsPoToken = hasGvsPoToken,
        )

    internal fun shouldSkipCipheredWebPlaybackCandidate(
        webClientPoTokenEnabled: Boolean,
        isWebClient: Boolean,
        isCiphered: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        PlaybackDataResolver.shouldSkipCipheredWebPlaybackCandidate(
            webClientPoTokenEnabled = webClientPoTokenEnabled,
            isWebClient = isWebClient,
            isCiphered = isCiphered,
            hasGvsPoToken = hasGvsPoToken,
        )

    internal fun buildStreamCacheKey(
        videoId: String,
        itag: Int,
        client: YouTubeClient,
        authFingerprint: String,
    ): String = StreamUrlCache.buildStreamCacheKey(videoId, itag, client, authFingerprint)

    fun invalidateCachedStreamUrls(videoId: String) {
        StreamUrlCache.invalidateCachedStreamUrls(videoId)
    }

    fun isExpiredOrNearExpiredStreamUrl(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = StreamUrlCache.isExpiredOrNearExpiredStreamUrl(url, nowMs)

    fun markStreamClientFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        StreamUrlCache.markStreamClientFailed(videoId, clientKey, httpStatusCode, authFingerprint)
    }

    fun markPreferredClientFailed(
        videoId: String,
        client: PlayerStreamClient,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        StreamUrlCache.markPreferredClientFailed(videoId, client, httpStatusCode, authFingerprint)
    }

    internal fun buildFailedClientKey(
        videoId: String,
        clientKey: String,
        authFingerprint: String,
    ): String = StreamUrlCache.buildFailedClientKey(videoId, clientKey, authFingerprint)

    internal fun resolvePreferredPlaybackClient(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): YouTubeClient = PlaybackDataResolver.resolvePreferredPlaybackClient(preferredStreamClient, authState)

    internal fun buildStreamClientOrder(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): List<YouTubeClient> = PlaybackDataResolver.buildStreamClientOrder(preferredStreamClient, authState)

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val authFingerprint: String,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [PlaybackDataResolver.MAIN_CLIENT].
     * Format & stream can be from [PlaybackDataResolver.MAIN_CLIENT] or [PlaybackDataResolver.STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        // if provided, this preference overrides ConnectivityManager.isActiveNetworkMetered
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> =
        PlaybackDataResolver.playerResponseForPlayback(
            videoId = videoId,
            playlistId = playlistId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager,
            preferredStreamClient = preferredStreamClient,
            networkMetered = networkMetered,
        )

    suspend fun playerResponseForDownload(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> =
        PlaybackDataResolver.playerResponseForDownload(
            videoId = videoId,
            playlistId = playlistId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager,
            networkMetered = networkMetered,
        )

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> =
        PlaybackDataResolver.playerResponseForMetadata(videoId, playlistId, authState)

    fun isBotDetectionException(error: PlaybackException): Boolean =
        PlaybackErrorMapper.isBotDetectionException(error)

    fun isBadStreamPlayerResponseException(error: PlaybackException): Boolean =
        PlaybackErrorMapper.isBadStreamPlayerResponseException(error)
}
