/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.playback.engine.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.engine.flacCacheKey
import moe.rukamori.archivetune.playback.engine.flacStreamCacheKey
import moe.rukamori.archivetune.playlist.cache.evaluateEligibleCachedSong
import moe.rukamori.archivetune.playlist.cache.hasUncachedPlayerSource
import moe.rukamori.archivetune.playlist.cache.removeSongResources
import moe.rukamori.archivetune.playback.engine.ytStreamCacheKey
import moe.rukamori.archivetune.playback.resolvers.getFormatForSource
import moe.rukamori.archivetune.playlist.cache.CachedSong
import moe.rukamori.archivetune.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Source for the cache playlist ("cache_playlist/{playlist}"), mirroring
 * CachePlaylistViewModel's updateCacheState: pure-cache media ids only
 * (cached but NOT completed downloads), refreshed on a periodic poll while
 * collected. Sort persists to the legacy SongSortTypeKey/SongSortDescendingKey
 * DataStore pair (legacy CachePlaylistScreen.kt:148-178) and re-applies on
 * every poll tick.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachePlaylistSource
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
        private val preferences: DataStore<Preferences>,
    ) : PlaylistTracksSource {
    override val id: String = "cache:all"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Cache

    /**
     * Cache has no custom order; the unified sort header's CUSTOM selection
     * maps to CREATE_DATE (the legacy default) instead of failing.
     */
    private fun PlaylistSongSortType.toSongSortType(): SongSortType =
        when (this) {
            PlaylistSongSortType.CUSTOM -> SongSortType.CREATE_DATE
            PlaylistSongSortType.CREATE_DATE -> SongSortType.CREATE_DATE
            PlaylistSongSortType.NAME -> SongSortType.NAME
            PlaylistSongSortType.ARTIST -> SongSortType.ARTIST
            PlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
        }

    /** Reports the applied sort back in the unified enum space. */
    private fun SongSortType.toPlaylistSongSortType(): PlaylistSongSortType =
        when (this) {
            SongSortType.CREATE_DATE -> PlaylistSongSortType.CREATE_DATE
            SongSortType.NAME -> PlaylistSongSortType.NAME
            SongSortType.ARTIST -> PlaylistSongSortType.ARTIST
            SongSortType.PLAY_TIME -> PlaylistSongSortType.PLAY_TIME
        }

    override fun observe(): Flow<PlaylistTracksResult> =
        preferences.data
            .map { preferences ->
                preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (
                    preferences[SongSortDescendingKey] ?: true
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                // The poll stays INSIDE flatMapLatest so a sort change
                // restarts it with the new prefs; each tick re-reads the
                // caches (cache contents, not just the sort, change under
                // us — that is the whole point of the poll).
                flow {
                    while (true) {
                        emit(computeSnapshot(sortType, descending))
                        delay(POLL_INTERVAL_MS)
                    }
                }.flowOn(Dispatchers.IO)
            }

    private suspend fun computeSnapshot(
        sortType: SongSortType,
        descending: Boolean,
    ): PlaylistTracksResult {
        val hideExplicit = preferences.get(HideExplicitKey, false)
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
            evaluateEligibleCachedSong(
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

        val tracks =
            withDownloadDates
                .asSequence()
                .filter { !hideExplicit || !it.song.song.explicit }
                .filter { it.song.artists.none { artist -> artist.blockedAt != null } }
                .toList()
                // CACHE SORT SLOT — legacy CachePlaylistScreen.kt:158-178: sort
                // the FILTERED cached songs, then reverse on descending (every
                // type reverses; the cache has no CUSTOM exception).
                .let { sortCachedSongs(it, sortType).reversed(descending) }
                .map { it.toPlaylistTrack() }

        return PlaylistTracksResult(
            // CACHE HERO — legacy CachePlaylistHeroSection renders first-song
            // artwork, the localized title (resolved UI-side via
            // stringResource(R.string.cached_playlist), see
            // rememberPlaylistDetailHeader), plural count + duration chips
            // (CachePlaylistScreen.kt:288-296, 656-693).
            header =
                PlaylistHeaderInfo(
                    id = id,
                    title = "", // resolved UI-side
                    thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
                    songCount = tracks.size,
                    totalDurationMs = tracks.sumOf { it.durationMs ?: 0L },
                ),
            tracks = tracks,
            sortInfo = PlaylistSortInfo(sortType.toPlaylistSongSortType(), descending),
        )
    }

    /** Legacy sort selectors, unchanged from CachePlaylistScreen.kt:160-177. */
    private fun sortCachedSongs(
        songs: List<CachedSong>,
        sortType: SongSortType,
    ): List<CachedSong> =
        when (sortType) {
            SongSortType.CREATE_DATE -> songs.sortedBy { it.song.song.dateDownload ?: LocalDateTime.MIN }
            SongSortType.NAME -> songs.sortedBy { it.title }
            SongSortType.ARTIST -> songs.sortedBy { item -> item.artists.joinToString(separator = "") { artist -> artist.name } }
            SongSortType.PLAY_TIME -> songs.sortedBy { it.song.song.totalPlayTime }
        }

    /**
     * Persists the legacy SongSortTypeKey/SongSortDescendingKey pair (legacy
     * rememberEnumPreference write path); observe() re-emits with the new sort
     * via its DataStore-backed chain.
     */
    override suspend fun setSort(
        sortType: PlaylistSongSortType,
        descending: Boolean,
    ): Result<Unit> =
        runCatching {
            preferences.edit { preferences ->
                preferences[SongSortTypeKey] = sortType.toSongSortType().name
                preferences[SongSortDescendingKey] = descending
            }
        }

    /**
     * CACHE SLOT — remove-from-cache for the unified screen's selection
     * delete action and the SelectionSongMenu's cache branch, exactly the
     * legacy CachePlaylistViewModel.removeSongFromCache (which stays alive
     * as SongMenu's cache-removal service).
     */
    fun removeSongFromCache(songId: String) {
        removeSongResources(songId, playerCache)
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
