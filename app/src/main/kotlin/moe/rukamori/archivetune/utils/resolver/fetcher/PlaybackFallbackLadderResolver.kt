/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver.fetcher

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.PlaybackErrorMapper
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.StreamUrlCache
import moe.rukamori.archivetune.utils.YTPlayerUtils.BadStreamPlayerResponseException
import moe.rukamori.archivetune.utils.YTPlayerUtils.BotDetectionPlaybackException
import moe.rukamori.archivetune.utils.YTPlayerUtils.LoginRequiredForPlaybackException
import moe.rukamori.archivetune.utils.YTPlayerUtils.PlaybackData
import moe.rukamori.archivetune.utils.getPlaybackPlayerResponseOrNull
import moe.rukamori.archivetune.utils.resolver.PlaybackAuthCoordinator
import moe.rukamori.archivetune.utils.resolver.PinnedStreamFormat
import moe.rukamori.archivetune.utils.resolver.PlaybackClientSelector
import moe.rukamori.archivetune.utils.resolver.PlaybackStreamUrlDecipher
import timber.log.Timber

internal object PlaybackFallbackLadderResolver {
    private const val TAG = "PlaybackFallbackLadderResolver"
    private const val DISABLE_VISIONOS_FAST_START = false

    suspend fun resolve(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
        pinnedFormat: PinnedStreamFormat?,
    ): PlaybackData {
        Timber.tag(TAG).i("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        val fetchAttemptId = if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.getAttemptId(videoId)
        } else 0L
        val fetchStartNanos = if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()
        } else 0L

        var authState = YouTube.currentPlaybackAuthState()
        val hasLoginCookie = authState.hasLoginCookie
        var canUseLoggedInPlayback = authState.hasPlaybackLoginContext
        if (!canUseLoggedInPlayback) {
            if (hasLoginCookie) {
                Timber.tag(TAG).w(
                    "Ignoring incomplete login context for %s because dataSyncId is missing; falling back to visitorData playback",
                    videoId,
                )
            }
            authState =
                PlaybackAuthCoordinator.ensureVisitorDataReady(
                    videoId = videoId,
                    authState = authState,
                    reason = if (hasLoginCookie) "cookie-only playback fallback" else "anonymous playback bootstrap",
                )
        }
        val sessionId = authState.visitorData
        val authStatus =
            when {
                canUseLoggedInPlayback -> "Logged in"
                hasLoginCookie -> "Cookie-only"
                else -> "Not logged in"
            }
        Timber.tag(TAG).v("Session authentication status: $authStatus (sessionId=${sessionId.orEmpty()})")

        if (!(moe.rukamori.archivetune.BuildConfig.DEBUG && DISABLE_VISIONOS_FAST_START)) {
            val fastPathData =
                VisionOsFastPathResolver.tryVisionOsFastPath(
                    videoId = videoId,
                    playlistId = playlistId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    networkMetered = networkMetered,
                    pinnedFormat = pinnedFormat,
                    signatureTimestamp = null,
                    authState = authState,
                    canUseLoggedInPlayback = canUseLoggedInPlayback,
                    fetchAttemptId = fetchAttemptId,
                    fetchStartNanos = fetchStartNanos,
                )
            if (fastPathData != null) {
                return fastPathData
            }
        }

        val signatureTimestamp = PlaybackStreamUrlDecipher.getSignatureTimestampOrNull(videoId)
        Timber.tag(TAG).v("Signature timestamp: $signatureTimestamp")

        val metadataResult =
            PlaybackMetadataFetcher.fetchMetadataForPlayback(
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                initialAuthState = authState,
                initialCanUseLoggedInPlayback = canUseLoggedInPlayback,
                fetchAttemptId = fetchAttemptId,
            )
        var metadataPlayerResponse = metadataResult.playerResponse
        var expectedDurationMs = metadataResult.expectedDurationMs
        authState = metadataResult.authState
        canUseLoggedInPlayback = metadataResult.canUseLoggedInPlayback
        val metadataClient = metadataResult.metadataClient

        val preferredYouTubeClient = PlaybackClientSelector.resolvePreferredPlaybackClient(preferredStreamClient, authState)
        if (
            preferredYouTubeClient == WEB_REMIX &&
            preferredStreamClient == PlayerStreamClient.ANDROID_VR &&
            canUseLoggedInPlayback
        ) {
            Timber.tag(TAG).i(
                "Promoting playback client to WEB_REMIX for %s because login and Web PoToken playback are available",
                videoId,
            )
        }

