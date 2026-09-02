package moe.rukamori.archivetune.playback

import android.net.NetworkCapabilities
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.moriextractor.ArchiveTuneExtractorException
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

internal fun MusicService.resolvePlaybackDataSpec(
    dataSpec: DataSpec,
    allowCacheShortCircuit: Boolean,
): DataSpec {
    if (dataSpec.uri.shouldBypassYouTubeResolver()) {
        return dataSpec
    }
    val mediaId = dataSpec.key ?: return dataSpec
    val storedFormat =
        runBlocking(Dispatchers.IO) {
            database.format(mediaId).first()
        }
    storedFormat?.let { format ->
        audioNormalizationFactorCache[mediaId] = calculateAudioNormalizationFactor(format, normalizeAudio = true)
    }
    val knownContentLength =
        contentLengthCache[mediaId] ?: storedFormat?.contentLength?.takeIf { it > 0L } ?: runCatching {
            downloadCache
                .getContentMetadata(mediaId)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrNull()?.takeIf { it > 0L } ?: runCatching {
            playerCache
                .getContentMetadata(mediaId)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrNull()?.takeIf { it > 0L } ?: runCatching {
            downloadCache.getCachedSpans(mediaId).takeIf { it.isNotEmpty() }?.sumOf { it.length }
        }.getOrNull()?.takeIf { it > 0L }

    knownContentLength?.takeIf { it > 0L }?.let { contentLengthCache[mediaId] = it }

    if (allowCacheShortCircuit) {
        resolveCachedDataSpec(
            dataSpec = dataSpec,
            mediaId = mediaId,
            knownContentLength = knownContentLength,
        )?.let { cachedDataSpec ->
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            return cachedDataSpec
        }
    }

    val requiredCachedLength =
        if (dataSpec.length >= 0) {
            dataSpec.length
        } else {
            knownContentLength?.let { nonNullContentLength ->
                (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
            }
        }

    if (allowCacheShortCircuit && requiredCachedLength != null) {
        val isFullyCached =
            downloadCache.isCached(mediaId, dataSpec.position, requiredCachedLength) ||
                playerCache.isCached(mediaId, dataSpec.position, requiredCachedLength)
        if (isFullyCached) {
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            return dataSpec
        }
    }

    val lowDataEnabled = isLowDataEnabled
    val isMeteredConnection = connectivityManager.isActiveNetworkMetered ||
        (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
    val shouldBypassFlac = lowDataEnabled && isMeteredConnection
    val currentSource = currentPlaybackSource
    val effectiveSource = if (shouldBypassFlac) PlaybackSource.YT_MUSIC else currentSource
    val cacheKey = "${mediaId}_${effectiveSource.name}"
    if (preferredStreamClient == PlayerStreamClient.ARCHIVETUNE_EXTRACTOR) {
        return resolveArchiveTuneExtractorDataSpec(
            dataSpec = dataSpec,
            mediaId = mediaId,
        )
    }

    val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
    playbackUrlCache[cacheKey]
        ?.takeIf {
            it.isValidFor(
                authFingerprint = authFingerprint,
                minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
            )
        }?.let {
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            val resolvedDataSpec = dataSpec.withUri(it.url.toUri())
            val length =
                resolveStreamChunkLength(
                    requestedLength = dataSpec.length,
                    position = dataSpec.position,
                    knownContentLength = knownContentLength,
                    chunkLength = MusicService.CHUNK_LENGTH,
                    mimeType = storedFormat?.mimeType,
                )
            return length?.let { nonNullLength ->
                resolvedDataSpec.subrange(0L, nonNullLength)
            } ?: resolvedDataSpec
        }

    val losslessResult = if (!shouldBypassFlac && currentSource == PlaybackSource.FLAC) {
        val cachedLossless = if (enableMemoryCache) losslessUrlCache.get(cacheKey) else null
        val isOffline = connectivityManager.activeNetwork == null
        val hasLocalCache = runCatching {
            playerCache.getCachedSpans(mediaId).isNotEmpty() || downloadCache.getCachedSpans(mediaId).isNotEmpty()
        }.getOrDefault(false)

        if (cachedLossless != null) {
            Timber.tag("FLAC_PLAYBACK").d("Using cached lossless URL for $mediaId")
            cachedLossless
        } else if (isOffline || hasLocalCache) {
            Timber.tag("FLAC_PLAYBACK").d("Bypassed FLAC due to offline or local cache present")
            null
        } else {
            try {
                runBlocking(Dispatchers.IO) {
                    val quality = dataStore.get(FlacStreamingQualityKey, FlacQuality.CD.name).toEnum(FlacQuality.CD)
                    var song = database.song(mediaId).firstOrNull()

                    if (song == null) {
                        Timber.tag("FLAC_PLAYBACK").w("Song $mediaId not found in DB for FLAC resolving, attempting to create transient song")
                        val metadata = (dataSpec.customData as? moe.rukamori.archivetune.models.MediaMetadata)
                            ?: withContext(Dispatchers.Main) {
                                player.currentMediaItem?.takeIf { it.mediaId == mediaId }?.metadata
                                    ?: player.findNextMediaItemById(mediaId)?.metadata
                            }

                        if (metadata != null) {
                            song = createTransientSongFromMedia(metadata)
                        }
                    }

                    val result = song?.let {
                        Timber.tag("FLAC_PLAYBACK").d("Resolving FLAC for: ${it.song.title} (ISRC: ${it.song.isrc ?: "none"}, mediaId: $mediaId)")
                        losslessStreamResolver.resolve(it, quality)
                    }

                    if (result != null) {
                        Timber.tag("FLAC_PLAYBACK").i("FLAC resolved successfully: origin=${result.origin}, url=${result.url}")
                        if (enableMemoryCache) losslessUrlCache.put(cacheKey, result)
                    } else {
                        Timber.tag("FLAC_PLAYBACK").w("FLAC resolver returned NULL for $mediaId")
                        losslessUrlCache.remove(cacheKey)
                    }
                    result
                }
            } catch (e: InterruptedException) {
                Timber.tag("FLAC_PLAYBACK").d("FLAC resolving interrupted by player skip")
                losslessUrlCache.remove(cacheKey)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag("FLAC_PLAYBACK").e(e, "FLAC resolve failed")
                losslessUrlCache.remove(cacheKey)
                null
            }
        }
    } else {
        Timber.tag("FLAC_PLAYBACK").d("Bypassed FLAC due to shouldBypassFlac=$shouldBypassFlac (lowDataEnabled=$lowDataEnabled, isMetered=$isMeteredConnection)")
        null
    }

    if (losslessResult != null && losslessResult.url.isNotBlank()) {
        val headers = mutableMapOf<String, String>()
        if (losslessResult.origin in listOf("squid", "kennyy", "arcod", "qobuz", "qbdlx")) {
            headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            headers["Referer"] = "https://music.youtube.com/"
        }

        val flacFormat = FormatEntity(
            id = mediaId,
            itag = 0,
            mimeType = "audio/flac",
            codecs = losslessResult.codec ?: "flac",
            bitrate = losslessResult.bitrateKbps ?: 0,
            sampleRate = losslessResult.sampleRateHz,
            contentLength = 0L,
            loudnessDb = null,
            perceptualLoudnessDb = null,
            playbackUrl = losslessResult.url,
            bitsPerSample = losslessResult.bitsPerSample
        )
        database.query { upsert(flacFormat) }

        val resolvedDataSpec = dataSpec.buildUpon()
            .setKey("flac_$mediaId")
            .setUri(losslessResult.url.toUri())
            .setHttpRequestHeaders(headers)
            .build()

        return resolvedDataSpec
    }

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

    val nonNullPlayback =
        requireNotNull(playbackData) {
            getString(R.string.error_unknown)
        }
    nonNullPlayback.playbackTracking
        ?.remotePlaybackTrackingUrl()
        ?.let { remotePlaybackTrackingUrlCache[mediaId] = it }
    val format = nonNullPlayback.format
    val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
    val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb
    val resolvedContentLength = format.contentLength ?: knownContentLength ?: 0L
    val resolvedCodecs =
        format.mimeType
            .substringAfter("codecs=", "")
            .removeSurrounding("\"")
            .substringBefore("\"")
    resolvedContentLength.takeIf { it > 0L }?.let { contentLengthCache[mediaId] = it }

    Timber
        .tag(
            "AudioNormalization",
        ).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
    if (loudnessDb == null && perceptualLoudnessDb == null) {
        Timber.tag("AudioNormalization").w("No loudness data available from YouTube for video: $mediaId")
    }

    val formatEntity =
        FormatEntity(
            id = mediaId,
            itag = format.itag,
            mimeType = format.mimeType.split(";")[0],
            codecs = resolvedCodecs,
            bitrate = format.bitrate,
            sampleRate = format.audioSampleRate,
            contentLength = resolvedContentLength,
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb,
            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
        )
    val resolvedNormalizationFactor = calculateAudioNormalizationFactor(formatEntity, normalizeAudio = true)
    audioNormalizationFactorCache[mediaId] = resolvedNormalizationFactor
    scope.launch {
        if (currentMediaMetadata.value?.id == mediaId &&
            dataStore.get(AudioNormalizationKey, true)
        ) {
            normalizeFactor.value = resolvedNormalizationFactor
        }
    }

    database.query {
        upsert(
            formatEntity,
        )
    }
    scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

    val streamUrl = nonNullPlayback.streamUrl

    val trackingExpiryMs = System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)

    if (!shouldBypassFlac) {
        playbackUrlCache[cacheKey] =
            AuthScopedCacheValue(
                url = streamUrl,
                expiresAtMs = trackingExpiryMs,
                authFingerprint = nonNullPlayback.authFingerprint,
            )
    }
    val resolvedDataSpec = dataSpec.withUri(streamUrl.toUri())
    val length =
        resolveStreamChunkLength(
            requestedLength = dataSpec.length,
            position = dataSpec.position,
            knownContentLength = knownContentLength ?: format.contentLength,
            chunkLength = MusicService.CHUNK_LENGTH,
            mimeType = format.mimeType,
        )
    return length?.let { nonNullLength ->
        resolvedDataSpec.subrange(0L, nonNullLength)
    } ?: resolvedDataSpec
}

private fun MusicService.resolveArchiveTuneExtractorDataSpec(
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
            return dataSpec.withUri(cached.url.toUri())
        }

    val streamUrl =
        runCatching {
            runBlocking(Dispatchers.IO) {
                streamingExtractionManager.extractAudioUrl(
                    videoUrl = mediaId.toYouTubeWatchUrl(),
                    userPoToken = userPoToken,
                    cookies = userCookies,
                    userGvsToken = userGvsToken,
                )
            }
        }.getOrElse { throwable ->
            when {
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

                throwable is ArchiveTuneExtractorException -> {
                    throw PlaybackException(
                        getString(R.string.error_no_stream),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }

                throwable is PlaybackException -> {
                    throw throwable
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

    extractorPlaybackUrlCache[mediaId] =
        AuthScopedCacheValue(
            url = streamUrl,
            expiresAtMs = System.currentTimeMillis() + MusicService.ArchiveTuneExtractorCacheTtlMs,
            authFingerprint = authFingerprint,
        )
    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
    return dataSpec.withUri(streamUrl.toUri())
}

private fun PlaybackAuthState.resolveExtractorPoToken(): String? =
    resolveExtractorGvsToken()
        ?: poTokenPlayer.normalizeExtractorRequestValue()

private fun PlaybackAuthState.resolveExtractorGvsToken(): String? =
    resolveGvsPoToken().normalizeExtractorRequestValue()
        ?: poTokenGvs.normalizeExtractorRequestValue()
        ?: poToken.normalizeExtractorRequestValue()

private fun PlaybackAuthState.resolveExtractorCookies(): String? = cookie.normalizeExtractorRequestValue()

private fun String?.normalizeExtractorRequestValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String.toYouTubeWatchUrl(): String = "https://music.youtube.com/watch?v=$this"

private fun MusicService.isExtractorPlaybackUri(uri: Uri): Boolean {
    val url = uri.toString()
    return extractorPlaybackUrlCache.values.any { it.url == url } ||
        uri.path?.startsWith("/api/play/") == true
}

private fun MusicService.resolveCachedDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    knownContentLength: Long?,
): DataSpec? {
    val requestedLength =
        when {
            dataSpec.length > 0L -> {
                dataSpec.length
            }

            knownContentLength != null && knownContentLength > dataSpec.position -> {
                knownContentLength - dataSpec.position
            }

            else -> {
                return null
            }
        }

    val cachedLength =
        getContinuousCachedLength(
            mediaId = mediaId,
            position = dataSpec.position,
            requestedLength = requestedLength,
        )

    if (cachedLength < requestedLength) return null

    return dataSpec.subrange(0L, requestedLength)
}

private fun MusicService.getContinuousCachedLength(
    mediaId: String,
    position: Long,
    requestedLength: Long,
): Long {
    val targetEnd = position.saturatingAdd(requestedLength)
    var cursor = position
    val spans =
        (
            runCatching { downloadCache.getCachedSpans(mediaId).toList() }.getOrNull().orEmpty() +
                runCatching { playerCache.getCachedSpans(mediaId).toList() }.getOrNull().orEmpty()
            ).asSequence()
            .filter { span -> span.position.saturatingAdd(span.length) > position }
            .sortedBy { span -> span.position }
            .toList()

    for (span in spans) {
        if (span.position > cursor) break
        val spanEnd = span.position.saturatingAdd(span.length)
        if (spanEnd > cursor) {
            cursor = minOf(spanEnd, targetEnd)
            if (cursor >= targetEnd) break
        }
    }

    return (cursor - position).coerceAtLeast(0L)
}

private fun Long.saturatingAdd(value: Long): Long {
    if (value <= 0L) return this
    val result = this + value
    return if (result < this) Long.MAX_VALUE else result
}

private fun Uri.shouldBypassYouTubeResolver(): Boolean {
    val normalizedScheme = scheme?.lowercase(Locale.US)
    return normalizedScheme == "content" ||
        normalizedScheme == "file" ||
        normalizedScheme == "android.resource" ||
        normalizedScheme == "http" ||
        normalizedScheme == "https"
}

private fun Uri.shouldBypassPlayerCache(): Boolean {
    val normalizedScheme = scheme?.lowercase(Locale.US)
    return normalizedScheme == "content" ||
        normalizedScheme == "file" ||
        normalizedScheme == "android.resource"
}

private fun Throwable.isNetworkConnectionFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ConnectException || current is UnknownHostException) return true
        current = current.cause
    }
    return false
}

private fun Throwable.isRequestTimeout(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SocketTimeoutException) return true
        if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
        current = current.cause
    }
    return false
}
