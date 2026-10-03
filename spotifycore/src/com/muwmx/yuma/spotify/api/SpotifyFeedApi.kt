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
import moe.rukamori.archivetune.spotify.NewReleasesResponse
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyParsers
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyRecommendations
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str

object SpotifyFeedApi {
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
}
