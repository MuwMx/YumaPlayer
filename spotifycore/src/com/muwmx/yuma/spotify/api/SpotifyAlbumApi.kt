/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.api

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyParsers
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str

object SpotifyAlbumApi {
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
}
