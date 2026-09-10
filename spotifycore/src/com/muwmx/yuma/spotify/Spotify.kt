/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyRecentlyPlayed
import moe.rukamori.archivetune.spotify.models.SpotifyRecommendations
import moe.rukamori.archivetune.spotify.models.SpotifySavedTrack
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.models.SpotifyUser

/**
 * Spotify API facade that delegates to domain-specific clients:
 * - [SpotifyGraphqlClient]: transport, authentication, HTTP client
 * - [SpotifyPlaylistApi]: playlist management, library hierarchy
 * - [SpotifySearchApi]: search
 * - [SpotifyLibraryApi]: user profile, liked tracks/artists, recommendations, albums, artists
 */
object Spotify {
    var accessToken: String?
        get() = SpotifyGraphqlClient.accessToken
        set(value) {
            SpotifyGraphqlClient.accessToken = value
        }

    class SpotifyException(
        val statusCode: Int,
        override val message: String,
        val retryAfterSec: Long = 0,
    ) : Exception(message)

    var logger: ((level: String, message: String) -> Unit)?
        get() = SpotifyGraphqlClient.logger
        set(value) {
            SpotifyGraphqlClient.logger = value
        }

    var onHashExpired: ((operationName: String) -> Unit)?
        get() = SpotifyGraphqlClient.onHashExpired
        set(value) {
            SpotifyGraphqlClient.onHashExpired = value
        }

    internal suspend fun graphqlPost(
        operationName: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject = SpotifyGraphqlClient.graphqlPost(operationName, variables)

    internal suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGraphqlClient.authenticatedGet(endpoint, failFastOn429, block)

    internal suspend inline fun <reified T> restGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGraphqlClient.restGet(endpoint, failFastOn429, block)

    // ── User Profile ───────────────────────────────────────────────────

    suspend fun me(): Result<SpotifyUser> = SpotifyLibraryApi.me()

    // ── Playlists & Library Hierarchy ──────────────────────────────────

    suspend fun myPlaylists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylist>> = SpotifyPlaylistApi.myPlaylists(limit, offset)

    suspend fun myLibraryNode(
        folderUri: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyLibraryItem>> = SpotifyPlaylistApi.myLibraryNode(folderUri, limit, offset)

    suspend fun myArtists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> = SpotifyLibraryApi.myArtists(limit, offset)

    suspend fun playlist(playlistId: String): Result<SpotifyPlaylist> = SpotifyPlaylistApi.playlist(playlistId)

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylistTrack>> = SpotifyPlaylistApi.playlistTracks(playlistId, limit, offset)

    suspend fun addTracksToPlaylist(
        playlistId: String,
        trackUris: List<String>,
    ): Result<Unit> = SpotifyPlaylistApi.addTracksToPlaylist(playlistId, trackUris)

    suspend fun removeTracksFromPlaylist(
        playlistId: String,
        items: List<PlaylistItemRef>,
    ): Result<Unit> = SpotifyPlaylistApi.removeTracksFromPlaylist(playlistId, items)

    suspend fun moveItemsInPlaylist(
        playlistId: String,
        uids: List<String>,
        beforeUid: String?,
    ): Result<Unit> = SpotifyPlaylistApi.moveItemsInPlaylist(playlistId, uids, beforeUid)

    suspend fun editPlaylistAttributes(
        playlistId: String,
        newName: String? = null,
        newDescription: String? = null,
    ): Result<Unit> = SpotifyPlaylistApi.editPlaylistAttributes(playlistId, newName, newDescription)

    /**
     * Reference to a specific item inside a playlist, needed for removal/reorder.
     */
    data class PlaylistItemRef(
        val uri: String,
        val uid: String,
    )

    // ── Liked Songs & Library Mutations ────────────────────────────────

    suspend fun likedSongs(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifySavedTrack>> = SpotifyLibraryApi.likedSongs(limit, offset)

    suspend fun addToLibrary(uris: List<String>): Result<Unit> = SpotifyLibraryApi.addToLibrary(uris)

    suspend fun removeFromLibrary(uris: List<String>): Result<Unit> = SpotifyLibraryApi.removeFromLibrary(uris)

    // ── Top Tracks & Recently Played ───────────────────────────────────

    suspend fun recentlyPlayed(limit: Int = 20): Result<SpotifyRecentlyPlayed> = SpotifyLibraryApi.recentlyPlayed(limit)

    suspend fun topTracks(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyTrack>> = SpotifyLibraryApi.topTracks(timeRange, limit, offset)

    suspend fun topArtists(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> = SpotifyLibraryApi.topArtists(timeRange, limit, offset)

    suspend fun recommendations(
        seedTrackIds: List<String> = emptyList(),
        seedArtistIds: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 50,
    ): Result<SpotifyRecommendations> = SpotifyLibraryApi.recommendations(seedTrackIds, seedArtistIds, seedGenres, limit)

    // ── Search ─────────────────────────────────────────────────────────

    suspend fun search(
        query: String,
        types: List<String> = listOf("track"),
        limit: Int = 20,
        offset: Int = 0,
    ): Result<SpotifySearchResult> = SpotifySearchApi.search(query, types, limit, offset)

    // ── Browse & Home ──────────────────────────────────────────────────

    suspend fun newReleases(
        limit: Int = 20,
        offset: Int = 0,
    ): Result<NewReleasesResponse> = SpotifyLibraryApi.newReleases(limit, offset)

    suspend fun home(
        sectionItemsLimit: Int = 10,
        timeZone: String =
            java.util.TimeZone
                .getDefault()
                .id,
    ): Result<SpotifyHomeFeed> = SpotifyLibraryApi.home(sectionItemsLimit, timeZone)

    // ── Albums & Artists ───────────────────────────────────────────────

    suspend fun album(albumId: String): Result<SpotifyAlbum> = SpotifyLibraryApi.album(albumId)

    suspend fun artist(artistId: String): Result<SpotifyArtist> = SpotifyLibraryApi.artist(artistId)

    suspend fun artistTopTracks(
        artistId: String,
        market: String = "US",
    ): Result<ArtistTopTracksResponse> = SpotifyLibraryApi.artistTopTracks(artistId, market)

    suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>> = SpotifyLibraryApi.artistRelatedArtists(artistId)

    suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> = SpotifyLibraryApi.relatedArtists(artistId)

    fun isAuthenticated(): Boolean = SpotifyGraphqlClient.isAuthenticated()
}
