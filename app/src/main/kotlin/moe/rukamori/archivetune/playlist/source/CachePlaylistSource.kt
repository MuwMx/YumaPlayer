/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import android.content.Context
import androidx.media3.datasource.cache.Cache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.playback.engine.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.engine.flacCacheKey
import moe.rukamori.archivetune.playback.engine.flacStreamCacheKey
import moe.rukamori.archivetune.ui.screens.playlist.hasUncachedPlayerSource
import moe.rukamori.archivetune.playback.engine.ytStreamCacheKey
import moe.rukamori.archivetune.playback.resolvers.getFormatForSource
import moe.rukamori.archivetune.ui.screens.playlist.CachedSong
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Source for the cache playlist ("cache_playlist/{playlist}"), mirroring
 * CachePlaylistViewModel's updateCacheState: pure-cache media ids only
 * (cached but NOT completed downloads), refreshed on a periodic poll while
 * collected.
 */
class CachePlaylistSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : PlaylistTracksSource {
    override val id: String = "cache:all"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.ReadOnly

    override fun observe(): Flow<PlaylistTracksResult> =
        flow {
            while (true) {
                emit(computeSnapshot())
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun computeSnapshot(): PlaylistTracksResult {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val playerKeys = playerCache.keys
        val downloadCacheKeys = downloadCache.keys
        val allCachedKeys = playerKeys + downloadCacheKeys
        val completedDownloadKeys =
            downloadUtil.downloads.value.values
                .filter { it.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED }
                .mapNotNull { download ->
                    download.request.customCacheKey?.takeIf(String::isNotBlank)
                        ?: download.request.id.takeIf(String::isNotBlank)
                }.toSet()

        val pureCacheIds = findPureCacheIds(allCachedKeys, completedDownloadKeys)
        val songs = pureCacheIds.chunked(BATCH_SIZE).flatMap { batch -> database.getSongsByIds(batch) }

        val now = LocalDateTime.now()
        val evaluated = songs.mapNotNull { song ->
            val mediaId = song.id
            val storedYtLength = database.getFormatForSource(mediaId, PlaybackSource.YT_MUSIC)?.contentLength?.takeIf { it > 0L } ?: -1L
            val storedFlacLength = database.getFormatForSource(mediaId, PlaybackSource.FLAC)?.contentLength?.takeIf { it > 0L } ?: -1L
            moe.rukamori.archivetune.ui.screens.playlist.evaluateEligibleCachedSong(
                song = song,
                playerCache = playerCache,
                downloadCache = downloadCache,
                storedYtLength = storedYtLength,
                storedFlacLength = storedFlacLength,
                completedDownloadKeys = completedDownloadKeys,
            )
        }

        val withDownloadDates =
            evaluated.map { cachedSong ->
                if (cachedSong.song.song.dateDownload == null) {
                    cachedSong.copy(song = cachedSong.song.copy(song = cachedSong.song.song.copy(dateDownload = now)))
                } else {
                    cachedSong
                }
            }

        return PlaylistTracksResult(
            header = null,
            tracks =
                withDownloadDates
                    .asSequence()
                    .filter { !hideExplicit || !it.song.song.explicit }
                    .filter { it.song.artists.none { artist -> artist.blockedAt != null } }
                    .sortedByDescending { it.song.song.dateDownload }
                    .map { it.toPlaylistTrack() }
                    .toList(),
        )
    }

    private fun findPureCacheIds(
        allCachedKeys: Set<String>,
        completedDownloadKeys: Set<String>,
    ): Set<String> {
        val allMediaIds = allCachedKeys.map(::extractMediaIdFromCacheKey).filter(String::isNotBlank).toSet()
        return allMediaIds.filter { mediaId ->
            hasUncachedPlayerSource(mediaId, allCachedKeys, completedDownloadKeys)
        }.toSet()
    }

    private companion object {
        const val BATCH_SIZE = 400
        const val POLL_INTERVAL_MS = 1000L
    }
}
