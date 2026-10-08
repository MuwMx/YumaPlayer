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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.getFormatForSource
import moe.rukamori.archivetune.playback.saturatingAdd
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

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
    val len1 = runCatching { playerCache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) }.getOrDefault(-1L)
    if (len1 > 0L) return len1
    val len2 = runCatching { downloadCache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) }.getOrDefault(-1L)
    if (len2 > 0L) return len2
    return -1L
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
): SongCacheEvaluation {
    val ytKeys = listOf(ytStreamCacheKey(mediaId), mediaId)
    val ytSpans = ytKeys.flatMap { key ->
        (runCatching { playerCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()) +
            (runCatching { downloadCache.getCachedSpans(key).toList() }.getOrNull().orEmpty())
    }
    val ytMerged = mergeSpans(ytSpans)
    val ytCachedBytes = ytMerged.sumOf { it.end - it.start }

    val ytLengthFromKeys = ytKeys.firstNotNullOfOrNull { key ->
        resolveKeyContentLength(key, playerCache, downloadCache).takeIf { it > 0L }
    } ?: -1L
    val ytContentLength = if (ytLengthFromKeys > 0L) ytLengthFromKeys else storedYtLength

    val isYtFullyCached = if (ytContentLength > 0L) {
        ytKeys.any { key ->
            runCatching { playerCache.isCached(key, 0L, ytContentLength) }.getOrDefault(false) ||
                runCatching { downloadCache.isCached(key, 0L, ytContentLength) }.getOrDefault(false)
        } || isContinuousRangeComplete(ytMerged, ytContentLength)
    } else {
        false
    }

    val flacKeys = listOf(flacStreamCacheKey(mediaId), flacCacheKey(mediaId))
    val flacSpans = flacKeys.flatMap { key ->
        (runCatching { playerCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()) +
            (runCatching { downloadCache.getCachedSpans(key).toList() }.getOrNull().orEmpty())
    }
    val flacMerged = mergeSpans(flacSpans)
    val flacCachedBytes = flacMerged.sumOf { it.end - it.start }

    val flacLengthFromKeys = flacKeys.firstNotNullOfOrNull { key ->
        resolveKeyContentLength(key, playerCache, downloadCache).takeIf { it > 0L }
    } ?: -1L
    val flacContentLength = if (flacLengthFromKeys > 0L) flacLengthFromKeys else storedFlacLength

    val isFlacFullyCached = if (flacContentLength > 0L) {
        flacKeys.any { key ->
            runCatching { playerCache.isCached(key, 0L, flacContentLength) }.getOrDefault(false) ||
                runCatching { downloadCache.isCached(key, 0L, flacContentLength) }.getOrDefault(false)
        } || isContinuousRangeComplete(flacMerged, flacContentLength)
    } else {
        false
    }

    val hasYt = ytCachedBytes > 0L || isYtFullyCached
    val hasFlac = flacCachedBytes > 0L || isFlacFullyCached

    val source = when {
        hasFlac && hasYt -> "FLAC + Opus"
        hasFlac -> "FLAC"
        hasYt -> "Opus"
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

internal fun removeSongResources(
    songId: String,
    playerCache: Cache,
    downloadCache: Cache,
) {
    val keys = listOf(
        ytStreamCacheKey(songId),
        flacStreamCacheKey(songId),
        songId,
        flacCacheKey(songId),
    )
    keys.forEach { key ->
        runCatching { playerCache.removeResource(key) }
        runCatching { downloadCache.removeResource(key) }
    }
}

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<CachedSong>>(emptyList())
        val cachedSongs: StateFlow<List<CachedSong>> = _cachedSongs

        init {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val cachedIds = playerCache.keys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
                    val downloadedIds = downloadCache.keys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
                    val pureCacheIds = cachedIds.subtract(downloadedIds)

                    val songs =
                        if (pureCacheIds.isNotEmpty()) {
                            database.getSongsByIds(pureCacheIds.toList())
                        } else {
                            emptyList()
                        }

                    val evaluatedSongs = songs.mapNotNull { song ->
                        val mediaId = song.id
                        val storedYtLength = database.getFormatForSource(mediaId, PlaybackSource.YT_MUSIC)?.contentLength?.takeIf { it > 0L } ?: -1L
                        val storedFlacLength = database.getFormatForSource(mediaId, PlaybackSource.FLAC)?.contentLength?.takeIf { it > 0L } ?: -1L
                        val evaluation = evaluateSongCache(
                            mediaId = mediaId,
                            playerCache = playerCache,
                            downloadCache = downloadCache,
                            storedYtLength = storedYtLength,
                            storedFlacLength = storedFlacLength,
                        )
                        if (evaluation.totalCachedBytes > 0L || evaluation.isFullyCached) {
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

                    delay(1000)
                }
            }
        }

        fun removeSongFromCache(songId: String) {
            removeSongResources(songId, playerCache, downloadCache)
        }
    }
