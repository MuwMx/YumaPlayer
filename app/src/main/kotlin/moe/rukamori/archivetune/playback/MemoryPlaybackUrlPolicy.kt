package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.playback.resolvers.StreamUrl
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

internal fun resolveFlacKeyMetadataLength(
    targetKey: String,
    caches: List<Cache>,
): Long {
    for (cache in caches) {
        try {
            val length = cache.getContentMetadata(targetKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            if (length > 0L) return length
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("MemoryPlaybackUrlPolicy").w(e, "Failed to read content metadata for $targetKey")
        }
    }
    return 0L
}

internal fun resolvePreservedFlacContentLength(
    targetKey: String,
    existingLength: Long? = null,
    inMemoryLength: Long? = null,
    caches: List<Cache> = emptyList(),
): Long {
    if (existingLength != null && existingLength > 0L) {
        return existingLength
    }
    if (inMemoryLength != null && inMemoryLength > 0L) {
        return inMemoryLength
    }
    return resolveFlacKeyMetadataLength(targetKey, caches)
}

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

    val targetKey = explicitKey ?: resolveTargetDataKey(mediaId, PlaybackSource.FLAC)
    val flacFormatId = formatIdForSource(mediaId, PlaybackSource.FLAC)
    val inMemoryLength = contentLengthCache[targetKey]
    val metadataLength = resolveFlacKeyMetadataLength(targetKey, listOf(playerCache, downloadCache))
    val externalLength = inMemoryLength?.takeIf { it > 0L } ?: metadataLength

    database.transaction {
        val existing = getFormatById(flacFormatId)
        val preservedLength = resolvePreservedFlacContentLength(
            targetKey = targetKey,
            existingLength = existing?.contentLength,
            inMemoryLength = externalLength,
        )
        if (preservedLength > 0L) {
            contentLengthCache[targetKey] = preservedLength
        }

        val flacFormat =
            FormatEntity(
                id = flacFormatId,
                itag = 0,
                mimeType = "audio/flac",
                codecs = streamUrl.codec ?: "flac",
                bitrate = streamUrl.bitrateKbps ?: 0,
                sampleRate = streamUrl.sampleRateHz,
                contentLength = preservedLength,
                loudnessDb = existing?.loudnessDb,
                perceptualLoudnessDb = existing?.perceptualLoudnessDb,
                playbackUrl = streamUrl.url,
                bitsPerSample = streamUrl.bitsPerSample ?: existing?.bitsPerSample,
            )
        upsert(flacFormat)
    }

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
