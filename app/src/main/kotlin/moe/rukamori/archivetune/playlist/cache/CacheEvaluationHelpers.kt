/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.engine.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.engine.flacCacheKey
import moe.rukamori.archivetune.playback.engine.flacStreamCacheKey
import moe.rukamori.archivetune.playback.engine.saturatingAdd
import moe.rukamori.archivetune.playback.engine.ytStreamCacheKey
import moe.rukamori.archivetune.ui.utils.formatFileSize
import timber.log.Timber

internal data class CacheRange(val start: Long, val end: Long)

internal data class KeyCacheResult(
    val cachedBytes: Long,
    val isFullyCached: Boolean,
)

internal data class SongCacheEvaluation(
    val hasYt: Boolean,
    val hasFlac: Boolean,
    val isYtFullyCached: Boolean,
    val isFlacFullyCached: Boolean,
    val ytCachedBytes: Long,
    val flacCachedBytes: Long,
    val totalCachedBytes: Long,
    val source: String,
    val formattedSize: String,
    val isFullyCached: Boolean,
)

internal fun mergeSpans(spans: Collection<CacheSpan>): List<CacheRange> {
    if (spans.isEmpty()) return emptyList()
    val sorted = spans.map { span ->
        val start = span.position
        val end = span.position.saturatingAdd(span.length)
        CacheRange(start, end)
    }.filter { it.end > it.start }.sortedBy { it.start }

    if (sorted.isEmpty()) return emptyList()
    val merged = ArrayList<CacheRange>(sorted.size)
    var current = sorted[0]
    for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.start <= current.end) {
            current = CacheRange(current.start, maxOf(current.end, next.end))
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

internal fun isContinuousRangeComplete(mergedRanges: List<CacheRange>, contentLength: Long): Boolean {
    if (contentLength <= 0L || mergedRanges.isEmpty()) return false
    val first = mergedRanges[0]
    return first.start == 0L && first.end >= contentLength
}

internal fun resolveKeyContentLength(
    key: String,
    playerCache: Cache,
    downloadCache: Cache,
): Long {
    val len1 = playerCache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
    if (len1 > 0L) return len1
    val len2 = downloadCache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
    if (len2 > 0L) return len2
    return -1L
}

internal fun evaluateKeyCache(
    key: String,
    playerCache: Cache,
    downloadCache: Cache,
    contentLength: Long,
): KeyCacheResult {
    val spans = playerCache.getCachedSpans(key).toList() + downloadCache.getCachedSpans(key).toList()
    val merged = mergeSpans(spans)
    val cachedBytes = merged.sumOf { it.end - it.start }

    val isFull = if (contentLength > 0L) {
        playerCache.isCached(key, 0L, contentLength) ||
            downloadCache.isCached(key, 0L, contentLength) ||
            isContinuousRangeComplete(merged, contentLength)
    } else {
        false
    }
    return KeyCacheResult(cachedBytes, isFull)
}

internal fun evaluateSongCache(
    mediaId: String,
    playerCache: Cache,
    downloadCache: Cache,
    storedYtLength: Long = -1L,
    storedFlacLength: Long = -1L,
    completedDownloadKeys: Set<String> = emptySet(),
): SongCacheEvaluation {
    val ytKeys = listOf(ytStreamCacheKey(mediaId), mediaId)
    val isYtEligible = !ytKeys.any { it in completedDownloadKeys }

    val v2YtKey = ytStreamCacheKey(mediaId)
    val legacyYtKey = mediaId
    val (ytCachedBytes, isYtFullyCached) = if (isYtEligible) {
        val v2YtLength = resolveKeyContentLength(v2YtKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedYtLength
        val legacyYtLength = resolveKeyContentLength(legacyYtKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedYtLength
        val v2YtResult = evaluateKeyCache(v2YtKey, playerCache, downloadCache, v2YtLength)
        val legacyYtResult = evaluateKeyCache(legacyYtKey, playerCache, downloadCache, legacyYtLength)
        Pair(v2YtResult.cachedBytes + legacyYtResult.cachedBytes, v2YtResult.isFullyCached || legacyYtResult.isFullyCached)
    } else {
        Pair(0L, false)
    }

    val flacKeys = listOf(flacStreamCacheKey(mediaId), flacCacheKey(mediaId))
    val isFlacEligible = !flacKeys.any { it in completedDownloadKeys }

    val v2FlacKey = flacStreamCacheKey(mediaId)
    val legacyFlacKey = flacCacheKey(mediaId)
    val (flacCachedBytes, isFlacFullyCached) = if (isFlacEligible) {
        val v2FlacLength = resolveKeyContentLength(v2FlacKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedFlacLength
        val legacyFlacLength = resolveKeyContentLength(legacyFlacKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedFlacLength
        val v2FlacResult = evaluateKeyCache(v2FlacKey, playerCache, downloadCache, v2FlacLength)
        val legacyFlacResult = evaluateKeyCache(legacyFlacKey, playerCache, downloadCache, legacyFlacLength)
        Pair(v2FlacResult.cachedBytes + legacyFlacResult.cachedBytes, v2FlacResult.isFullyCached || legacyFlacResult.isFullyCached)
    } else {
        Pair(0L, false)
    }

    val hasYt = isYtEligible && (ytCachedBytes > 0L || isYtFullyCached)
    val hasFlac = isFlacEligible && (flacCachedBytes > 0L || isFlacFullyCached)

    val source = when {
        isFlacFullyCached && isYtFullyCached -> "FLAC + Opus"
        isFlacFullyCached -> "FLAC"
        isYtFullyCached -> "Opus"
        else -> ""
    }

    val totalCachedBytes = ytCachedBytes + flacCachedBytes
    val formattedSize = formatFileSize(totalCachedBytes)
    val isFullyCached = isFlacFullyCached || isYtFullyCached

    return SongCacheEvaluation(
        hasYt = hasYt,
        hasFlac = hasFlac,
        isYtFullyCached = isYtFullyCached,
        isFlacFullyCached = isFlacFullyCached,
        ytCachedBytes = ytCachedBytes,
        flacCachedBytes = flacCachedBytes,
        totalCachedBytes = totalCachedBytes,
        source = source,
        formattedSize = formattedSize,
        isFullyCached = isFullyCached,
    )
}

fun evaluateEligibleCachedSong(
    song: Song,
    playerCache: Cache,
    downloadCache: Cache,
    storedYtLength: Long = -1L,
    storedFlacLength: Long = -1L,
    completedDownloadKeys: Set<String> = emptySet(),
): CachedSong? {
    val evaluation = evaluateSongCache(
        mediaId = song.id,
        playerCache = playerCache,
        downloadCache = downloadCache,
        storedYtLength = storedYtLength,
        storedFlacLength = storedFlacLength,
        completedDownloadKeys = completedDownloadKeys,
    )
    return if (evaluation.isFullyCached) {
        CachedSong(
            song = song,
            source = evaluation.source,
            cachedBytes = evaluation.totalCachedBytes,
            formattedSize = evaluation.formattedSize,
            isFullyCached = evaluation.isFullyCached,
        )
    } else {
        null
    }
}

fun extractCompletedDownloadKeys(downloads: Collection<Download>): Set<String> {
    return downloads
        .filter { it.state == Download.STATE_COMPLETED }
        .mapNotNull { download ->
            download.request.customCacheKey?.takeIf(String::isNotBlank)
                ?: download.request.id.takeIf(String::isNotBlank)
        }
        .toSet()
}

fun findPureCacheIds(
    allCachedKeys: Set<String>,
    completedDownloadKeys: Set<String>,
): Set<String> {
    val allMediaIds = allCachedKeys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
    return allMediaIds.filter { mediaId ->
        hasUncachedPlayerSource(mediaId, allCachedKeys, completedDownloadKeys)
    }.toSet()
}

fun hasUncachedPlayerSource(
    mediaId: String,
    cachedKeys: Set<String>,
    downloadKeys: Set<String>,
): Boolean {
    val ytKeys = listOf(ytStreamCacheKey(mediaId), mediaId)
    val flacKeys = listOf(flacStreamCacheKey(mediaId), flacCacheKey(mediaId))

    val hasYtCached = ytKeys.any { it in cachedKeys }
    val hasYtDownload = ytKeys.any { it in downloadKeys }
    val hasFlacCached = flacKeys.any { it in cachedKeys }
    val hasFlacDownload = flacKeys.any { it in downloadKeys }

    return (hasYtCached && !hasYtDownload) || (hasFlacCached && !hasFlacDownload)
}

fun removeSongResources(
    songId: String,
    playerCache: Cache,
) {
    val keys = listOf(
        ytStreamCacheKey(songId),
        flacStreamCacheKey(songId),
        songId,
        flacCacheKey(songId),
    )
    keys.forEach { key ->
        runCatching { playerCache.removeResource(key) }
    }
}

suspend fun fetchSongsInBatches(
    database: MusicDatabase,
    ids: Collection<String>,
    batchSize: Int = 400,
): List<Song> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(batchSize).flatMap { batch ->
        database.getSongsByIds(batch)
    }
}

suspend fun runPollingLoop(
    delayMs: Long = 1000L,
    delayProvider: suspend (Long) -> Unit = { delay(it) },
    step: suspend () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("CachePlaylist").e(e, "Error updating cached songs")
        }
        delayProvider(delayMs)
    }
}
