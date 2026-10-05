/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.recovery

import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlaybackRecoveryEngine
import moe.rukamori.archivetune.utils.YTPlayerUtils

object RecoveryMaintenanceOps {
    suspend fun recoverSong(
        mediaId: String,
        databaseProvider: () -> MusicDatabase,
        findNextMediaItemById: (String) -> MediaItem?,
        playbackData: YTPlayerUtils.PlaybackData? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) {
        withContext(ioDispatcher) {
            val database = databaseProvider()
            val song = database.song(mediaId).first()
            val mediaMetadata =
                withContext(mainDispatcher) {
                    findNextMediaItemById(mediaId)?.metadata
                } ?: return@withContext
            val duration =
                song?.song?.duration?.takeIf { it != -1 }
                    ?: mediaMetadata.duration.takeIf { it != -1 }
                    ?: (
                        playbackData?.videoDetails ?: YTPlayerUtils
                            .playerResponseForMetadata(mediaId)
                            .getOrNull()
                            ?.videoDetails
                    )?.lengthSeconds?.toInt()
                    ?: -1
            database.query {
                if (song == null) {
                    insert(mediaMetadata.copy(duration = duration))
                } else if (song.song.duration == -1) {
                    update(song.song.copy(duration = duration))
                }
            }
            if (!database.hasRelatedSongs(mediaId)) {
                val relatedEndpoint =
                    YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                        ?: return@withContext
                val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return@withContext
                database.query {
                    relatedPage.songs
                        .map(SongItem::toMediaMetadata)
                        .onEach(::insert)
                        .map {
                            RelatedSongMap(
                                songId = mediaId,
                                relatedSongId = it.id,
                            )
                        }.forEach(::insert)
                }
            }
        }
    }

    suspend fun recoverSong(
        mediaId: String,
        databaseProvider: () -> MusicDatabase,
        playerActions: PlaybackRecoveryEngine.PlayerActions,
        playbackData: YTPlayerUtils.PlaybackData? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) {
        recoverSong(
            mediaId = mediaId,
            databaseProvider = databaseProvider,
            findNextMediaItemById = playerActions::findNextMediaItemById,
            playbackData = playbackData,
            ioDispatcher = ioDispatcher,
            mainDispatcher = mainDispatcher,
        )
    }

    suspend fun trimPlayerCacheToBytes(
        limitBytes: Long,
        playerCache: Cache,
        getPlayerCacheDirectorySizeBytes: () -> Long,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (limitBytes <= 0L) return

        withContext(ioDispatcher) {
            val currentSpace = runCatching { playerCache.cacheSpace }.getOrNull() ?: 0L
            var totalBytes = if (currentSpace > 0L) currentSpace else getPlayerCacheDirectorySizeBytes()
            if (totalBytes <= limitBytes) return@withContext

            data class Candidate(
                val key: String,
                val lastTouchTimestamp: Long,
                val sizeBytes: Long,
            )

            val candidates =
                runCatching {
                    playerCache.keys
                        .mapNotNull { key ->
                            runCatching {
                                val spans = playerCache.getCachedSpans(key)
                                if (spans.isEmpty()) return@runCatching null
                                val oldestTouch = spans.minOf { it.lastTouchTimestamp }
                                val sizeBytes = spans.sumOf { it.length }
                                Candidate(key = key, lastTouchTimestamp = oldestTouch, sizeBytes = sizeBytes)
                            }.getOrNull()
                        }.sortedBy { it.lastTouchTimestamp }
                }.getOrNull().orEmpty()

            for (candidate in candidates) {
                if (totalBytes <= limitBytes) break
                val removedSize = candidate.sizeBytes.coerceAtLeast(0L)
                runCatching { playerCache.removeResource(candidate.key) }
                totalBytes -= removedSize
            }
        }
    }

    suspend fun trimPlayerCacheToBytes(
        limitBytes: Long,
        cacheOps: PlaybackRecoveryEngine.CacheOps,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        trimPlayerCacheToBytes(
            limitBytes = limitBytes,
            playerCache = cacheOps.playerCache,
            getPlayerCacheDirectorySizeBytes = cacheOps::getPlayerCacheDirectorySizeBytes,
            ioDispatcher = ioDispatcher,
        )
    }

    val registeredCacheKeys: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun createAutomixCacheListener(
        playerCache: Cache,
        tag: String = "MusicService",
        flacCacheKeyPrefix: String = "flac_",
        onSpanAdded: (cache: Cache, key: String, mediaId: String) -> Unit,
    ): Cache.Listener =
        object : Cache.Listener {
            override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                val key = span.key
                timber.log.Timber.tag(tag).d("Automix cache onSpanAdded key=$key length=${span.length} cached=${cache.isCached(key, span.position, span.length)}")
                val mediaId =
                    if (key.startsWith(flacCacheKeyPrefix)) {
                        key.removePrefix(flacCacheKeyPrefix)
                    } else {
                        key
                    }
                if (mediaId.isNotBlank()) {
                    onSpanAdded(cache, key, mediaId)
                }
            }

            override fun onSpanRemoved(cache: Cache, span: CacheSpan) {}

            override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {}
        }
}
