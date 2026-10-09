/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.getFormatForSource
import moe.rukamori.archivetune.playback.saturatingAdd
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "CachePlaylistViewModel"
private const val BATCH_SIZE = 400
private const val POLL_INTERVAL_MS = 1000L

@Immutable
data class CachedSong(
    val song: Song,
    val source: String,
    val cachedBytes: Long,
    val formattedSize: String,
    val isFullyCached: Boolean,
) {
    val id: String get() = song.id
    val title: String get() = song.song.title
    val artists get() = song.artists
    val duration: Int get() = song.song.duration
    val thumbnailUrl get() = song.song.thumbnailUrl

    fun toMediaItem() = song.toMediaItem()

    val statusText: String
        get() = if (isFullyCached) {
            "$source • $formattedSize"
        } else {
            "$source • Частично • $formattedSize"
        }
}

internal data class CacheRange(val start: Long, val end: Long)

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

internal data class KeyCacheResult(
    val cachedBytes: Long,
    val isFullyCached: Boolean,
)

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

@Immutable
data class SongCacheEvaluation(
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

internal fun evaluateEligibleCachedSong(
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

internal fun extractCompletedDownloadKeys(downloads: Collection<Download>): Set<String> {
    return downloads
        .filter { it.state == Download.STATE_COMPLETED }
        .mapNotNull { download ->
            download.request.customCacheKey?.takeIf(String::isNotBlank)
                ?: download.request.id.takeIf(String::isNotBlank)
        }
        .toSet()
}

internal fun findPureCacheIds(
    allCachedKeys: Set<String>,
    completedDownloadKeys: Set<String>,
): Set<String> {
    val allMediaIds = allCachedKeys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
    return allMediaIds.filter { mediaId ->
        hasUncachedPlayerSource(mediaId, allCachedKeys, completedDownloadKeys)
    }.toSet()
}

internal fun hasUncachedPlayerSource(
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

internal suspend fun fetchSongsInBatches(
    database: MusicDatabase,
    ids: Collection<String>,
    batchSize: Int = BATCH_SIZE,
): List<Song> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(batchSize).flatMap { batch ->
        database.getSongsByIds(batch)
    }
}

internal suspend fun runPollingLoop(
    delayMs: Long = POLL_INTERVAL_MS,
    delayProvider: suspend (Long) -> Unit = { delay(it) },
    step: suspend () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating cached songs")
        }
        delayProvider(delayMs)
    }
}

internal fun removeSongResources(
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

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<CachedSong>>(emptyList())
        val cachedSongs: StateFlow<List<CachedSong>> = _cachedSongs

        init {
            viewModelScope.launch(Dispatchers.IO) {
                runPollingLoop {
                    updateCacheState()
                }
            }
        }

        internal suspend fun updateCacheState() {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val playerKeys = playerCache.keys
            val downloadCacheKeys = downloadCache.keys
            val allCachedKeys = playerKeys + downloadCacheKeys

            val completedDownloadKeys = extractCompletedDownloadKeys(downloadUtil.downloads.value.values)

            val pureCacheIds = findPureCacheIds(allCachedKeys, completedDownloadKeys)

            val songs = fetchSongsInBatches(database, pureCacheIds, BATCH_SIZE)

            val evaluatedSongs = songs.mapNotNull { song ->
                val mediaId = song.id
                val storedYtLength = database.getFormatForSource(mediaId, PlaybackSource.YT_MUSIC)?.contentLength?.takeIf { it > 0L } ?: -1L
                val storedFlacLength = database.getFormatForSource(mediaId, PlaybackSource.FLAC)?.contentLength?.takeIf { it > 0L } ?: -1L
                evaluateEligibleCachedSong(
                    song = song,
                    playerCache = playerCache,
                    downloadCache = downloadCache,
                    storedYtLength = storedYtLength,
                    storedFlacLength = storedFlacLength,
                    completedDownloadKeys = completedDownloadKeys,
                )
            }

            val now = LocalDateTime.now()
            val songsToUpdate = evaluatedSongs.mapNotNull { cachedSong ->
                if (cachedSong.song.song.dateDownload == null) {
                    cachedSong.song.song.copy(dateDownload = now)
                } else {
                    null
                }
            }
            if (songsToUpdate.isNotEmpty()) {
                database.query {
                    songsToUpdate.forEach { update(it) }
                }
            }

            val updatedSongs = evaluatedSongs.map { cachedSong ->
                if (cachedSong.song.song.dateDownload == null) {
                    cachedSong.copy(song = cachedSong.song.copy(song = cachedSong.song.song.copy(dateDownload = now)))
                } else {
                    cachedSong
                }
            }

            _cachedSongs.value =
                updatedSongs
                    .filter { !hideExplicit || !it.song.song.explicit }
                    .filter { it.song.artists.none { artist -> artist.blockedAt != null } }
                    .sortedByDescending { it.song.song.dateDownload }
        }

        fun removeSongFromCache(songId: String) {
            removeSongResources(songId, playerCache)
        }
    }
