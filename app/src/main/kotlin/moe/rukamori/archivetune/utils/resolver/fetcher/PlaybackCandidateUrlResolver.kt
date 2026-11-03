/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver.fetcher

import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.YTPlayerUtils.PlaybackData
import moe.rukamori.archivetune.utils.resolver.PlaybackClientSelector
import moe.rukamori.archivetune.utils.resolver.PlaybackFormatSelector
import moe.rukamori.archivetune.utils.resolver.PinnedStreamFormat
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import timber.log.Timber

internal data class ResolvedStreamCandidate(
    val format: PlayerResponse.StreamingData.Format,
    val streamUrl: String,
    val streamExpiresInSeconds: Int,
    val client: YouTubeClient,
)

internal object PlaybackCandidateUrlResolver {
    private const val TAG = "PlaybackCandidateUrlResolver"

    suspend fun resolveClientCandidate(
        videoId: String,
        client: YouTubeClient,
        streamPlayerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        isMetered: Boolean,
        pinnedFormat: PinnedStreamFormat?,
        canUseLoggedInPlayback: Boolean,
        expectedDurationMs: Long?,
        authState: PlaybackAuthState,
        fetchAttemptId: Long,
        clientAttemptStartNanos: Long,
        tryIndex: Int,
    ): ResolvedStreamCandidate? {
        val candidates =
            PlaybackFormatSelector.selectAudioFormatCandidates(
                streamPlayerResponse,
                audioQuality,
                isMetered,
                pinnedFormat,
            )

        if (candidates.isEmpty()) {
            if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                moe.rukamori.archivetune.playback.PlaybackTiming.log(
                    "STREAM_CLIENT_FAILED",
                    attemptId = fetchAttemptId,
                    mediaId = videoId,
                    startNanos = clientAttemptStartNanos,
                    extra = "try=$tryIndex client=${PlaybackClientSelector.describeClient(client)} reason=no-candidates",
                )
            }
            return null
        }

        var selectedFormat: PlayerResponse.StreamingData.Format? = null
        var selectedUrl: String? = null

        for (candidate in candidates) {
            if (canUseLoggedInPlayback && expectedDurationMs != null && PlaybackFormatSelector.isLikelyPreview(candidate, expectedDurationMs)) continue
            if (PlaybackFormatSelector.shouldSkipCipheredWebCandidate(client, candidate, authState)) continue

            val cached = StreamUrlCache.getValidCachedStreamUrl(videoId, authState.fingerprint)
            val candidateUrl =
                if (cached != null && cached.itag == candidate.itag) {
                    cached.url
                } else {
                    PlaybackStreamUrlDecipher.findUrlOrNull(candidate, videoId, client, authState)
                } ?: continue

            selectedFormat = candidate
            selectedUrl = candidateUrl
            break
        }

        if (selectedFormat == null || selectedUrl == null) {
            Timber.tag(TAG).w(
                "No playable stream candidate resolved for %s at quality %s after checking %d formats",
                PlaybackClientSelector.describeClient(client),
                audioQuality,
                candidates.size,
            )
            if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                moe.rukamori.archivetune.playback.PlaybackTiming.log(
                    "STREAM_CLIENT_FAILED",
                    attemptId = fetchAttemptId,
                    mediaId = videoId,
                    startNanos = clientAttemptStartNanos,
                    extra = "try=$tryIndex client=${PlaybackClientSelector.describeClient(client)} reason=no-url formats=${candidates.size}",
                )
            }
            return null
        }

        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "STREAM_CLIENT_SUCCESS",
                attemptId = fetchAttemptId,
                mediaId = videoId,
                startNanos = clientAttemptStartNanos,
                extra = "try=$tryIndex client=${PlaybackClientSelector.describeClient(client)} itag=${selectedFormat.itag}",
            )
        }

        val streamExpiresInSeconds =
            StreamUrlCache.resolveExpireSeconds(
                apiExpire = streamPlayerResponse.streamingData?.expiresInSeconds,
                streamUrl = selectedUrl,
            )

        Timber.tag(TAG).i("Format found: ${selectedFormat.mimeType}, bitrate: ${selectedFormat.bitrate}")
        Timber.tag(TAG).v("Stream expires in: $streamExpiresInSeconds seconds")

        StreamUrlCache.lastSuccessfulClientKey = StreamClientUtils.buildClientKey(client)

        return ResolvedStreamCandidate(
            format = selectedFormat,
            streamUrl = selectedUrl,
            streamExpiresInSeconds = streamExpiresInSeconds,
            client = client,
        )
    }

    fun assemblePlaybackData(
        videoId: String,
        resolvedCandidate: ResolvedStreamCandidate?,
        streamPlayerResponse: PlayerResponse?,
        metadataPlayerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        authState: PlaybackAuthState,
        fetchAttemptId: Long,
        fetchStartNanos: Long,
    ): PlaybackData {
        if (resolvedCandidate == null) {
            Timber.tag(TAG).e(
                "Could not find suitable format for quality: $audioQuality. Available formats from last client: ${streamPlayerResponse?.streamingData?.adaptiveFormats?.filter {
                    it.isAudio
                }?.map { "${it.mimeType} @ ${it.bitrate}bps (itag: ${it.itag})" }}",
            )
            throw Exception("Could not find format for quality: $audioQuality")
        }

        val format = resolvedCandidate.format
        val streamUrl = resolvedCandidate.streamUrl
        val streamExpiresInSeconds = resolvedCandidate.streamExpiresInSeconds
        val resolvedStreamClient = resolvedCandidate.client

        Timber.tag(TAG).i("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")

        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "FINAL_CLIENT_USED",
                attemptId = fetchAttemptId,
                mediaId = videoId,
                startNanos = fetchStartNanos,
                extra = "client=${PlaybackClientSelector.describeClient(resolvedStreamClient)} itag=${format.itag}",
            )
        }

        StreamUrlCache.putCachedStreamUrl(
            mediaId = videoId,
            url = streamUrl,
            itag = format.itag,
            expiresInSeconds = streamExpiresInSeconds,
            authFingerprint = authState.fingerprint,
        )

        val finalResponse = streamPlayerResponse ?: metadataPlayerResponse
        return PlaybackData(
            finalResponse.playerConfig?.audioConfig,
            finalResponse.videoDetails,
            finalResponse.playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            authState.fingerprint,
        )
    }
}
