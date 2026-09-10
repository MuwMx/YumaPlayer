/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyRecentlyPlayed
import moe.rukamori.archivetune.spotify.models.SpotifyRecommendations
import moe.rukamori.archivetune.spotify.models.SpotifySavedTrack
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.models.SpotifyUser

object SpotifyLibraryApi {
    // ── User Profile (GQL with REST fallback) ──────────────────────────

    suspend fun me(): Result<SpotifyUser> =
        runCatching {
            try {
                val response =
                    SpotifyGraphqlClient.graphqlPost(
                        operationName = "profileAttributes",
                    )
                val profile =
                    response.obj("data")?.obj("me")?.obj("profile")
                        ?: throw SpotifyException(500, "Invalid profileAttributes response")

                val uri = profile.str("uri") ?: ""
                SpotifyUser(
                    id = uri.substringAfterLast(":"),
                    displayName = profile.str("name"),
                    email = null,
                    images = SpotifyParsers.parseGqlImages(profile.obj("avatar")?.arr("sources")),
                )
            } catch (e: Exception) {
                SpotifyGraphqlClient.log("W", "GQL me() failed, falling back to REST: ${e.message}")
                SpotifyGraphqlClient.authenticatedGet<SpotifyUser>("me")
            }
        }

    // ── Library Artists (GQL: libraryV3 with Artists filter) ───────────

