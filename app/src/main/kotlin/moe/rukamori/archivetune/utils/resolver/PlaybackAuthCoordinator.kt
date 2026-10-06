/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.utils.BotDetectionPlaybackException
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

object PlaybackAuthCoordinator {
    private const val logTag = "PlaybackAuthCoordinator"

    suspend fun recoverFromBadStreamPlayerResponse(videoId: String) {
        val authState = YouTube.currentPlaybackAuthState()
        val refreshedAuthState =
            ensureVisitorDataReady(
                videoId = videoId,
                authState = authState,
                forceRefresh = true,
                reason = "all stream clients failed",
            )
        if (refreshedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = refreshedAuthState
        }
        StreamUrlCache.clearPlaybackAuthCaches()
    }

    internal suspend fun ensureVisitorDataReady(
        videoId: String,
        authState: PlaybackAuthState,
        forceRefresh: Boolean = false,
        reason: String,
    ): PlaybackAuthState {
        if (!forceRefresh) {
            authState.visitorData
                ?.takeIf { it.isNotBlank() }
                ?.let { return authState }
        }

        val action = if (forceRefresh) "Refreshing" else "Fetching"
        Timber.tag(logTag).i("%s visitorData for %s (%s)", action, videoId, reason)

        val refreshedVisitorData =
            YouTube
                .visitorData()
                .onFailure {
                    Timber.tag(logTag).e(it, "Failed to refresh visitorData for $videoId")
                    reportException(it)
                }.getOrNull()
                ?.takeIf { it.isNotBlank() }

        if (refreshedVisitorData != null) {
            YouTube.visitorData = refreshedVisitorData
            return authState.copy(visitorData = refreshedVisitorData).normalized()
        }

        return authState
    }

    internal suspend fun repairAuthStateAfterBotDetection(
        videoId: String,
        authState: PlaybackAuthState,
        reason: String,
    ): PlaybackAuthState {
        var repairedAuthState = authState

        if (authState.hasLoginCookie) {
            val activeChannel =
                YouTube
                    .accountChannels()
                    .onFailure {
                        Timber.tag(logTag).w(it, "Failed to refresh playback account channel for $videoId")
                        reportException(it)
                    }.getOrNull()
                    ?.let { channels ->
                        channels.firstOrNull { it.isSelected } ?: channels.firstOrNull()
                    }

            val refreshedDataSyncId = activeChannel?.dataSyncId?.takeIf { it.isNotBlank() }
            if (refreshedDataSyncId != null && refreshedDataSyncId != repairedAuthState.dataSyncId) {
                Timber.tag(logTag).i("Refreshed playback dataSyncId for %s after bot detection", videoId)
                repairedAuthState = repairedAuthState.copy(dataSyncId = refreshedDataSyncId).normalized()
            }
        }

        if (
            repairedAuthState.visitorData.isNullOrBlank() ||
            !hasCompleteWebPlaybackPoToken(repairedAuthState)
        ) {
            repairedAuthState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = repairedAuthState,
                    forceRefresh = true,
                    reason = reason,
                )
        }

        if (repairedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = repairedAuthState
            StreamUrlCache.clearPlaybackAuthCaches()
        }

        return repairedAuthState
    }

    fun shouldPreferWebRemixForLoggedInPlayback(
        preferredStreamClient: PlayerStreamClient,
        isLoggedIn: Boolean,
        webClientPoTokenEnabled: Boolean,
        hasPlayerPoToken: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        preferredStreamClient == PlayerStreamClient.ANDROID_VR &&
            isLoggedIn &&
            webClientPoTokenEnabled &&
            hasPlayerPoToken &&
            hasGvsPoToken

    internal fun hasCompleteWebPlaybackPoToken(authState: PlaybackAuthState): Boolean =
        authState.webClientPoTokenEnabled &&
            !authState.resolvePlayerPoToken(WEB_REMIX).isNullOrBlank() &&
            !authState.resolveGvsPoToken(WEB_REMIX).isNullOrBlank()

    fun shouldSkipCipheredWebPlaybackCandidate(
        webClientPoTokenEnabled: Boolean,
        isWebClient: Boolean,
        isCiphered: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        webClientPoTokenEnabled &&
            isWebClient &&
            isCiphered &&
            !hasGvsPoToken

    internal suspend fun refreshIpRotationForBotDetection(
        videoId: String,
        failure: BotDetectionPlaybackException?,
    ): Boolean {
        if (failure == null) return false
        if (YouTube.ipRotationActiveCount.value <= 0) return false

        return runCatching {
            Timber.tag(logTag).w(
                failure,
                "Refreshing IP rotation after YouTube bot detection blocked playback for %s",
                videoId,
            )
            YouTube.refreshIpRotation()
            StreamUrlCache.clearPlaybackAuthCaches()
        }.onFailure {
            Timber.tag(logTag).w(it, "Failed to refresh IP rotation after bot detection for %s", videoId)
            reportException(it)
        }.isSuccess
    }
}
