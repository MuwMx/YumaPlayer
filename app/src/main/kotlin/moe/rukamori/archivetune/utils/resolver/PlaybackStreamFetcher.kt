/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import android.net.ConnectivityManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils.BotDetectionPlaybackException
import moe.rukamori.archivetune.utils.YTPlayerUtils.LoginRequiredForPlaybackException
import moe.rukamori.archivetune.utils.YTPlayerUtils.PlaybackData
import moe.rukamori.archivetune.utils.resolver.fetcher.PlaybackFallbackLadderResolver
import moe.rukamori.archivetune.utils.resolver.fetcher.PlaybackMetadataFetcher
import timber.log.Timber

object PlaybackStreamFetcher {
    private const val logTag = "PlaybackStreamFetcher"
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<PlaybackData>>>()

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [PlaybackClientSelector.MAIN_CLIENT].
     * Format & stream can be from [PlaybackClientSelector.MAIN_CLIENT] or [PlaybackClientSelector.STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        // if provided, this preference overrides ConnectivityManager.isActiveNetworkMetered
        networkMetered: Boolean? = null,
        pinnedFormat: PinnedStreamFormat? = null,
    ): Result<PlaybackData> {
        val coalesceKey = "$videoId:$audioQuality:${pinnedFormat?.itag}"
        val deferred = CompletableDeferred<Result<PlaybackData>>()
        val existing = inFlightRequests.putIfAbsent(coalesceKey, deferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val result =
                fetchPlayerResponseForPlayback(
                    videoId = videoId,
                    playlistId = playlistId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    preferredStreamClient = preferredStreamClient,
                    networkMetered = networkMetered,
                    pinnedFormat = pinnedFormat,
                )
            deferred.complete(result)
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            deferred.cancel(e)
            throw e
        } catch (e: Throwable) {
            deferred.complete(Result.failure(e))
            throw e
        } finally {
            inFlightRequests.remove(coalesceKey, deferred)
        }
    }

    private suspend fun fetchPlayerResponseForPlayback(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
        pinnedFormat: PinnedStreamFormat?,
    ): Result<PlaybackData> {
        return try {
            val attempts =
                when (audioQuality) {
                    AudioQuality.HIGHEST -> listOf(AudioQuality.HIGHEST, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.AUTO -> listOf(AudioQuality.AUTO, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.LOW -> listOf(AudioQuality.LOW, AudioQuality.HIGH, AudioQuality.AUTO)
                }.distinct()

            var lastError: Throwable? = null
            var didRefreshIpRotationAfterBotDetection = false
            for ((attemptIndex, attempt) in attempts.withIndex()) {
                val attemptResult =
                    try {
                        Result.success(
                            playerResponseForPlaybackOnce(
                                videoId = videoId,
                                playlistId = playlistId,
                                audioQuality = attempt,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = preferredStreamClient,
                                networkMetered = networkMetered,
                                pinnedFormat = pinnedFormat,
                            )
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                if (attemptResult.isSuccess) return Result.success(attemptResult.getOrThrow())
                lastError = attemptResult.exceptionOrNull()
                if (lastError is kotlinx.coroutines.CancellationException) {
                    throw lastError
                }
                if (
                    !didRefreshIpRotationAfterBotDetection &&
                    lastError is BotDetectionPlaybackException &&
                    PlaybackAuthCoordinator.refreshIpRotationForBotDetection(videoId, lastError)
                ) {
                    didRefreshIpRotationAfterBotDetection = true
                    val rotatedAttemptResult =
                        try {
                            Result.success(
                                playerResponseForPlaybackOnce(
                                    videoId = videoId,
                                    playlistId = playlistId,
                                    audioQuality = attempt,
                                    connectivityManager = connectivityManager,
                                    preferredStreamClient = preferredStreamClient,
                                    networkMetered = networkMetered,
                                    pinnedFormat = pinnedFormat,
                                )
                            )
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Result.failure(e)
                        }
                    if (rotatedAttemptResult.isSuccess) return Result.success(rotatedAttemptResult.getOrThrow())
                    lastError = rotatedAttemptResult.exceptionOrNull()
                    if (lastError is kotlinx.coroutines.CancellationException) {
                        throw lastError
                    }
                }

                val isTerminalLogin = lastError is LoginRequiredForPlaybackException ||
                    lastError is YTPlayerUtils.InvalidPlaybackLoginContextException

                if (isTerminalLogin) {
                    if (attemptIndex < attempts.lastIndex) {
                        if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                                "FALLBACK_LADDER_TERMINAL_LOGIN",
                                attemptId = moe.rukamori.archivetune.playback.PlaybackTiming.getAttemptId(videoId),
                                mediaId = videoId,
                                extra = "prevented_rerun=${attemptIndex + 1} remaining=${attempts.size - attemptIndex - 1} reason=${lastError.message.orEmpty()}",
                            )
                        }
                    }
                    break
                }

                if (lastError is BotDetectionPlaybackException) {
                    break
                }

                if (attemptIndex >= 1 && attemptIndex < attempts.lastIndex) {
                    if (moe.rukamori.archivetune.BuildConfig.DEBUG) {
                        moe.rukamori.archivetune.playback.PlaybackTiming.log(
                            "FALLBACK_LADDER_RETRY_BOUNDED",
                            attemptId = moe.rukamori.archivetune.playback.PlaybackTiming.getAttemptId(videoId),
                            mediaId = videoId,
                            extra = "prevented_rerun=${attemptIndex + 1} err=${lastError?.javaClass?.simpleName}",
                        )
                    }
                    break
                }
            }
            throw lastError ?: IllegalStateException("Failed to resolve stream")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun playerResponseForDownload(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> {
        return try {
            Timber.tag(logTag).i("Fetching download response for videoId: $videoId, playlistId: $playlistId")
            var lastError: Throwable? = null

            for (preferredStreamClient in PlaybackClientSelector.downloadPreferredStreamClientAttempts) {
                val attemptResult =
                    playerResponseForPlayback(
                        videoId = videoId,
                        playlistId = playlistId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = preferredStreamClient,
                        networkMetered = networkMetered,
                    )

                if (attemptResult.isSuccess) return Result.success(attemptResult.getOrThrow())

                lastError = attemptResult.exceptionOrNull()
                if (lastError is kotlinx.coroutines.CancellationException) {
                    throw lastError
                }
                Timber.tag(logTag).w(
                    lastError,
                    "Download stream resolution failed with preferred client %s for %s",
                    preferredStreamClient.name,
                    videoId,
                )
            }

            throw lastError ?: IllegalStateException("Failed to resolve download stream for $videoId")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    internal suspend fun playerResponseForPlaybackOnce(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
        pinnedFormat: PinnedStreamFormat? = null,
    ): PlaybackData {
        return PlaybackFallbackLadderResolver.resolve(
            videoId = videoId,
            playlistId = playlistId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager,
            preferredStreamClient = preferredStreamClient,
            networkMetered = networkMetered,
            pinnedFormat = pinnedFormat,
        )
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> {
        return PlaybackMetadataFetcher.fetchMetadataOnly(
            videoId = videoId,
            playlistId = playlistId,
            authState = authState,
        )
    }
}