    suspend fun myArtists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("filters") { add("Artists") }
                    put("order", null as String?)
                    put("textFilter", "")
                    putJsonArray("features") {
                        add("LIKED_SONGS")
                        add("YOUR_EPISODES_V2")
                        add("PRERELEASES")
                        add("EVENTS")
                    }
                    put("limit", limit)
                    put("offset", offset)
                    put("flatten", false)
                    putJsonArray("expandedFolders") {}
                    put("folderUri", null as String?)
                    put("includeFoldersWhenFlattening", true)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "libraryV3",
                    variables = vars,
                )

            val libraryData =
                response.obj("data")?.obj("me")?.obj("libraryV3")
                    ?: throw SpotifyException(500, "Invalid libraryV3 response")

            val totalCount = libraryData.int("totalCount") ?: 0
            val pagingInfo = libraryData.obj("pagingInfo")

            val artists =
                libraryData.arr("items")?.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    val typeName = wrapper.str("__typename") ?: ""
                    if (!typeName.contains("Artist", ignoreCase = true)) return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null

                    val artistUri = wrapper.str("_uri") ?: data.str("uri") ?: return@mapNotNull null
                    val artistId = artistUri.substringAfterLast(":")

                    val name =
                        data.obj("profile")?.str("name")
                            ?: data.str("name")
                            ?: return@mapNotNull null

                    val images =
                        data
                            .obj("visuals")
                            ?.obj("avatarImage")
                            ?.arr("sources")
                            ?.let { SpotifyParsers.parseGqlImages(it) }
                            ?: emptyList()

                    SpotifyArtist(
                        id = artistId,
                        name = name,
                        images = images,
                        uri = artistUri,
                    )
                } ?: emptyList()

            SpotifyPaging(
                items = artists,
                total = totalCount,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }

    // ── Liked Songs (GQL: fetchLibraryTracks) ───────────────────────────

    suspend fun likedSongs(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifySavedTrack>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("offset", offset)
                    put("limit", limit)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "fetchLibraryTracks",
                    variables = vars,
                )

            val tracksData =
                response
                    .obj("data")
                    ?.obj("me")
                    ?.obj("library")
                    ?.obj("tracks")
                    ?: throw SpotifyException(500, "Invalid fetchLibraryTracks response")

            val savedTracks =
                tracksData.arr("items")?.mapNotNull { elem ->
                    val trackWrapper = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    val trackData = trackWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = trackWrapper.str("_uri") ?: trackWrapper.str("uri")
                    SpotifySavedTrack(track = SpotifyParsers.parseGqlTrack(trackData, uriOverride = wrapperUri))
                } ?: emptyList()

            SpotifyPaging(
                items = savedTracks,
                total = tracksData.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }

    // ── Library Mutations (GQL: addToLibrary / removeFromLibrary) ──────

    /**
     * Saves tracks/albums/playlists to the user's Spotify library (like).
     * @param uris Full Spotify URIs, e.g. `["spotify:track:abc123"]`.
     */
    suspend fun addToLibrary(uris: List<String>): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("libraryItemUris") {
                        uris.forEach { add(it) }
                    }
                }
            SpotifyGraphqlClient.graphqlPost(
                operationName = "addToLibrary",
                variables = vars,
            )
            SpotifyGraphqlClient.log("D", "addToLibrary: added ${uris.size} items")
        }

    /**
     * Removes tracks/albums/playlists from the user's Spotify library (unlike).
     * @param uris Full Spotify URIs, e.g. `["spotify:track:abc123"]`.
     */
    suspend fun removeFromLibrary(uris: List<String>): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("libraryItemUris") {
                        uris.forEach { add(it) }
                    }
                }
            SpotifyGraphqlClient.graphqlPost(
                operationName = "removeFromLibrary",
                variables = vars,
            )
            SpotifyGraphqlClient.log("D", "removeFromLibrary: removed ${uris.size} items")
        }

    // ── Top Tracks (REST fallback — no GQL equivalent) ──────────────────

    suspend fun recentlyPlayed(limit: Int = 20): Result<SpotifyRecentlyPlayed> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("me/player/recently-played", failFastOn429 = true) {
                parameter("limit", limit)
            }
        }

    suspend fun topTracks(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyTrack>> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("me/top/tracks", failFastOn429 = true) {
                parameter("time_range", timeRange)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    // ── Top Artists (REST fallback — no GQL equivalent) ─────────────────

    suspend fun topArtists(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("me/top/artists", failFastOn429 = true) {
                parameter("time_range", timeRange)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    // ── Recommendations (REST fallback — no GQL equivalent) ─────────────

    suspend fun recommendations(
        seedTrackIds: List<String> = emptyList(),
        seedArtistIds: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 50,
    ): Result<SpotifyRecommendations> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("recommendations") {
                if (seedTrackIds.isNotEmpty()) parameter("seed_tracks", seedTrackIds.joinToString(","))
                if (seedArtistIds.isNotEmpty()) parameter("seed_artists", seedArtistIds.joinToString(","))
                if (seedGenres.isNotEmpty()) parameter("seed_genres", seedGenres.joinToString(","))
                parameter("limit", limit)
            }
        }

    // ── Browse: New Releases (GQL: queryWhatsNewFeed) ───────────────────

    suspend fun newReleases(
        limit: Int = 20,
        offset: Int = 0,
    ): Result<NewReleasesResponse> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("offset", offset)
                    put("limit", limit)
                    put("onlyUnPlayedItems", false)
                    putJsonArray("includedContentTypes") { add("ALBUM") }
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "queryWhatsNewFeed",
                    variables = vars,
                )

            val feedData =
                response.obj("data")?.obj("whatsNewFeedItems")
                    ?: throw SpotifyException(500, "Invalid queryWhatsNewFeed response")

            val pagingInfo = feedData.obj("pagingInfo")

            val albums =
                feedData.arr("items")?.mapNotNull { elem ->
                    val content = elem.jsonObject.obj("content") ?: return@mapNotNull null
                    if (content.str("__typename") != "AlbumResponseWrapper") return@mapNotNull null
                    val data = content.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Album") return@mapNotNull null

                    val uri = data.str("uri") ?: return@mapNotNull null
                    SpotifyAlbum(
                        id = uri.substringAfterLast(":"),
                        name = data.str("name") ?: "",
                        albumType = data.str("albumType")?.lowercase(),
                        artists =
                            data.obj("artists")?.arr("items")?.mapNotNull {
                                SpotifyParsers.parseGqlSimpleArtist(it.jsonObject)
                            } ?: emptyList(),
                        images = SpotifyParsers.parseGqlImages(data.obj("coverArt")?.arr("sources")),
                        releaseDate = data.obj("date")?.str("isoString"),
                        uri = uri,
                    )
                } ?: emptyList()

            NewReleasesResponse(
                albums =
                    SpotifyPaging(
                        items = albums,
                        total = feedData.int("totalCount") ?: 0,
                        limit = pagingInfo?.int("limit") ?: limit,
                        offset = pagingInfo?.int("offset") ?: offset,
                    ),
            )
        }

    // ── Home feed (GQL: home) ──────────────────────────────────────────

    suspend fun home(
        sectionItemsLimit: Int = 10,
        timeZone: String =
            java.util.TimeZone
                .getDefault()
                .id,
    ): Result<SpotifyHomeFeed> =
        runCatching {
            SpotifyGraphqlClient.log("D", "spotifyHome: GQL home() request — timeZone=$timeZone limit=$sectionItemsLimit")
            val vars =
                buildJsonObject {
                    put("homeEndUserIntegration", "INTEGRATION_WEB_PLAYER")
                    put("timeZone", timeZone)
                    put("sp_t", "")
                    put("facet", "")
                    put("sectionItemsLimit", sectionItemsLimit)
                    put("includeEpisodeContentRatingsV2", false)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "home",
                    variables = vars,
                )

            val homeData =
                response.obj("data")?.obj("home")
                    ?: run {
                        SpotifyGraphqlClient.log("E", "spotifyHome: GQL response has no data.home — keys=${response.obj("data")?.keys}")
                        throw SpotifyException(500, "Invalid home response")
                    }

            val greeting = homeData.obj("greeting")?.str("transformedLabel")
            SpotifyGraphqlClient.log("D", "spotifyHome: GQL home() OK greeting='$greeting'")

            val sectionElements =
                homeData
                    .obj("sectionContainer")
                    ?.obj("sections")
                    ?.arr("items")
                    ?: run {
                        SpotifyGraphqlClient.log("W", "spotifyHome: no sectionContainer.sections.items in response")
                        return@runCatching SpotifyHomeFeed(
                            greeting = greeting,
                            sections = emptyList(),
                        )
                    }

            SpotifyGraphqlClient.log("D", "spotifyHome: parsing ${sectionElements.size} raw sections")
            val sections =
                sectionElements.mapNotNull { elem ->
                    SpotifyParsers.parseHomeSection(elem.jsonObject)
                }
            SpotifyGraphqlClient.log("D", "spotifyHome: parsed ${sections.size}/${sectionElements.size} sections successfully")

            SpotifyHomeFeed(
                greeting = greeting,
                sections = sections,
            )
        }

    // ── Albums (GQL: getAlbum) ──────────────────────────────────────────

    suspend fun album(albumId: String): Result<SpotifyAlbum> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:album:$albumId")
                    put("locale", "")
                    put("offset", 0)
                    put("limit", 50)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "getAlbum",
                    variables = vars,
                )

            val albumData =
                response.obj("data")?.obj("albumUnion")
                    ?: throw SpotifyException(500, "Invalid getAlbum response")

            val artists =
                albumData.obj("artists")?.arr("items")?.mapNotNull {
                    SpotifyParsers.parseGqlSimpleArtist(it.jsonObject)
                } ?: emptyList()

            val albumImages = SpotifyParsers.parseGqlImages(albumData.obj("coverArt")?.arr("sources"))

            val albumSimple =
                SpotifySimpleAlbum(
                    id = albumId,
                    name = albumData.str("name") ?: "",
                    images = albumImages,
                    releaseDate = albumData.obj("date")?.str("isoString"),
                    albumType = albumData.str("type")?.lowercase(),
                    artists = artists,
                    uri = "spotify:album:$albumId",
                )

            val tracksData = albumData.obj("tracksV2")
            val trackItems =
                tracksData?.arr("items")?.mapNotNull { elem ->
                    val trackObj = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    SpotifyParsers.parseGqlTrack(trackObj, albumOverride = albumSimple)
                } ?: emptyList()

            SpotifyAlbum(
                id = albumId,
                name = albumData.str("name") ?: "",
                albumType = albumData.str("type")?.lowercase(),
                artists = artists,
                images = albumImages,
                releaseDate = albumData.obj("date")?.str("isoString"),
                totalTracks = tracksData?.int("totalCount") ?: 0,
                tracks = SpotifyPaging(items = trackItems, total = tracksData?.int("totalCount") ?: 0),
                uri = "spotify:album:$albumId",
            )
        }

    // ── Artists (GQL: queryArtistOverview) ───────────────────────────────

    suspend fun artist(artistId: String): Result<SpotifyArtist> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:artist:$artistId")
                    put("locale", "")
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "queryArtistOverview",
                    variables = vars,
                )

            val artistData =
                response.obj("data")?.obj("artistUnion")
                    ?: throw SpotifyException(500, "Invalid queryArtistOverview response")

            SpotifyArtist(
                id = artistId,
                name = artistData.obj("profile")?.str("name") ?: "",
                images = SpotifyParsers.parseGqlImages(artistData.obj("visuals")?.obj("avatarImage")?.arr("sources")),
                uri = "spotify:artist:$artistId",
            )
        }

    suspend fun artistTopTracks(
        artistId: String,
        market: String = "US",
    ): Result<ArtistTopTracksResponse> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:artist:$artistId")
                    put("locale", "")
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "queryArtistOverview",
                    variables = vars,
                )

            val artistData =
                response.obj("data")?.obj("artistUnion")
                    ?: throw SpotifyException(500, "Invalid queryArtistOverview response")

            val topTracksItems =
                artistData
                    .obj("discography")
                    ?.obj("topTracks")
                    ?.arr("items") ?: JsonArray(emptyList())

            val tracks =
                topTracksItems.mapNotNull { elem ->
                    val trackObj = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    SpotifyParsers.parseGqlTrack(trackObj)
                }

            ArtistTopTracksResponse(tracks = tracks)
        }

    /**
     * Extracts related artists from the GQL queryArtistOverview endpoint.
     * This avoids the rate-limited REST /related-artists endpoint entirely.
     */
    suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:artist:$artistId")
                    put("locale", "")
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "queryArtistOverview",
                    variables = vars,
                )

            val artistData =
                response.obj("data")?.obj("artistUnion")
                    ?: throw SpotifyException(500, "Invalid queryArtistOverview response")

            val relatedItems =
                artistData
                    .obj("relatedContent")
                    ?.obj("relatedArtists")
                    ?.arr("items")
                    ?: JsonArray(emptyList())

            relatedItems.mapNotNull { elem ->
                val uri = elem.jsonObject.str("uri") ?: return@mapNotNull null
                val id = uri.substringAfterLast(":")
                val name = elem.jsonObject.obj("profile")?.str("name") ?: return@mapNotNull null
                val images =
                    SpotifyParsers.parseGqlImages(
                        elem.jsonObject
                            .obj("visuals")
                            ?.obj("avatarImage")
                            ?.arr("sources"),
                    )
                SpotifyArtist(id = id, name = name, images = images, uri = uri)
            }
        }

    // ── Related Artists (REST fallback) ─────────────────────────────────

    suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("artists/$artistId/related-artists")
        }
}

@Serializable
data class ArtistTopTracksResponse(
    val tracks: List<SpotifyTrack> = emptyList(),
)

@Serializable
data class RelatedArtistsResponse(
    val artists: List<SpotifyArtist> = emptyList(),
)

@Serializable
data class NewReleasesResponse(
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
)
