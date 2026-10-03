/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str
import moe.rukamori.archivetune.spotify.models.SpotifyExternalIds
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

internal object SpotifyTrackParser {
    fun parseGqlImage(source: JsonObject): SpotifyImage? {
        val url = source.str("url") ?: return null
        return SpotifyImage(url = url, height = source.int("height"), width = source.int("width"))
    }

    fun parseGqlImages(sources: JsonArray?): List<SpotifyImage> =
        sources?.mapNotNull { parseGqlImage(it.jsonObject) } ?: emptyList()

    fun parseGqlSimpleArtist(artistObj: JsonObject): SpotifySimpleArtist? {
        val uri = artistObj.str("uri") ?: return null
        return SpotifySimpleArtist(
            id = uri.substringAfterLast(":"),
            name = artistObj.obj("profile")?.str("name") ?: "",
            uri = uri,
        )
    }

    fun parseGqlTrack(
        trackData: JsonObject,
        albumOverride: SpotifySimpleAlbum? = null,
        uriOverride: String? = null,
    ): SpotifyTrack {
        val uri =
            uriOverride
                ?: trackData.str("uri")
                ?: trackData.str("_uri")
                ?: ""
        val trackId = uri.substringAfterLast(":")

        val artists =
            trackData.obj("artists")?.arr("items")?.mapNotNull { elem ->
                parseGqlSimpleArtist(elem.jsonObject)
            } ?: emptyList()

        val album =
            albumOverride ?: run {
                val albumData = trackData.obj("albumOfTrack")
                val albumUri = albumData?.str("uri") ?: ""
                val albumId = albumUri.substringAfterLast(":")
                SpotifySimpleAlbum(
                    id = albumId,
                    name = albumData?.str("name") ?: "",
                    images = parseGqlImages(albumData?.obj("coverArt")?.arr("sources")),
                    uri = albumUri.ifEmpty { null },
                )
            }

        val isrc =
            trackData.obj("external_ids")?.str("isrc")
                ?: trackData.obj("externalIds")?.str("isrc")
                ?: trackData.str("isrc")
        val externalIds = isrc?.takeIf { it.isNotBlank() }?.let { SpotifyExternalIds(isrc = it) }

        val explicit =
            trackData.obj("contentRating")?.let { rating ->
                rating.str("label")?.equals("EXPLICIT", ignoreCase = true) == true ||
                    rating.str("name")?.equals("EXPLICIT", ignoreCase = true) == true
            } ?: run {
                val ratingStr = trackData.str("contentRating")
                if (ratingStr != null) {
                    ratingStr.equals("EXPLICIT", ignoreCase = true) || ratingStr.toBooleanStrictOrNull() == true
                } else {
                    trackData.str("explicit")?.toBooleanStrictOrNull() == true ||
                        try {
                            trackData["explicit"]?.jsonPrimitive?.booleanOrNull == true
                        } catch (_: Exception) {
                            false
                        }
                }
            }

        return SpotifyTrack(
            id = trackId,
            name = trackData.str("name") ?: "",
            artists = artists,
            album = album,
            durationMs = parseGqlTrackDurationMs(trackData),
            explicit = explicit,
            uri = uri.ifEmpty { null },
            externalIds = externalIds,
        )
    }

    fun parseGqlTrackDurationMs(trackData: JsonObject): Int {
        trackData.obj("duration")?.int("totalMilliseconds")?.let { if (it > 0) return it }
        trackData.int("durationMs")?.let { if (it > 0) return it }
        trackData.int("duration_ms")?.let { if (it > 0) return it }
        trackData.int("duration")?.let { sec -> if (sec > 0) return sec * 1000 }
        return 0
    }
}
