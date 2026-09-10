/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.SongEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils
    @Inject
    constructor(
        private val syncLikes: SyncLikes,
        private val ytmSync: YtmSync,
        private val spotifySyncOps: SpotifySyncOps,
    ) {
        suspend fun performFullSync(authoritative: Boolean = false) = ytmSync.performFullSync(authoritative)

        suspend fun cleanupDuplicatePlaylists() = ytmSync.cleanupDuplicatePlaylists()

        fun likeSong(s: SongEntity, explicitSpotifyId: String? = null) = syncLikes.likeSong(s, explicitSpotifyId)

        fun likeSongs(songs: List<SongEntity>) = syncLikes.likeSongs(songs)

        suspend fun syncLikedSongs(authoritative: Boolean = false) = ytmSync.syncLikedSongs(authoritative)

        suspend fun syncLibrarySongs(authoritative: Boolean = false) = ytmSync.syncLibrarySongs(authoritative)

        suspend fun syncLikedAlbums(authoritative: Boolean = false) = ytmSync.syncLikedAlbums(authoritative)

        suspend fun syncArtistsSubscriptions(authoritative: Boolean = false) = ytmSync.syncArtistsSubscriptions(authoritative)

        suspend fun syncSavedPlaylists(authoritative: Boolean = false) = ytmSync.syncSavedPlaylists(authoritative)

        suspend fun syncAutoSyncPlaylists() = ytmSync.syncAutoSyncPlaylists()

        suspend fun syncPlaylistNow(
            browseId: String,
            playlistId: String,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = ytmSync.syncPlaylistNow(
            browseId = browseId,
            playlistId = playlistId,
            propagateFailures = propagateFailures,
            onProgress = onProgress,
        )

        suspend fun syncSpotifyPlaylists(authoritative: Boolean = false) = spotifySyncOps.syncSpotifyPlaylists(authoritative)

        suspend fun syncSpotifyLikedSongs(
            authoritative: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = spotifySyncOps.syncSpotifyLikedSongs(authoritative, onProgress)

        fun trySpotifyAutoSync(authoritative: Boolean = false) = spotifySyncOps.trySpotifyAutoSync(authoritative)
    }
