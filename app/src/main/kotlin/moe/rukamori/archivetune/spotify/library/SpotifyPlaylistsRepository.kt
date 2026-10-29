/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyPlaylistsRepository
    @Inject
    constructor(
        private val sessionRepository: SpotifySessionRepository,
        private val cacheStore: SpotifyCacheStore,
    ) {
        private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
        val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        suspend fun restoreCachedPlaylists() {
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext
                val cached = cacheStore.loadPlaylists()
                if (cached.isNotEmpty()) {
                    _playlists.value = cached
                }
            }
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                _isRefreshing.value = true
                _errorMessage.value = null
                try {
                    sessionRepository.ensureAuthenticated()
                    val loaded = fetchAllPlaylists()
                    _playlists.value = loaded
                    cacheStore.savePlaylists(loaded)
                    loaded
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _errorMessage.value = error.message
                    _playlists.value
                } finally {
                    _isRefreshing.value = false
                }
            }

        suspend fun playlist(playlistId: String): SpotifyPlaylist =
            withContext(Dispatchers.IO) {
                sessionRepository.ensureAuthenticated()
                sessionRepository.spotifyCallWithTokenRetry {
                    Spotify.playlist(playlistId).getOrThrow()
                }
            }

        /**
         * Reads the whole playlist. The first page reveals the total, then the
         * remaining pages are requested in parallel.
         */
        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                sessionRepository.ensureAuthenticated()

                suspend fun page(
                    offset: Int,
                    limit: Int,
                ) = sessionRepository.spotifyCallWithTokenRetry {
                    Spotify
                        .playlistTracks(
                            playlistId = playlistId,
                            limit = limit,
                            offset = offset,
                        ).getOrThrow()
                }

                val first = page(offset = 0, limit = TRACK_PAGE_SIZE)
                if (first.items.isEmpty()) return@withContext emptyList()

                val permits = Semaphore(TRACK_PAGE_PARALLELISM)
                val laterPages =
                    coroutineScope {
                        (TRACK_PAGE_SIZE until first.total step TRACK_PAGE_SIZE)
                            .map { offset ->
                                async { offset to permits.withPermit { page(offset, TRACK_PAGE_SIZE).items } }
                            }.awaitAll()
                    }

                val items = ArrayList<SpotifyPlaylistTrack>(first.total)
                items += first.items
                for ((offset, pageItems) in laterPages) {
                    // A page that came back short leaves a gap that has to be read before this one.
                    while (items.size < offset) {
                        val gap = page(offset = items.size, limit = offset - items.size).items
                        if (gap.isEmpty()) break
                        items += gap
                    }
                    if (items.size == offset) items += pageItems
                }
                while (items.size < first.total) {
                    val tail = page(offset = items.size, limit = TRACK_PAGE_SIZE).items
                    if (tail.isEmpty()) break
                    items += tail
                }

                items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
            }

        /**
         * The library's playlists without the extra request [refreshPlaylists] makes for each
         * playlist whose track count Spotify leaves out. Does not update [playlists].
         */
        suspend fun playlistsWithoutCounts(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                sessionRepository.ensureAuthenticated()
                fetchAllPlaylists(fillMissingCounts = false)
            }

        internal suspend fun fetchAllPlaylists(fillMissingCounts: Boolean = true): List<SpotifyPlaylist> {
            val playlists = ArrayList<SpotifyPlaylist>()
            var offset = 0
            val limit = 50

            while (true) {
                val page =
                    sessionRepository.spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                if (page.items.isEmpty()) break
                playlists +=
                    page.items.map { playlist ->
                        if (!fillMissingCounts || playlist.tracks?.total != null) {
                            playlist
                        } else {
                            playlistTrackCount(playlist.id)
                                ?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                                ?: playlist
                        }
                    }
                offset += page.items.size
                if (offset >= page.total || page.items.size < limit) break
            }

            return playlists
        }

        internal suspend fun playlistTrackCount(playlistId: String): Int? =
            try {
                sessionRepository.spotifyCallWithTokenRetry {
                    Spotify
                        .playlistTracks(
                            playlistId = playlistId,
                            limit = 1,
                            offset = 0,
                        ).getOrThrow()
                }.total
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportException(error)
                null
            }

        internal fun clear() {
            _playlists.value = emptyList()
            _errorMessage.value = null
        }

        private companion object {
            const val TRACK_PAGE_SIZE = 500
            const val TRACK_PAGE_PARALLELISM = 6
        }
    }
