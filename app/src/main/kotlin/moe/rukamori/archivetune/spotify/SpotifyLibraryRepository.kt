/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.spotify.library.SpotifyCacheStore
import moe.rukamori.archivetune.spotify.library.SpotifyLikedRepository
import moe.rukamori.archivetune.spotify.library.SpotifyPlaylistsRepository
import moe.rukamori.archivetune.spotify.library.SpotifySessionRepository
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val profileCache: SpotifyProfileCache,
    ) {
        private val cacheStore = SpotifyCacheStore(context)
        private val sessionRepository = SpotifySessionRepository(context, profileCache)
        private val playlistsRepository = SpotifyPlaylistsRepository(sessionRepository, cacheStore)
        private val likedRepository = SpotifyLikedRepository(sessionRepository, cacheStore)

        val playlists: StateFlow<List<SpotifyPlaylist>> = playlistsRepository.playlists
        val isRefreshing: StateFlow<Boolean> = playlistsRepository.isRefreshing
        val errorMessage: StateFlow<String?> = playlistsRepository.errorMessage
        val likedSongsTotal: StateFlow<Int> = likedRepository.likedSongsTotal
        val likedSongs: StateFlow<List<SpotifyTrack>> = likedRepository.likedSongs

        suspend fun likedSongsPage(
            limit: Int = 50,
            offset: Int = 0,
        ): SpotifyPaging<SpotifyTrack> = likedRepository.likedSongsPage(limit, offset)

        suspend fun refreshLikedSongsTotal() = likedRepository.refreshLikedSongsTotal()

        suspend fun restoreCachedPlaylists() = playlistsRepository.restoreCachedPlaylists()

        suspend fun restoreCachedLikedSongs() = likedRepository.restoreCachedLikedSongs()

        suspend fun restoreSession(): SpotifyAccountSession = sessionRepository.restoreSession()

        suspend fun connectWithCookies(
            spDc: String,
            spKey: String,
        ): SpotifyAccountSession {
            playlistsRepository.clear()
            likedRepository.clear()
            return sessionRepository.connectWithCookies(spDc, spKey)
        }

        suspend fun logout() {
            sessionRepository.logout()
            playlistsRepository.clear()
            likedRepository.clear()
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> = playlistsRepository.refreshPlaylists()

        suspend fun playlist(playlistId: String): SpotifyPlaylist = playlistsRepository.playlist(playlistId)

        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> = playlistsRepository.playlistTracks(playlistId)

        suspend fun refreshLikedSongs() = likedRepository.refreshLikedSongs()
    }

data class SpotifyAccountSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)
