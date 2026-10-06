/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.net.ConnectivityManager
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.resolver.*
import okhttp3.OkHttpClient

typealias PlaybackData = YTPlayerUtils.PlaybackData

object PlaybackDataResolver {
    val MAIN_CLIENT: YouTubeClient = PlaybackClientSelector.MAIN_CLIENT
    val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = PlaybackClientSelector.STREAM_FALLBACK_CLIENTS

    internal fun currentStreamClient(): OkHttpClient = PlaybackStreamValidator.currentStreamClient()

    suspend fun recoverFromBadStreamPlayerResponse(videoId: String) =
        PlaybackAuthCoordinator.recoverFromBadStreamPlayerResponse(videoId)

    internal suspend fun ensureVisitorDataReady(
        videoId: String, authState: PlaybackAuthState, forceRefresh: Boolean = false, reason: String,
    ): PlaybackAuthState = PlaybackAuthCoordinator.ensureVisitorDataReady(videoId, authState, forceRefresh, reason)

    internal suspend fun repairAuthStateAfterBotDetection(
        videoId: String, authState: PlaybackAuthState, reason: String,
    ): PlaybackAuthState = PlaybackAuthCoordinator.repairAuthStateAfterBotDetection(videoId, authState, reason)

    fun shouldPreferWebRemixForLoggedInPlayback(
        preferredStreamClient: PlayerStreamClient, isLoggedIn: Boolean, webClientPoTokenEnabled: Boolean,
        hasPlayerPoToken: Boolean, hasGvsPoToken: Boolean,
    ): Boolean = PlaybackAuthCoordinator.shouldPreferWebRemixForLoggedInPlayback(preferredStreamClient, isLoggedIn, webClientPoTokenEnabled, hasPlayerPoToken, hasGvsPoToken)

    internal fun hasCompleteWebPlaybackPoToken(authState: PlaybackAuthState): Boolean =
        PlaybackAuthCoordinator.hasCompleteWebPlaybackPoToken(authState)

    fun shouldSkipCipheredWebPlaybackCandidate(
        webClientPoTokenEnabled: Boolean, isWebClient: Boolean, isCiphered: Boolean, hasGvsPoToken: Boolean,
    ): Boolean = PlaybackAuthCoordinator.shouldSkipCipheredWebPlaybackCandidate(webClientPoTokenEnabled, isWebClient, isCiphered, hasGvsPoToken)

    fun resolvePreferredPlaybackClient(preferredStreamClient: PlayerStreamClient, authState: PlaybackAuthState): YouTubeClient =
        PlaybackClientSelector.resolvePreferredPlaybackClient(preferredStreamClient, authState)

    fun buildStreamClientOrder(preferredStreamClient: PlayerStreamClient, authState: PlaybackAuthState): List<YouTubeClient> =
        PlaybackClientSelector.buildStreamClientOrder(preferredStreamClient, authState)

    suspend fun playerResponseForPlayback(
        videoId: String, playlistId: String? = null, audioQuality: AudioQuality, connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR, networkMetered: Boolean? = null,
    ): Result<PlaybackData> = PlaybackStreamFetcher.playerResponseForPlayback(videoId, playlistId, audioQuality, connectivityManager, preferredStreamClient, networkMetered)

    suspend fun playerResponseForDownload(
        videoId: String, playlistId: String? = null, audioQuality: AudioQuality, connectivityManager: ConnectivityManager, networkMetered: Boolean? = null,
    ): Result<PlaybackData> = PlaybackStreamFetcher.playerResponseForDownload(videoId, playlistId, audioQuality, connectivityManager, networkMetered)

    suspend fun playerResponseForMetadata(
        videoId: String, playlistId: String? = null, authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> = PlaybackStreamFetcher.playerResponseForMetadata(videoId, playlistId, authState)

    fun findFormat(
        playerResponse: PlayerResponse, audioQuality: AudioQuality, connectivityManager: ConnectivityManager, networkMetered: Boolean? = null,
    ): PlayerResponse.StreamingData.Format? = PlaybackFormatSelector.findFormat(playerResponse, audioQuality, connectivityManager, networkMetered)

    fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse, audioQuality: AudioQuality, networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> = PlaybackFormatSelector.selectAudioFormatCandidates(playerResponse, audioQuality, networkMetered)

    fun validateStatus(url: String): Boolean = PlaybackStreamValidator.validateStatus(url)
}
