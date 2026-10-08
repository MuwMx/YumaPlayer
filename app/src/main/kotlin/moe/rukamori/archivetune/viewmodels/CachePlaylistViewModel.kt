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
    val spans = (runCatching { playerCache.getCachedSpans(key).toList() }.getOrNull().orEmpty()) +
        (runCatching { downloadCache.getCachedSpans(key).toList() }.getOrNull().orEmpty())
    val merged = mergeSpans(spans)
    val cachedBytes = merged.sumOf { it.end - it.start }

    val isFull = if (contentLength > 0L) {
        runCatching { playerCache.isCached(key, 0L, contentLength) }.getOrDefault(false) ||
            runCatching { downloadCache.isCached(key, 0L, contentLength) }.getOrDefault(false) ||
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
): SongCacheEvaluation {
    val v2YtKey = ytStreamCacheKey(mediaId)
    val legacyYtKey = mediaId
    val v2YtLength = resolveKeyContentLength(v2YtKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedYtLength
    val legacyYtLength = resolveKeyContentLength(legacyYtKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedYtLength

    val v2YtResult = evaluateKeyCache(v2YtKey, playerCache, downloadCache, v2YtLength)
    val legacyYtResult = evaluateKeyCache(legacyYtKey, playerCache, downloadCache, legacyYtLength)

    val ytCachedBytes = v2YtResult.cachedBytes + legacyYtResult.cachedBytes
    val isYtFullyCached = v2YtResult.isFullyCached || legacyYtResult.isFullyCached

    val v2FlacKey = flacStreamCacheKey(mediaId)
    val legacyFlacKey = flacCacheKey(mediaId)
    val v2FlacLength = resolveKeyContentLength(v2FlacKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedFlacLength
    val legacyFlacLength = resolveKeyContentLength(legacyFlacKey, playerCache, downloadCache).takeIf { it > 0L } ?: storedFlacLength

    val v2FlacResult = evaluateKeyCache(v2FlacKey, playerCache, downloadCache, v2FlacLength)
    val legacyFlacResult = evaluateKeyCache(legacyFlacKey, playerCache, downloadCache, legacyFlacLength)

    val flacCachedBytes = v2FlacResult.cachedBytes + legacyFlacResult.cachedBytes
    val isFlacFullyCached = v2FlacResult.isFullyCached || legacyFlacResult.isFullyCached

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

internal fun hasUncachedPlayerSource(
    mediaId: String,
    playerKeys: Set<String>,
    downloadKeys: Set<String>,
): Boolean {
    val ytKeys = listOf(ytStreamCacheKey(mediaId), mediaId)
    val flacKeys = listOf(flacStreamCacheKey(mediaId), flacCacheKey(mediaId))

    val hasYtPlayer = ytKeys.any { it in playerKeys }
    val hasYtDownload = ytKeys.any { it in downloadKeys }
    val hasFlacPlayer = flacKeys.any { it in playerKeys }
    val hasFlacDownload = flacKeys.any { it in downloadKeys }

    return (hasYtPlayer && !hasYtDownload) || (hasFlacPlayer && !hasFlacDownload)
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
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<CachedSong>>(emptyList())
        val cachedSongs: StateFlow<List<CachedSong>> = _cachedSongs

        init {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val playerKeys = runCatching { playerCache.keys }.getOrDefault(emptySet())
                    val downloadKeys = runCatching { downloadCache.keys }.getOrDefault(emptySet())
                    val playerMediaIds = playerKeys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
                    val pureCacheIds = playerMediaIds.filter { mediaId ->
                        hasUncachedPlayerSource(mediaId, playerKeys, downloadKeys)
                    }.toSet()

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
                        if (evaluation.isFullyCached) {                            CachedSong(
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
            removeSongResources(songId, playerCache)
        }
    }
