package moe.rukamori.archivetune.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.FormatEntity

internal fun MusicService.getOrResolveContentLength(
    targetKey: String,
    mediaId: String,
    storedFormat: FormatEntity?,
): Long? {
    val cached = contentLengthCache[targetKey]
    if (cached != null && cached > 0L) return cached

    val isTargetMatchingFormat = when {
        targetKey == ytStreamCacheKey(mediaId) || targetKey == mediaId ->
            storedFormat?.matchesSource(PlaybackSource.YT_MUSIC) == true
        targetKey == flacStreamCacheKey(mediaId) || targetKey == flacCacheKey(mediaId) ->
            storedFormat?.matchesSource(PlaybackSource.FLAC) == true
        else -> false
    }

    val formatLength = if (isTargetMatchingFormat) storedFormat?.contentLength?.takeIf { it > 0L } else null

    val resolved = formatLength
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

    resolved?.takeIf { it > 0L }?.let { contentLengthCache[targetKey] = it }
    return resolved
}

internal fun MusicService.isKeyFullyCached(
    targetKey: String,
    mediaId: String,
    storedFormat: FormatEntity? = null,
): Boolean {
    val length = getOrResolveContentLength(targetKey, mediaId, storedFormat) ?: return false
    if (length <= 0L) return false
    return downloadCache.isCached(targetKey, 0L, length) ||
        playerCache.isCached(targetKey, 0L, length) ||
        getContinuousCachedLength(targetKey, 0L, length) >= length
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
        if (dataSpec.length > 0L) {
            dataSpec.length
        } else {
            knownLength?.let { nonNullContentLength ->
                (nonNullContentLength - dataSpec.position).takeIf { it > 0L }
            }
        }

    if (requiredLength != null && requiredLength > 0L) {
        val isFullyCached =
            downloadCache.isCached(targetKey, dataSpec.position, requiredLength) ||
                playerCache.isCached(targetKey, dataSpec.position, requiredLength) ||
                getContinuousCachedLength(targetKey, dataSpec.position, requiredLength) >= requiredLength
        if (isFullyCached) {
            scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            val specWithKey = if (dataSpec.key != targetKey) {
                dataSpec.buildUpon().setKey(targetKey).build()
            } else {
                dataSpec
            }
            return specWithKey.subrange(0L, requiredLength)
        }
    }

    return null
}

internal fun MusicService.probeDiskCacheShortCircuit(
    dataSpec: DataSpec,
    mediaId: String,
    currentSource: PlaybackSource,
    storedFormat: FormatEntity?,
    allowCacheShortCircuit: Boolean,
): DataSpec? {
    if (!allowCacheShortCircuit) {
        return null
    }

    val selectedVersionedKey = streamCacheKey(mediaId, currentSource)
    resolveFromDiskCache(
        dataSpec = dataSpec,
        mediaId = mediaId,
        targetKey = selectedVersionedKey,
        storedFormat = storedFormat,
    )?.let { return it }

    val selectedLegacyKey = legacyDataKey(mediaId, currentSource)
    if (validateLegacyCacheKeyAcrossCaches(downloadCache, playerCache, selectedLegacyKey, currentSource)) {
        resolveFromDiskCache(
            dataSpec = dataSpec,
            mediaId = mediaId,
            targetKey = selectedLegacyKey,
            storedFormat = storedFormat,
        )?.let { return it }
    }

    val isOffline = connectivityManager.activeNetwork == null
    if (!isOffline) {
        return null
    }

    val oppositeSource = when (currentSource) {
        PlaybackSource.FLAC -> PlaybackSource.YT_MUSIC
        PlaybackSource.YT_MUSIC -> PlaybackSource.FLAC
    }
    val oppositeVersionedKey = streamCacheKey(mediaId, oppositeSource)
    if (isKeyFullyCached(oppositeVersionedKey, mediaId, storedFormat = null)) {
        resolveFromDiskCache(
            dataSpec = dataSpec,
            mediaId = mediaId,
            targetKey = oppositeVersionedKey,
            storedFormat = null,
        )?.let { return it }
    }

    val oppositeLegacyKey = legacyDataKey(mediaId, oppositeSource)
    if (validateLegacyCacheKeyAcrossCaches(downloadCache, playerCache, oppositeLegacyKey, oppositeSource) &&
        isKeyFullyCached(oppositeLegacyKey, mediaId, storedFormat = null)
    ) {
        resolveFromDiskCache(
            dataSpec = dataSpec,
            mediaId = mediaId,
            targetKey = oppositeLegacyKey,
            storedFormat = null,
        )?.let { return it }
    }

    return null
}
