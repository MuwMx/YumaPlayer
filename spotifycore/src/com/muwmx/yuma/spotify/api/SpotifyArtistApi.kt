/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.spotify.ArtistTopTracksResponse
import moe.rukamori.archivetune.spotify.RelatedArtistsResponse
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyParsers
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str

object SpotifyArtistApi {
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

    suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> =
        runCatching {
            SpotifyGraphqlClient.authenticatedGet("artists/$artistId/related-artists")
        }
}
