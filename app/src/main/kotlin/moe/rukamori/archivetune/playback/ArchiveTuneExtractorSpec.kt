package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.moriextractor.ArchiveTuneExtractorException
import moe.rukamori.archivetune.utils.AuthScopedCacheValue

internal fun MusicService.resolveArchiveTuneExtractorDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
): DataSpec {
    val authState = YouTube.currentPlaybackAuthState()
    val authFingerprint = MusicService.ArchiveTuneExtractorCacheFingerprintPrefix + authState.fingerprint
    val userPoToken = authState.resolveExtractorPoToken()
    val userGvsToken = authState.resolveExtractorGvsToken()
    val userCookies = authState.resolveExtractorCookies()

    extractorPlaybackUrlCache[mediaId]
        ?.takeIf {
            it.isValidFor(
                authFingerprint = authFingerprint,
                minimumRemainingMs = 0L,
            )
        }?.let { cached ->
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            val targetDataKey = resolveTargetDataKey(mediaId, PlaybackSource.YT_MUSIC)
            val specWithKey = if (dataSpec.key != targetDataKey) dataSpec.buildUpon().setKey(targetDataKey).build() else dataSpec
            return specWithKey.withUri(cached.url.toUri())
        }

    val streamUrl =
        extractAudioUrl(
            mediaId = mediaId,
            userPoToken = userPoToken,
            cookies = userCookies,
            userGvsToken = userGvsToken,
        )

    val extractorCacheValue =
        AuthScopedCacheValue(
            url = streamUrl,
            expiresAtMs = System.currentTimeMillis() + MusicService.ArchiveTuneExtractorCacheTtlMs,
            authFingerprint = authFingerprint,
        )
    extractorPlaybackUrlCache[mediaId] = extractorCacheValue
    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
    val targetDataKey = resolveTargetDataKey(mediaId, PlaybackSource.YT_MUSIC)
    val specWithKey = if (dataSpec.key != targetDataKey) dataSpec.buildUpon().setKey(targetDataKey).build() else dataSpec
    return specWithKey.withUri(streamUrl.toUri())
}

private fun MusicService.extractAudioUrl(
    mediaId: String,
    userPoToken: String?,
    cookies: String?,
    userGvsToken: String?,
): String =
    runCatching {
        runBlocking(Dispatchers.IO) {
            streamingExtractionManager.extractAudioUrl(
                videoUrl = mediaId.toYouTubeWatchUrl(),
                userPoToken = userPoToken,
                cookies = cookies,
                userGvsToken = userGvsToken,
            )
        }
    }.getOrElse { throwable ->
        throw mapExtractorThrowable(throwable)
    }

private fun MusicService.mapExtractorThrowable(throwable: Throwable): PlaybackException =
    when {
        throwable.isNetworkConnectionFailure() -> {
            PlaybackException(
                getString(R.string.error_no_internet),
                throwable,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        }

        throwable.isRequestTimeout() -> {
            PlaybackException(
                getString(R.string.error_timeout),
                throwable,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            )
        }

        throwable is ArchiveTuneExtractorException -> {
            PlaybackException(
                getString(R.string.error_no_stream),
                throwable,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }

        throwable is PlaybackException -> {
            throwable
        }

        else -> {
            PlaybackException(
                getString(R.string.error_unknown),
                throwable,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }
    }
