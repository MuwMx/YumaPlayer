package moe.rukamori.archivetune.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.entities.FormatEntity

internal fun MusicService.getOrResolveContentLength(
    targetKey: String,
    mediaId: String,
    storedFormat: FormatEntity?,
): Long? {
    val cached = contentLengthCache[targetKey]
    if (cached != null && cached > 0L) return cached

    val resolved =
        (if (targetKey == mediaId) storedFormat?.contentLength?.takeIf { it > 0L } else null)
            ?: runCatching {
                downloadCache
                    .getContentMetadata(targetKey)
                    .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            }.getOrNull()?.takeIf { it > 0L }
            ?: runCatching {
                playerCache
                    .getContentMetadata(targetKey)
                    .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            }.getOrNull()?.takeIf { it > 0L }
            ?: runCatching {
                downloadCache.getCachedSpans(targetKey).takeIf { it.isNotEmpty() }?.sumOf { it.length }
            }.getOrNull()?.takeIf { it > 0L }
            ?: runCatching {
                playerCache.getCachedSpans(targetKey).takeIf { it.isNotEmpty() }?.sumOf { it.length }
            }.getOrNull()?.takeIf { it > 0L }

    resolved?.takeIf { it > 0L }?.let { contentLengthCache[targetKey] = it }
    return resolved
}

internal fun MusicService.resolveFromDiskCache(
    dataSpec: DataSpec,
    mediaId: String,
    targetKey: String,
    storedFormat: FormatEntity?,
): DataSpec? {
    val knownLength =
        getOrResolveContentLength(
            targetKey = targetKey,
            mediaId = mediaId,
            storedFormat = storedFormat,
        )

    resolveCachedDataSpec(
        dataSpec = dataSpec,
        cacheKey = targetKey,
        knownContentLength = knownLength,
    )?.let { cachedDataSpec ->
        scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
        return cachedDataSpec
    }

    val requiredLength =
        if (dataSpec.length >= 0) {
            dataSpec.length
        } else {
            knownLength?.let { nonNullContentLength ->
                (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
            }
        }

    if (requiredLength != null) {
        val isFullyCached =
            downloadCache.isCached(targetKey, dataSpec.position, requiredLength) ||
                playerCache.isCached(targetKey, dataSpec.position, requiredLength)
        if (isFullyCached) {
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            return if (dataSpec.key != targetKey) {
                dataSpec.buildUpon().setKey(targetKey).build()
            } else {
                dataSpec
            }
        }
    }

    return null
}

internal fun MusicService.probeDiskCacheShortCircuit(
    dataSpec: DataSpec,
    mediaId: String,
    dataSpecCacheKey: String,
    flacKey: String,
    storedFormat: FormatEntity?,
    allowCacheShortCircuit: Boolean,
): DataSpec? {
    if (!allowCacheShortCircuit) {
        return null
    }

    resolveFromDiskCache(
        dataSpec = dataSpec,
        mediaId = mediaId,
        targetKey = dataSpecCacheKey,
        storedFormat = storedFormat,
    )?.let { return it }

    val fallbackCacheKey = if (dataSpecCacheKey == flacKey) mediaId else flacKey
    val shouldCheckFallback =
        connectivityManager.activeNetwork == null ||
            runCatching {
                downloadCache.getCachedSpans(fallbackCacheKey).isNotEmpty() ||
                    playerCache.getCachedSpans(fallbackCacheKey).isNotEmpty()
            }.getOrDefault(false)

    if (shouldCheckFallback) {
        resolveFromDiskCache(
            dataSpec = dataSpec,
            mediaId = mediaId,
            targetKey = fallbackCacheKey,
            storedFormat = storedFormat,
        )?.let { return it }
    }

    return null
}
