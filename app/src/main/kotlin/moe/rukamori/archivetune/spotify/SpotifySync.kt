/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Spotui / ArchiveTune (2026) | Original work by © Spotui & Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.spotify.sync.SpotifyLibraryMutations
import moe.rukamori.archivetune.spotify.sync.SpotifyTokenManager

object SpotifySync {
    typealias ResolvedSpotifyMatch = SpotifyLibraryMutations.ResolvedSpotifyMatch

    fun syncLike(spotifyId: String, isLiked: Boolean) {
        SpotifyLibraryMutations.syncLike(spotifyId, isLiked)
    }

    fun syncLike(context: Context, spotifyId: String, isLiked: Boolean) {
        SpotifyLibraryMutations.syncLike(context, spotifyId, isLiked)
    }

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) {
        SpotifyLibraryMutations.setTrackSaved(context, trackId, saved)
    }

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) {
        SpotifyLibraryMutations.setAlbumSaved(context, albumId, saved)
    }

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) {
        SpotifyLibraryMutations.setArtistFollowed(context, artistId, followed)
    }

    fun syncLikeForSong(
        context: Context,
        database: MusicDatabase,
        song: SongEntity,
        isLiked: Boolean,
        explicitSpotifyId: String? = null,
    ) {
        SpotifyLibraryMutations.syncLikeForSong(context, database, song, isLiked, explicitSpotifyId)
    }

    fun syncLikeForSongs(
        context: Context,
        database: MusicDatabase,
        songs: Collection<SongEntity>,
    ) {
        SpotifyLibraryMutations.syncLikeForSongs(context, database, songs)
    }

    suspend fun ensureToken(context: Context): Boolean =
        SpotifyTokenManager.ensureToken(context)
}
