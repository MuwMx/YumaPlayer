/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.util.Log
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

        fun likeSong(s: SongEntity, explicitSpotifyId: String? = null) {
            Log.d("SPLIT_STRESS", "FACADE likeSong id=${s.id} thread=${Thread.currentThread().name}")
            syncLikes.likeSong(s, explicitSpotifyId)
        }

        fun likeSongs(songs: List<SongEntity>) {
            Log.d("SPLIT_STRESS", "FACADE likeSongs n=${songs.size} thread=${Thread.currentThread().name}")
            syncLikes.likeSongs(songs)
        }

        suspend fun syncLikedSongs(authoritative: Boolean = false) {
            Log.d("SPLIT_STRESS", "FACADE->YTM syncLikedSongs auth=$authoritative thread=${Thread.currentThread().name}")
            ytmSync.syncLikedSongs(authoritative)
        }

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
        ) {
            Log.d("SPLIT_STRESS", "FACADE->SPOT syncSpotifyLikedSongs auth=$authoritative thread=${Thread.currentThread().name}")
            spotifySyncOps.syncSpotifyLikedSongs(authoritative, onProgress)
        }

        fun trySpotifyAutoSync(authoritative: Boolean = false) = spotifySyncOps.trySpotifyAutoSync(authoritative)
    }
