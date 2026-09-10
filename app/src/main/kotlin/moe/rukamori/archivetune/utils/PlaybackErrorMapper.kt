/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.media3.common.PlaybackException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import timber.log.Timber
import java.util.Locale

typealias LoginRequiredForPlaybackException = YTPlayerUtils.LoginRequiredForPlaybackException
typealias InvalidPlaybackLoginContextException = YTPlayerUtils.InvalidPlaybackLoginContextException
typealias BotDetectionPlaybackException = YTPlayerUtils.BotDetectionPlaybackException
typealias BadStreamPlayerResponseException = YTPlayerUtils.BadStreamPlayerResponseException

object PlaybackErrorMapper {
    private const val logTag = "PlaybackErrorMapper"

    internal data class PlaybackGateFailure(
        val clientName: String,
        val status: String,
        val reason: String?,
    )

    fun isBotDetectionException(error: PlaybackException): Boolean {
        val message = error.message.orEmpty()
        if (isBotDetectionError(message)) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is BotDetectionPlaybackException) return true
            if (isBotDetectionError(cause.message.orEmpty())) return true
            cause = cause.cause
        }
        return false
    }

    fun isBadStreamPlayerResponseException(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is BadStreamPlayerResponseException) return true
            cause = cause.cause
        }
        return false
    }

    fun isJavaScriptPlayerExtractorFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (
                message.contains("deobfuscation", ignoreCase = true) ||
                message.contains("JavaScript player", ignoreCase = true) ||
                message.contains("base JavaScript player", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isInvalidPlaybackLoginContextFailure(error: Throwable): Boolean {
        val clientError = error as? ClientRequestException ?: return false
        if (clientError.response.status != HttpStatusCode.BadRequest) return false

        val message = clientError.message.orEmpty()
        if (!message.contains("/youtubei/v1/player", ignoreCase = true)) return false
        if (message.contains("Origin doesn't match Host", ignoreCase = true)) return false

        return message.contains("INVALID_ARGUMENT", ignoreCase = true) ||
            message.contains("invalid argument", ignoreCase = true)
    }

    fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            "confirm" in lower && "not a" in lower ||
            "not a robot" in lower ||
            "verify" in lower && "human" in lower
    }

    fun isLoginRecoveryError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "confirm your age" in lower ||
            "age-restricted" in lower ||
            "age restricted" in lower ||
            "inappropriate for some users" in lower ||
            "mature audiences" in lower ||
            "adult" in lower && "sign in" in lower ||
            "allow" in lower && "youtube music" in lower
    }

    fun throwInvalidPlaybackLoginContextIfNeeded(
        videoId: String,
        authState: PlaybackAuthState,
        failure: Throwable,
    ) {
        if (!authState.hasPlaybackLoginContext) return
        if (!isInvalidPlaybackLoginContextFailure(failure)) return

        Timber.tag(logTag).w(
            failure,
            "Detected invalid logged-in playback context for %s; requiring login refresh",
            videoId,
        )
        throw InvalidPlaybackLoginContextException(
            videoId = videoId,
            targetUrl = "https://music.youtube.com/watch?v=$videoId",
            cause = failure,
        )
    }

    fun getPlaybackPlayerResponseOrThrow(
        result: Result<PlayerResponse>,
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse {
        val failure = result.exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            throw failure
        }
        return result.getOrThrow()
    }

    fun getPlaybackPlayerResponseOrNull(
        result: Result<PlayerResponse>,
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse? {
        val failure = result.exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            return null
        }
        return result.getOrNull()
    }
}

internal fun Throwable.isJavaScriptPlayerExtractorFailure(): Boolean =
    PlaybackErrorMapper.isJavaScriptPlayerExtractorFailure(this)

internal fun Throwable.isInvalidPlaybackLoginContextFailure(): Boolean =
    PlaybackErrorMapper.isInvalidPlaybackLoginContextFailure(this)

internal fun Result<PlayerResponse>.getPlaybackPlayerResponseOrThrow(
    videoId: String,
    authState: PlaybackAuthState,
): PlayerResponse = PlaybackErrorMapper.getPlaybackPlayerResponseOrThrow(this, videoId, authState)

internal fun Result<PlayerResponse>.getPlaybackPlayerResponseOrNull(
    videoId: String,
    authState: PlaybackAuthState,
): PlayerResponse? = PlaybackErrorMapper.getPlaybackPlayerResponseOrNull(this, videoId, authState)