        val orderedClients = PlaybackClientSelector.buildStreamClientOrder(preferredStreamClient, authState)
        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "STREAM_CLIENT_ORDER",
                attemptId = fetchAttemptId,
                mediaId = videoId,
                extra = "count=${orderedClients.size} first=${orderedClients.firstOrNull()?.let { PlaybackClientSelector.describeClient(it) }}",
            )
        }
        val streamClients =
            orderedClients.filterNot { client ->
                val blocked =
                    StreamUrlCache.isStreamClientTemporarilyBlocked(
                        videoId = videoId,
                        clientKey = StreamClientUtils.buildClientKey(client),
                        authFingerprint = authState.fingerprint,
                    )
                if (blocked) {
                    Timber.tag(TAG).w("Temporarily blocked stream client for $videoId: ${PlaybackClientSelector.describeClient(client)}")
                }
                blocked
            }

        val botDetectedClients = mutableSetOf<String>()
        var gateFailure: PlaybackErrorMapper.PlaybackGateFailure? = null

        fun authMode(): String =
            when {
                canUseLoggedInPlayback -> "logged-in"
                hasLoginCookie -> "cookie-only"
                else -> "visitor"
            }

        var streamPlayerResponse: PlayerResponse? = null
        var streamClientUsed: YouTubeClient? = null
        var resolvedCandidate: ResolvedStreamCandidate? = null
        var didRepairAuthAfterBotDetection = false

        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered

        for ((index, candidateClient) in streamClients.withIndex()) {
            var client = candidateClient
            resolvedCandidate = null
            streamClientUsed = null
            streamPlayerResponse = null
            val clientAttemptStartNanos = if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()
            } else 0L
            if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                moe.rukamori.archivetune.playback.PlaybackTiming.log(
                    "STREAM_CLIENT_ATTEMPT_START",
                    attemptId = fetchAttemptId,
                    mediaId = videoId,
                    extra = "try=${index + 1}/${streamClients.size} client=${PlaybackClientSelector.describeClient(client)}",
                )
            }

            Timber.tag(TAG).v(
                "Trying ${if (client == PlaybackClientSelector.MAIN_CLIENT) "MAIN_CLIENT" else "fallback client"} ${index + 1}/${streamClients.size}: ${PlaybackClientSelector.describeClient(
                    client,
                )}",
            )

            if (client != PlaybackClientSelector.MAIN_CLIENT && client.loginRequired && !canUseLoggedInPlayback) {
                Timber.tag(TAG).i("Skipping client ${PlaybackClientSelector.describeClient(client)} - requires login but auth mode is ${authMode()}")
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log(
                        "STREAM_CLIENT_SKIPPED",
                        attemptId = fetchAttemptId,
                        mediaId = videoId,
                        startNanos = clientAttemptStartNanos,
                        extra = "try=${index + 1} client=${PlaybackClientSelector.describeClient(client)} reason=login-required",
                    )
                }
                continue
            }

            streamPlayerResponse =
                if (client == metadataClient) {
                    metadataPlayerResponse
                } else {
                    Timber.tag(TAG).i("Fetching player response for fallback client: ${PlaybackClientSelector.describeClient(client)}")
                    YouTube
                        .player(
                            videoId = videoId,
                            playlistId = playlistId,
                            client = client,
                            signatureTimestamp = signatureTimestamp,
                            setLogin = canUseLoggedInPlayback,
                            authState = authState,
                        ).getPlaybackPlayerResponseOrNull(videoId, authState)
                }

            if (streamPlayerResponse == null) {
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log(
                        "STREAM_CLIENT_FAILED",
                        attemptId = fetchAttemptId,
                        mediaId = videoId,
                        startNanos = clientAttemptStartNanos,
                        extra = "try=${index + 1} client=${PlaybackClientSelector.describeClient(client)} reason=empty-response",
                    )
                }
                continue
            }

            var playabilityStatus = streamPlayerResponse.playabilityStatus
            if (playabilityStatus.status != "OK") {
                var reason = playabilityStatus.reason.orEmpty()
                var isLoginRecovery = PlaybackErrorMapper.isLoginRecoveryError(playabilityStatus.status, reason)
                var isBotDetection = PlaybackErrorMapper.isBotDetectionError(reason)

                if (isBotDetection && !didRepairAuthAfterBotDetection) {
                    val botRepairStartNanos = if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                        moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()
                    } else 0L
                    if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "BOT_REPAIR_START",
                            attemptId = fetchAttemptId,
                            mediaId = videoId,
                            extra = "try=${index + 1} client=${PlaybackClientSelector.describeClient(client)}",
                        )
                    }
                    val repairedAuthState =
                        PlaybackAuthCoordinator.repairAuthStateAfterBotDetection(
                            videoId = videoId,
                            authState = authState,
                            reason = "bot-detection recovery on ${client.clientName}",
                        )
                    val shouldUseWebRemix =
                        repairedAuthState.hasPlaybackLoginContext &&
                            PlaybackAuthCoordinator.hasCompleteWebPlaybackPoToken(repairedAuthState) &&
                            client != WEB_REMIX

                    if (repairedAuthState.fingerprint != authState.fingerprint || shouldUseWebRemix) {
                        authState = repairedAuthState
                        canUseLoggedInPlayback = authState.hasPlaybackLoginContext
                        client = if (shouldUseWebRemix) WEB_REMIX else client
                        didRepairAuthAfterBotDetection = true
                        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                                "BOT_REPAIR_END",
                                attemptId = fetchAttemptId,
                                mediaId = videoId,
                                startNanos = botRepairStartNanos,
                                extra = "try=${index + 1} client=${PlaybackClientSelector.describeClient(client)}",
                            )
                        }
                        Timber.tag(TAG).i(
                            "Retrying %s for %s after repairing playback auth",
                            PlaybackClientSelector.describeClient(client),
                            videoId,
                        )
                        streamPlayerResponse =
                            YouTube
                                .player(
                                    videoId = videoId,
                                    playlistId = playlistId,
                                    client = client,
                                    signatureTimestamp = signatureTimestamp,
                                    setLogin = canUseLoggedInPlayback,
                                    authState = authState,
                                ).getPlaybackPlayerResponseOrNull(videoId, authState)

                        if (streamPlayerResponse == null) continue

                        playabilityStatus = streamPlayerResponse.playabilityStatus
                        reason = playabilityStatus.reason.orEmpty()
                        isLoginRecovery = PlaybackErrorMapper.isLoginRecoveryError(playabilityStatus.status, reason)
                        isBotDetection = PlaybackErrorMapper.isBotDetectionError(reason)
                    }
                }

                if (playabilityStatus.status == "OK") {
                    if (client == metadataClient) {
                        metadataPlayerResponse = streamPlayerResponse
                        expectedDurationMs =
                            metadataPlayerResponse.videoDetails
                                ?.lengthSeconds
                                ?.toLongOrNull()
                                ?.takeIf { it > 0 }
                                ?.times(1000L)
                    }
                    Timber.tag(TAG).i(
                        "Recovered playback with %s after auth repair",
                        PlaybackClientSelector.describeClient(client),
                    )
                } else {
                    val statusMessage =
                        "Player response status not OK for ${PlaybackClientSelector.describeClient(client)} [auth=${authMode()}]: " +
                            "${playabilityStatus.status}, reason: $reason, loginRecovery: $isLoginRecovery, botDetection: $isBotDetection"
                    if (isLoginRecovery) {
                        Timber.tag(TAG).i(statusMessage)
                    } else {
                        Timber.tag(TAG).w(statusMessage)
                    }
                    if (isLoginRecovery) {
                        gateFailure =
                            PlaybackErrorMapper.PlaybackGateFailure(
                                clientName = PlaybackClientSelector.describeClient(client),
                                status = playabilityStatus.status,
                                reason = playabilityStatus.reason,
                            )
                    } else if (isBotDetection) {
                        botDetectedClients.add(PlaybackClientSelector.describeClient(client))
                    }
                    if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "STREAM_CLIENT_FAILED",
                            attemptId = fetchAttemptId,
                            mediaId = videoId,
                            startNanos = clientAttemptStartNanos,
                            extra = "try=${index + 1} client=${PlaybackClientSelector.describeClient(client)} reason=status-${playabilityStatus.status}",
                        )
                    }
                    continue
                }
            }

            val candidate =
                PlaybackCandidateUrlResolver.resolveClientCandidate(
                    videoId = videoId,
                    client = client,
                    streamPlayerResponse = streamPlayerResponse,
                    audioQuality = audioQuality,
                    isMetered = isMetered,
                    pinnedFormat = pinnedFormat,
                    canUseLoggedInPlayback = canUseLoggedInPlayback,
                    expectedDurationMs = expectedDurationMs,
                    authState = authState,
                    fetchAttemptId = fetchAttemptId,
                    clientAttemptStartNanos = clientAttemptStartNanos,
                    tryIndex = index + 1,
                )

            if (candidate != null) {
                resolvedCandidate = candidate
                streamClientUsed = client
                break
            }
        }

        if (streamPlayerResponse == null) {
            if (botDetectedClients.isNotEmpty()) {
                Timber.tag(TAG).e("Bot detection triggered on clients: $botDetectedClients - all clients failed")
                throw BotDetectionPlaybackException(
                    videoId = videoId,
                    clients = botDetectedClients.toSet(),
                )
            }
            gateFailure?.let { failure ->
                Timber.tag(TAG).w(
                    "Playback requires login recovery for $videoId via ${failure.clientName} (${failure.status}): ${failure.reason.orEmpty()}",
                )
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log(
                        "LOGIN_REQUIRED_RESOLVER_BUILT",
                        attemptId = fetchAttemptId,
                        mediaId = videoId,
                        extra = "status=${failure.status} reason=${failure.reason.orEmpty()}",
                    )
                }
                throw LoginRequiredForPlaybackException(
                    videoId = videoId,
                    targetUrl = "https://music.youtube.com/watch?v=$videoId",
                    reason = failure.reason,
                )
            }
            Timber.tag(TAG).e("Bad stream player response - all clients failed")
            throw BadStreamPlayerResponseException(videoId)
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val status = streamPlayerResponse.playabilityStatus.status
            val errorReason = streamPlayerResponse.playabilityStatus.reason.orEmpty()

            if (PlaybackErrorMapper.isBotDetectionError(errorReason)) {
                botDetectedClients.add(streamClientUsed?.let { PlaybackClientSelector.describeClient(it) } ?: "last client")
                throw BotDetectionPlaybackException(
                    videoId = videoId,
                    clients = botDetectedClients.toSet(),
                )
            }

            if (PlaybackErrorMapper.isLoginRecoveryError(status, errorReason)) {
                Timber.tag(TAG).w("Playback requires login recovery for $videoId: $errorReason")
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log(
                        "LOGIN_REQUIRED_RESOLVER_BUILT",
                        attemptId = fetchAttemptId,
                        mediaId = videoId,
                        extra = "status=$status reason=$errorReason",
                    )
                }
                throw LoginRequiredForPlaybackException(
                    videoId = videoId,
                    targetUrl = "https://music.youtube.com/watch?v=$videoId",
                    reason = errorReason,
                )
            }

            if (botDetectedClients.isNotEmpty()) {
                Timber.tag(TAG).e("Bot detection triggered on clients: $botDetectedClients")
                throw BotDetectionPlaybackException(
                    videoId = videoId,
                    clients = botDetectedClients.toSet(),
                )
            }

            gateFailure?.let { failure ->
                Timber.tag(TAG).w(
                    "Playback requires login recovery for $videoId via earlier client ${failure.clientName} (${failure.status}): ${failure.reason.orEmpty()}",
                )
                if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                    moe.rukamori.archivetune.playback.PlaybackTiming.log(
                        "LOGIN_REQUIRED_RESOLVER_BUILT",
                        attemptId = fetchAttemptId,
                        mediaId = videoId,
                        extra = "status=${failure.status} reason=${failure.reason.orEmpty()}",
                    )
                }
                throw LoginRequiredForPlaybackException(
                    videoId = videoId,
                    targetUrl = "https://music.youtube.com/watch?v=$videoId",
                    reason = failure.reason,
                )
            }

            Timber.tag(TAG).e("Playability status not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }

        return PlaybackCandidateUrlResolver.assemblePlaybackData(
            videoId = videoId,
            resolvedCandidate = resolvedCandidate,
            streamPlayerResponse = streamPlayerResponse,
            metadataPlayerResponse = metadataPlayerResponse,
            audioQuality = audioQuality,
            authState = authState,
            fetchAttemptId = fetchAttemptId,
            fetchStartNanos = fetchStartNanos,
        )
    }
}
