/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.Cache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.playback.resolvers.getFormatForSource
import moe.rukamori.archivetune.playlist.cache.CachedSong
import moe.rukamori.archivetune.playlist.cache.evaluateEligibleCachedSong
import moe.rukamori.archivetune.playlist.cache.extractCompletedDownloadKeys
import moe.rukamori.archivetune.playlist.cache.fetchSongsInBatches
import moe.rukamori.archivetune.playlist.cache.findPureCacheIds
import moe.rukamori.archivetune.playlist.cache.removeSongResources
import moe.rukamori.archivetune.playlist.cache.runPollingLoop
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "CachePlaylistViewModel"
private const val BATCH_SIZE = 400
private const val POLL_INTERVAL_MS = 1000L

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
