package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.playback.resolvers.StreamUrl
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import timber.log.Timber

internal fun MusicService.resolveTargetDataKey(mediaId: String, source: PlaybackSource): String {
    val versioned = streamCacheKey(mediaId, source)
    val hasVersioned = runCatching {
        downloadCache.getCachedSpans(versioned).isNotEmpty() || playerCache.getCachedSpans(versioned).isNotEmpty()
    }.getOrDefault(false)
    if (hasVersioned) return versioned

    val legacy = legacyDataKey(mediaId, source)
    if (validateLegacyCacheKeyAcrossCaches(downloadCache, playerCache, legacy, source)) {
        return legacy
    }

    return versioned
}

internal fun MusicService.buildResolvedFlacDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    streamUrl: StreamUrl,
    explicitKey: String? = null,
): DataSpec {
    val headers = mutableMapOf<String, String>()
    if (streamUrl.origin in listOf("squid", "kennyy", "arcod", "qobuz", "qbdlx")) {
        headers["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        headers["Referer"] = "https://music.youtube.com/"
    }

    val flacFormat =
        FormatEntity(
            id = formatIdForSource(mediaId, PlaybackSource.FLAC),
            itag = 0,
            mimeType = "audio/flac",
            codecs = streamUrl.codec ?: "flac",
            bitrate = streamUrl.bitrateKbps ?: 0,
            sampleRate = streamUrl.sampleRateHz,
            contentLength = 0L,
            loudnessDb = null,
            perceptualLoudnessDb = null,
            playbackUrl = streamUrl.url,
            bitsPerSample = streamUrl.bitsPerSample,
        )
    database.query { upsert(flacFormat) }

    val targetKey = explicitKey ?: resolveTargetDataKey(mediaId, PlaybackSource.FLAC)
    return dataSpec.buildUpon()
        .setKey(targetKey)
        .setUri(streamUrl.url.toUri())
        .setHttpRequestHeaders(headers)
        .build()
}

internal fun MusicService.buildResolvedPlaybackDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    cached: AuthScopedCacheValue,
    knownContentLength: Long?,
    storedFormat: FormatEntity?,
    explicitKey: String? = null,
): DataSpec {
    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
    val targetKey = explicitKey ?: resolveTargetDataKey(mediaId, PlaybackSource.YT_MUSIC)
    val specWithKey = if (dataSpec.key != targetKey) dataSpec.buildUpon().setKey(targetKey).build() else dataSpec
    val resolvedDataSpec = specWithKey.withUri(cached.url.toUri())
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

internal fun MusicService.resolveFromMemoryUrlPolicy(
    dataSpec: DataSpec,
    mediaId: String,
    authFingerprint: String,
    effectiveSource: PlaybackSource,
    knownContentLength: Long?,
    storedFormat: FormatEntity?,
): DataSpec? {
    if (effectiveSource == PlaybackSource.FLAC) {
        val cachedLossless = if (enableMemoryCache) {
            losslessUrlCache.get(formatIdForSource(mediaId, PlaybackSource.FLAC))
        } else null

        if (cachedLossless != null) {
            Timber.tag("FLAC_PLAYBACK").d("Using cached lossless URL for $mediaId")
            return buildResolvedFlacDataSpec(dataSpec, mediaId, cachedLossless)
        }
    } else {
        val cachedPlayback = playbackUrlCache[formatIdForSource(mediaId, PlaybackSource.YT_MUSIC)]
            ?.takeIf {
                it.isValidFor(
                    authFingerprint = authFingerprint,
                    minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                )
            }

        if (cachedPlayback != null) {
            return buildResolvedPlaybackDataSpec(dataSpec, mediaId, cachedPlayback, knownContentLength, storedFormat)
        }
    }

    return null
}
