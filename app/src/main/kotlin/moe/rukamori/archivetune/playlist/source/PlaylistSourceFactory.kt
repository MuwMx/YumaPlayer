/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import android.content.Context
import androidx.media3.datasource.cache.Cache
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        private val downloadUtil: DownloadUtil,
        private val spotifyRepository: SpotifyLibraryRepository,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
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
            "auto_playlist" -> AutoPlaylistSource(context, arg, database, downloadUtil, syncUtils)
            "cache_playlist" -> CachePlaylistSource(context, database, downloadUtil, playerCache, downloadCache)
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
