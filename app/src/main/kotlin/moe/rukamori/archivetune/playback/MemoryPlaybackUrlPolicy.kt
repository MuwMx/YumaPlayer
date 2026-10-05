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

internal fun MusicService.buildResolvedFlacDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    flacKey: String,
    streamUrl: StreamUrl,
): DataSpec {
    val headers = mutableMapOf<String, String>()
    if (streamUrl.origin in listOf("squid", "kennyy", "arcod", "qobuz", "qbdlx")) {
        headers["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        headers["Referer"] = "https://music.youtube.com/"
    }

    val flacFormat =
        FormatEntity(
            id = mediaId,
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

    return dataSpec.buildUpon()
        .setKey(flacKey)
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
): DataSpec {
    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
    val specWithMediaId = if (dataSpec.key != mediaId) dataSpec.buildUpon().setKey(mediaId).build() else dataSpec
    val resolvedDataSpec = specWithMediaId.withUri(cached.url.toUri())
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
    flacKey: String,
    cacheKey: String,
    networkCacheKey: String,
    authFingerprint: String,
    effectiveSource: PlaybackSource,
    knownContentLength: Long?,
    storedFormat: FormatEntity?,
): DataSpec? {
    val cachedPlayback = (playbackUrlCache[networkCacheKey]
        ?: playbackUrlCache[mediaId]
        ?: playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"]
        ?: playbackUrlCache[flacKey])
        ?.takeIf {
            it.isValidFor(
                authFingerprint = authFingerprint,
                minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
            )
        }

    val cachedLossless = if (enableMemoryCache) {
        losslessUrlCache.get(cacheKey)
            ?: losslessUrlCache.get(flacKey)
            ?: losslessUrlCache.get(mediaId)
            ?: losslessUrlCache.get("${mediaId}_${PlaybackSource.FLAC.name}")
    } else null

    if (effectiveSource == PlaybackSource.FLAC) {
        if (cachedLossless != null) {
            Timber.tag("FLAC_PLAYBACK").d("Using cached lossless URL for $mediaId")
            return buildResolvedFlacDataSpec(dataSpec, mediaId, flacKey, cachedLossless)
        }
        if (cachedPlayback != null) {
            Timber.tag("FLAC_PLAYBACK").d("Using prefetched playback URL fallback for $mediaId")
            return buildResolvedPlaybackDataSpec(dataSpec, mediaId, cachedPlayback, knownContentLength, storedFormat)
        }
    } else {
        if (cachedPlayback != null) {
            return buildResolvedPlaybackDataSpec(dataSpec, mediaId, cachedPlayback, knownContentLength, storedFormat)
        }
        if (cachedLossless != null) {
            Timber.tag("FLAC_PLAYBACK").d("Using prefetched lossless URL fallback for $mediaId")
            return buildResolvedFlacDataSpec(dataSpec, mediaId, flacKey, cachedLossless)
        }
    }

    return null
}
