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
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
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
         *
         * Pages read in parallel only fit together if the playlist stays the same while they are read. Every
         * page reports the playlist's total; if one differs from the first page's, or a page comes back short,
         * the playlist changed in between (or the server answered oddly), and the read starts over. After
         * [PARALLEL_READ_ATTEMPTS] such reads it falls back to reading one page after the other.
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

                val items =
                    (1..PARALLEL_READ_ATTEMPTS).firstNotNullOfOrNull { readInParallel(::page) }
                        ?: readInSequence(::page)
                items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
            }

        /** The playlist read in parallel pages, or null if the pages don't fit together. */
        private suspend fun readInParallel(
            page: suspend (offset: Int, limit: Int) -> SpotifyPaging<SpotifyPlaylistTrack>,
        ): List<SpotifyPlaylistTrack>? {
            val first = page(0, TRACK_PAGE_SIZE)
            val total = first.total
            if (first.items.isEmpty() || first.items.size >= total) return first.items.takeIf { it.size == total }

            // the server may hand out smaller pages than asked for; then the rest is asked for in that size
            val pageSize = first.items.size
            val permits = Semaphore(TRACK_PAGE_PARALLELISM)
            val laterPages =
                coroutineScope {
                    (pageSize until total step pageSize)
                        .map { offset -> async { offset to permits.withPermit { page(offset, pageSize) } } }
                        .awaitAll()
                }

            val items = ArrayList<SpotifyPlaylistTrack>(total)
            items += first.items
            for ((offset, later) in laterPages) {
                if (later.total != total || later.items.size != minOf(pageSize, total - offset)) return null
                items += later.items
            }
            return items
        }

        /** The playlist one page after the other, as before parallel reads; used when those keep failing to fit. */
        private suspend fun readInSequence(
            page: suspend (offset: Int, limit: Int) -> SpotifyPaging<SpotifyPlaylistTrack>,
        ): List<SpotifyPlaylistTrack> {
            val items = ArrayList<SpotifyPlaylistTrack>()
            while (true) {
                val next = page(items.size, SEQUENTIAL_PAGE_SIZE)
                if (next.items.isEmpty()) break
                items += next.items
                if (items.size >= next.total) break
            }
            return items
        }

        internal suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
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
                        if (playlist.tracks?.total != null) {
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
            const val PARALLEL_READ_ATTEMPTS = 2
            const val SEQUENTIAL_PAGE_SIZE = 100
        }
    }
