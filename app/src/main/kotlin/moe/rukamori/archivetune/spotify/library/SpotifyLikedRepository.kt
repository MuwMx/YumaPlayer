/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLikedRepository
    @Inject
    constructor(
        private val sessionRepository: SpotifySessionRepository,
        private val cacheStore: SpotifyCacheStore,
    ) {
        private val _likedSongsTotal = MutableStateFlow(0)
        val likedSongsTotal: StateFlow<Int> = _likedSongsTotal.asStateFlow()

        private val _likedSongs = MutableStateFlow<List<SpotifyTrack>>(emptyList())
        val likedSongs: StateFlow<List<SpotifyTrack>> = _likedSongs.asStateFlow()

        suspend fun restoreCachedLikedSongs() {
            withContext(Dispatchers.IO) {
                if (_likedSongs.value.isNotEmpty()) return@withContext
                val cached = cacheStore.loadLikedSongs()
                if (cached.isNotEmpty()) {
                    _likedSongs.value = cached
                }
            }
        }

        suspend fun likedSongsPage(
            limit: Int = 50,
            offset: Int = 0,
        ): SpotifyPaging<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                val page =
                    sessionRepository.spotifyCallWithTokenRetry {
                        Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                    }
                val filtered = page.items.mapNotNull { it.track.takeUnless(SpotifyTrack::isLocal) }
                Timber.tag("SpotifyPipeline").d(
                    "likedSongsPage: received ${filtered.size} tracks, total=${page.total}"
                )
                SpotifyPaging(
                    items = filtered,
                    total = page.total,
                    limit = page.limit,
                    offset = page.offset,
                )
            }

        suspend fun refreshLikedSongsTotal() {
            withContext(Dispatchers.IO) {
                runCatching {
                    sessionRepository.ensureAuthenticated()
                    sessionRepository.spotifyCallWithTokenRetry {
                        Spotify.likedSongs(limit = 1, offset = 0).getOrThrow().total
                    }
                }.onSuccess { total ->
                    _likedSongsTotal.value = total
                }.onFailure { error ->
                    reportException(error)
                }
            }
        }

        suspend fun refreshLikedSongs() =
            withContext(Dispatchers.IO) {
                sessionRepository.ensureAuthenticated()
                val accumulated = mutableListOf<SpotifyTrack>()
                var offset = 0
                val limit = 50
                while (currentCoroutineContext().isActive) {
                    val page =
                        sessionRepository.spotifyCallWithTokenRetry {
                            Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                        }
                    if (page.items.isEmpty()) break
                    val pageTracks = page.items.mapNotNull { it.track.takeUnless(SpotifyTrack::isLocal) }
                    if (pageTracks.isNotEmpty()) {
                        accumulated += pageTracks
                        _likedSongs.value = accumulated.toList()
                    }
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
                }
                if (accumulated.isNotEmpty()) {
                    cacheStore.saveLikedSongs(accumulated)
                }
            }

        internal fun clear() {
            _likedSongs.value = emptyList()
            _likedSongsTotal.value = 0
        }
    }
