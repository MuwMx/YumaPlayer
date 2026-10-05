/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber

internal fun MusicService.resolveYtPlaybackResponse(
    mediaId: String,
    shouldBypassFlac: Boolean,
    isMeteredConnection: Boolean,
): YTPlayerUtils.PlaybackData {
    val playbackData =
        runBlocking(Dispatchers.IO) {
            retryWithoutPlaybackLoginContext {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = if (shouldBypassFlac) AudioQuality.LOW else audioQuality,
                    connectivityManager = connectivityManager,
                    preferredStreamClient = preferredStreamClient,
                    networkMetered = isMeteredConnection,
                )
            }.recoverCatching { youtubeFailure ->
                if (youtubeFailure !is YTPlayerUtils.BotDetectionPlaybackException) throw youtubeFailure

                Timber.tag("MusicService").w(
                    youtubeFailure,
                    "YouTube stream clients hit bot detection for %s; trying external audio fallback",
                    mediaId,
                )
                throw youtubeFailure
            }
        }.getOrElse { throwable ->
            when {
                throwable is YTPlayerUtils.InvalidPlaybackLoginContextException -> {
                    promptLoginRecovery(mediaId, throwable.targetUrl)
                    throw PlaybackException(
                        getString(R.string.playback_requires_youtube_music_login_refresh),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }

                throwable is YTPlayerUtils.LoginRequiredForPlaybackException -> {
                    throw PlaybackException(
                        getString(R.string.playback_requires_youtube_music_confirmation),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }

                throwable is YTPlayerUtils.BotDetectionPlaybackException -> {
                    throw PlaybackException(
                        getString(R.string.error_no_stream),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }

                throwable is YTPlayerUtils.BadStreamPlayerResponseException -> {
                    throw PlaybackException(
                        getString(R.string.error_no_stream),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }

                throwable is PlaybackException -> {
                    throw throwable
                }

                throwable.isNetworkConnectionFailure() -> {
                    throw PlaybackException(
                        getString(R.string.error_no_internet),
                        throwable,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    )
                }

                throwable.isRequestTimeout() -> {
                    throw PlaybackException(
                        getString(R.string.error_timeout),
                        throwable,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    )
                }

                else -> {
                    throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }
            }
        }

    return requireNotNull(playbackData) {
        getString(R.string.error_unknown)
    }
}

internal fun MusicService.resolveYtPlaybackDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    flacKey: String,
    networkCacheKey: String,
    shouldBypassFlac: Boolean,
    isMeteredConnection: Boolean,
    knownContentLength: Long?,
): DataSpec {
    val playbackData = resolveYtPlaybackResponse(
        mediaId = mediaId,
        shouldBypassFlac = shouldBypassFlac,
        isMeteredConnection = isMeteredConnection,
    )
    return persistPlaybackFormat(
        dataSpec = dataSpec,
        mediaId = mediaId,
        flacKey = flacKey,
        networkCacheKey = networkCacheKey,
        knownContentLength = knownContentLength,
        playbackData = playbackData,
    )
}
