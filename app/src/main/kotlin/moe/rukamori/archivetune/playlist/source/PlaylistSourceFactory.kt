/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.datasource.cache.Cache
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.download.DownloadUtil
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.utils.SyncUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [PlaylistTracksSource] for a route, keyed off the same route
 * prefixes NavigationTabOwnership and MediaLibrarySessionCallback use:
 *
 *   local_playlist/{playlistId}     -> RoomPlaylistSource
 *   online_playlist/{playlistId}   -> InnerTubePlaylistSource
 *   spotify_playlist/{playlistId}   -> SpotifyPlaylistSource
 *   auto_playlist/{name}           -> AutoPlaylistSource
 *   cache_playlist/{name}          -> CachePlaylistSource
 *   top_playlist/{top}             -> TopPlaylistSource
 *
 * Sources holding live per-screen state (continuations, polling flows) are
 * created per call, NOT injected singletons.
 */
@Singleton
class PlaylistSourceFactory
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        private val downloadUtil: DownloadUtil,
        private val spotifyRepository: SpotifyLibraryRepository,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
        private val preferences: DataStore<Preferences>,
    ) {
    /** Route without arguments, e.g. "online_playlist". */
    fun sourceFor(
        route: String,
        arg: String,
    ): PlaylistTracksSource =
        when (route) {
            "local_playlist" -> RoomPlaylistSource(arg, database, syncUtils)
            "online_playlist" -> InnerTubePlaylistSource(arg, database)
            "spotify_playlist" -> SpotifyPlaylistSource(arg, spotifyRepository)
            "auto_playlist" -> AutoPlaylistSource(arg, database, downloadUtil, syncUtils, preferences)
            "cache_playlist" -> CachePlaylistSource(database, downloadUtil, playerCache, downloadCache, preferences)
            "top_playlist" -> TopPlaylistSource(arg, database)
            else -> throw IllegalArgumentException("Unknown playlist route: $route")
        }

    companion object {
        val ROUTES =
            setOf(
                "local_playlist",
                "online_playlist",
                "spotify_playlist",
                "auto_playlist",
                "cache_playlist",
                "top_playlist",
            )
    }
}
