/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback.recovery

import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import moe.rukamori.archivetune.utils.YTPlayerUtils

object PlaybackErrorClassifiers {
    val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)

    fun findRetryableStreamFailure(
        error: PlaybackException,
    ): HttpDataSource.InvalidResponseCodeException? {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is HttpDataSource.InvalidResponseCodeException &&
                throwable.responseCode in RETRYABLE_STREAM_RESPONSE_CODES
            ) {
                return throwable
            }
            throwable = throwable.cause
        }
        return null
    }

    fun isRetryableRemoteParserFailure(error: PlaybackException): Boolean {
        if (
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) {
            return true
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable.message?.contains("Skipping atom with length", ignoreCase = true) == true) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }

    fun isCacheCorruptionError(
        error: PlaybackException,
        isContentCached: Boolean,
    ): Boolean {
        val isIoError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        val isContainerParseError =
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

        if (!isIoError && !isContainerParseError) {
            return false
        }

        var throwable: Throwable? = error.cause
        while (throwable != null) {
            when {
                throwable is IllegalStateException || throwable is IllegalArgumentException -> {
                    if (throwable.stackTrace.any { it.className.startsWith("androidx.media3.extractor") }) {
                        return true
                    }
                }

                isContainerParseError && isContentCached && throwable is ParserException -> {
                    return true
                }

                isContainerParseError && isContentCached &&
                    throwable.message?.let {
                        it.contains("Invalid integer size", ignoreCase = true) ||
                            it.contains("Skipping atom with length", ignoreCase = true) ||
                            it.contains("contentIsMalformed=true", ignoreCase = true)
                    } == true -> {
                    return true
                }
            }
            throwable = throwable.cause
        }
        return false
    }

    fun hasAnyCachedData(
        mediaId: String,
        isFullyCached: Boolean,
        vararg caches: Cache?,
    ): Boolean {
        return isFullyCached || runCatching {
            caches.any { it?.getCachedSpans(mediaId)?.isNotEmpty() == true }
        }.getOrDefault(false)
    }

    fun findLoginRecoveryTarget(error: PlaybackException): String? {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is YTPlayerUtils.InvalidPlaybackLoginContextException) {
                return throwable.targetUrl
            }
            throwable = throwable.cause
        }
        return null
    }

    fun hasLoginRequiredException(error: PlaybackException): Boolean {
        var throwable: Throwable? = error.cause
        while (throwable != null) {
            if (throwable is YTPlayerUtils.LoginRequiredForPlaybackException) {
                return true
            }
            throwable = throwable.cause
        }
        return false
    }
}
