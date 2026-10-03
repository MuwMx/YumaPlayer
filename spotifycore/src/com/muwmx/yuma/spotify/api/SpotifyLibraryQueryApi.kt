/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.api

import io.ktor.client.request.parameter
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyParsers
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyRecentlyPlayed
import moe.rukamori.archivetune.spotify.models.SpotifySavedTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.models.SpotifyUser
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str

object SpotifyLibraryQueryApi {
    suspend fun me(): Result<SpotifyUser> =
        runCatching {
            try {
                val response = SpotifyGraphqlClient.graphqlPost(operationName = "profileAttributes")
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

            val pagingInfo = tracksData.obj("pagingInfo")

            val savedTracks =
                tracksData.arr("items")?.mapNotNull { elem ->
                    val trackWrapper = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    val trackData = trackWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = trackWrapper.str("_uri") ?: trackWrapper.str("uri")
                    val track = SpotifyParsers.parseGqlTrack(trackData, uriOverride = wrapperUri)
                    if (track.id.isBlank() || track.name.isBlank()) return@mapNotNull null
                    SpotifySavedTrack(track = track)
                } ?: emptyList()

            val total = tracksData.int("totalCount") ?: 0
            SpotifyGraphqlClient.log("D", "likedSongs GQL: received ${savedTracks.size} tracks, total: $total")

            SpotifyPaging(
                items = savedTracks,
                total = total,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }

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
}
