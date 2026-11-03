/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver.fetcher

import android.net.ConnectivityManager
import moe.rukamori.archivetune.cipher.CipherTrace
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.VISIONOS
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.PlaybackErrorMapper
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.YTPlayerUtils.PlaybackData
import moe.rukamori.archivetune.utils.getPlaybackPlayerResponseOrNull
import moe.rukamori.archivetune.utils.resolver.PlaybackClientSelector
import moe.rukamori.archivetune.utils.resolver.PlaybackFormatSelector
import moe.rukamori.archivetune.utils.resolver.PinnedStreamFormat
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import timber.log.Timber

internal object VisionOsFastPathResolver {
    private const val TAG = "VisionOsFastPathResolver"

    suspend fun tryVisionOsFastPath(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean?,
        pinnedFormat: PinnedStreamFormat?,
        signatureTimestamp: Int?,
        authState: PlaybackAuthState,
        canUseLoggedInPlayback: Boolean,
        fetchAttemptId: Long,
        fetchStartNanos: Long,
    ): PlaybackData? {
        val fastPathStartNanos = moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()
        moe.rukamori.archivetune.playback.PlaybackTiming.log(
            "FAST_PATH_START",
            attemptId = fetchAttemptId,
            mediaId = videoId,
            startNanos = fastPathStartNanos,
            extra = "status=START client=VISIONOS",
        )

        var fallbackReason = "unknown"
        try {
            val response =
                YouTube
                    .player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = VISIONOS,
                        signatureTimestamp = signatureTimestamp,
                        setLogin = canUseLoggedInPlayback,
                        authState = authState,
                    ).getPlaybackPlayerResponseOrNull(videoId, authState)

            if (response == null) {
                fallbackReason = "null_response"
            } else if (response.playabilityStatus.status != "OK") {
                val reason = response.playabilityStatus.reason.orEmpty()
                fallbackReason = when {
                    PlaybackErrorMapper.isBotDetectionError(reason) -> "bot_detection"
                    PlaybackErrorMapper.isLoginRecoveryError(reason) -> "login_recovery"
                    else -> "status_${response.playabilityStatus.status}"
                }
            } else if ((response.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0L) <= 0L) {
                fallbackReason = "invalid_duration"
            } else {
                val expectedDurationMs = (response.videoDetails?.lengthSeconds?.toLongOrNull() ?: 0L) * 1000L
                val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
                val candidates =
                    PlaybackFormatSelector.selectAudioFormatCandidates(
                        response,
                        audioQuality,
                        isMetered,
                        pinnedFormat,
                    )

                if (candidates.isEmpty()) {
                    fallbackReason = "no_audio_candidates"
                } else {
                    var selectedFormat: PlayerResponse.StreamingData.Format? = null
                    var selectedUrl: String? = null

                    for (candidate in candidates) {
                        if (PlaybackFormatSelector.isLikelyPreview(candidate, expectedDurationMs)) continue
                        if (PlaybackFormatSelector.shouldSkipCipheredWebCandidate(VISIONOS, candidate, authState)) continue

                        val cached = StreamUrlCache.getValidCachedStreamUrl(videoId, authState.fingerprint)
                        val candidateUrl =
                            if (cached != null && cached.itag == candidate.itag) {
                                cached.url
                            } else {
                                PlaybackStreamUrlDecipher.findUrlOrNull(candidate, videoId, VISIONOS, authState)
                            } ?: continue

                        selectedFormat = candidate
                        selectedUrl = candidateUrl
                        break
                    }

                    if (selectedFormat == null || selectedUrl == null) {
                        fallbackReason = "no_playable_url"
                    } else {
                        val streamExpiresInSeconds =
                            StreamUrlCache.resolveExpireSeconds(
                                apiExpire = response.streamingData?.expiresInSeconds,
                                streamUrl = selectedUrl,
                            )

                        StreamUrlCache.putCachedStreamUrl(
                            mediaId = videoId,
                            url = selectedUrl,
                            itag = selectedFormat.itag,
                            expiresInSeconds = streamExpiresInSeconds,
                            authFingerprint = authState.fingerprint,
                        )

                        val formatType = when {
                            selectedFormat.url != null -> "DIRECT"
                            selectedFormat.signatureCipher != null -> "SIGNATURE_CIPHER"
                            else -> "OTHER"
                        }
                        CipherTrace.log("VISIONOS_SUCCESS", "itag=${selectedFormat.itag} format=$formatType")

                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "FAST_PATH_SUCCESS",
                            attemptId = fetchAttemptId,
                            mediaId = videoId,
                            startNanos = fastPathStartNanos,
                            extra = "status=OK itag=${selectedFormat.itag}",
                        )
                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "FAST_PATH_TOTAL",
                            attemptId = fetchAttemptId,
                            mediaId = videoId,
                            startNanos = fastPathStartNanos,
                            extra = "status=SUCCESS",
                        )
                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "FINAL_CLIENT_USED",
                            attemptId = fetchAttemptId,
                            mediaId = videoId,
                            startNanos = fetchStartNanos,
                            extra = "client=${PlaybackClientSelector.describeClient(VISIONOS)} itag=${selectedFormat.itag}",
                        )

                        return PlaybackData(
                            response.playerConfig?.audioConfig,
                            response.videoDetails,
                            response.playbackTracking,
                            selectedFormat,
                            selectedUrl,
                            streamExpiresInSeconds,
                            authState.fingerprint,
                        )
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.tag(TAG).w(e, "VISIONOS fast path encountered unexpected error for %s", videoId)
            fallbackReason = "exception_${e.javaClass.simpleName}"
        }

        moe.rukamori.archivetune.playback.PlaybackTiming.log(
            "FAST_PATH_FALLBACK",
            attemptId = fetchAttemptId,
            mediaId = videoId,
            startNanos = fastPathStartNanos,
            extra = "status=FALLBACK reason=$fallbackReason",
        )
        moe.rukamori.archivetune.playback.PlaybackTiming.log(
            "FAST_PATH_TOTAL",
            attemptId = fetchAttemptId,
            mediaId = videoId,
            startNanos = fastPathStartNanos,
            extra = "status=FALLBACK",
        )
        return null
    }
}
