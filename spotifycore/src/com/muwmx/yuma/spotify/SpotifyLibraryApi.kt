/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.spotify.api.SpotifyAlbumApi
import moe.rukamori.archivetune.spotify.api.SpotifyArtistApi
import moe.rukamori.archivetune.spotify.api.SpotifyFeedApi
import moe.rukamori.archivetune.spotify.api.SpotifyLibraryQueryApi
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyRecentlyPlayed
import moe.rukamori.archivetune.spotify.models.SpotifyRecommendations
import moe.rukamori.archivetune.spotify.models.SpotifySavedTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.models.SpotifyUser

object SpotifyLibraryApi {
    suspend fun me(): Result<SpotifyUser> = SpotifyLibraryQueryApi.me()
    suspend fun myArtists(limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyArtist>> =
        SpotifyLibraryQueryApi.myArtists(limit, offset)
    suspend fun likedSongs(limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifySavedTrack>> =
        SpotifyLibraryQueryApi.likedSongs(limit, offset)
    suspend fun addToLibrary(uris: List<String>): Result<Unit> = SpotifyLibraryQueryApi.addToLibrary(uris)
    suspend fun removeFromLibrary(uris: List<String>): Result<Unit> = SpotifyLibraryQueryApi.removeFromLibrary(uris)
    suspend fun recentlyPlayed(limit: Int = 20): Result<SpotifyRecentlyPlayed> =
        SpotifyLibraryQueryApi.recentlyPlayed(limit)
    suspend fun topTracks(timeRange: String = "medium_term", limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyTrack>> =
        SpotifyLibraryQueryApi.topTracks(timeRange, limit, offset)
    suspend fun topArtists(timeRange: String = "medium_term", limit: Int = 50, offset: Int = 0): Result<SpotifyPaging<SpotifyArtist>> =
        SpotifyLibraryQueryApi.topArtists(timeRange, limit, offset)
    suspend fun recommendations(
        seedTrackIds: List<String> = emptyList(),
        seedArtistIds: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 50,
    ): Result<SpotifyRecommendations> = SpotifyFeedApi.recommendations(seedTrackIds, seedArtistIds, seedGenres, limit)
    suspend fun newReleases(limit: Int = 20, offset: Int = 0): Result<NewReleasesResponse> =
        SpotifyFeedApi.newReleases(limit, offset)
    suspend fun home(
        sectionItemsLimit: Int = 10,
        timeZone: String = java.util.TimeZone.getDefault().id,
    ): Result<SpotifyHomeFeed> = SpotifyFeedApi.home(sectionItemsLimit, timeZone)
    suspend fun album(albumId: String): Result<SpotifyAlbum> = SpotifyAlbumApi.album(albumId)
    suspend fun artist(artistId: String): Result<SpotifyArtist> = SpotifyArtistApi.artist(artistId)
    suspend fun artistTopTracks(artistId: String, market: String = "US"): Result<ArtistTopTracksResponse> =
        SpotifyArtistApi.artistTopTracks(artistId, market)
    suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>> =
        SpotifyArtistApi.artistRelatedArtists(artistId)
    suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> =
        SpotifyArtistApi.relatedArtists(artistId)
}

@Serializable
data class ArtistTopTracksResponse(val tracks: List<SpotifyTrack> = emptyList())

@Serializable
data class RelatedArtistsResponse(val artists: List<SpotifyArtist> = emptyList())

@Serializable
data class NewReleasesResponse(val albums: SpotifyPaging<SpotifyAlbum>? = null)
