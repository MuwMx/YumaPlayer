/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.parser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection

internal object SpotifyHomeParser {
    fun parseHomeSection(sectionObj: JsonObject): SpotifyHomeFeedSection? {
        val sectionData = sectionObj.obj("data") ?: return null
        val typename = sectionData.str("__typename") ?: return null
        val titleObj = sectionData.obj("title")
        val title =
            titleObj?.str("transformedLabel")
                ?: titleObj?.str("translatedBaseText")
                ?: titleObj?.str("text")

        val sectionItems = sectionObj.obj("sectionItems")
        val totalCount = sectionItems?.int("totalCount") ?: 0
        val itemElements = sectionItems?.arr("items") ?: return null

        val items =
            itemElements.mapNotNull { itemElem ->
                parseHomeItem(itemElem.jsonObject)
            }

        if (items.isEmpty()) return null

        return SpotifyHomeFeedSection(
            sectionUri = sectionObj.str("uri") ?: "",
            title = title,
            typename = typename,
            totalCount = totalCount,
            items = items,
        )
    }

    fun parseHomeItem(itemObj: JsonObject): SpotifyHomeFeedItem? {
        val content = itemObj.obj("content") ?: return null
        val wrapper = content.str("__typename") ?: return null
        val data = content.obj("data") ?: return null

        return when (wrapper) {
            "PlaylistResponseWrapper" -> parseHomePlaylist(data)
            "AlbumResponseWrapper" -> parseHomeAlbum(data)
            "ArtistResponseWrapper" -> parseHomeArtist(data)
            else -> null
        }
    }

    fun parseHomePlaylist(data: JsonObject): SpotifyHomeFeedItem.Playlist? {
        val uri = data.str("uri") ?: return null
        val imageItem =
            data
                .obj("images")
                ?.arr("items")
                ?.firstOrNull()
                ?.jsonObject
        val imageUrl =
            imageItem
                ?.arr("sources")
                ?.firstOrNull()
                ?.jsonObject
                ?.str("url")
        val colorHex = imageItem?.obj("extractedColors")?.obj("colorDark")?.str("hex")
        val madeFor =
            data
                .arr("attributes")
                ?.firstOrNull { it.jsonObject.str("key") == "madeFor.username" }
                ?.jsonObject
                ?.str("value")

        return SpotifyHomeFeedItem.Playlist(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            description = data.str("description"),
            format = data.str("format"),
            totalCount = data.obj("content")?.int("totalCount") ?: 0,
            imageUrl = imageUrl,
            extractedColorHex = colorHex,
            ownerName = data.obj("ownerV2")?.obj("data")?.str("name"),
            madeForUsername = madeFor,
        )
    }

    fun parseHomeAlbum(data: JsonObject): SpotifyHomeFeedItem.Album? {
        val uri = data.str("uri") ?: return null
        val artists =
            data.obj("artists")?.arr("items")?.mapNotNull {
                SpotifyTrackParser.parseGqlSimpleArtist(it.jsonObject)
            } ?: emptyList()
        val imageUrl =
            data
                .obj("coverArt")
                ?.arr("sources")
                ?.firstOrNull()
                ?.jsonObject
                ?.str("url")

        return SpotifyHomeFeedItem.Album(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            albumType = data.str("type")?.lowercase(),
            artists = artists,
            imageUrl = imageUrl,
        )
    }

    fun parseHomeArtist(data: JsonObject): SpotifyHomeFeedItem.Artist? {
        val uri = data.str("uri") ?: return null
        val profile = data.obj("profile")
        val imageUrl =
            data
                .obj("visuals")
                ?.obj("avatarImage")
                ?.arr("sources")
                ?.firstOrNull()
                ?.jsonObject
                ?.str("url")
        return SpotifyHomeFeedItem.Artist(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = profile?.str("name") ?: "",
            imageUrl = imageUrl,
        )
    }

    fun parseGqlSearchAlbum(data: JsonObject): SpotifyAlbum {
        val uri = data.str("uri") ?: ""
        return SpotifyAlbum(
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            albumType = data.str("type")?.lowercase(),
            artists =
                data.obj("artists")?.arr("items")?.mapNotNull {
                    SpotifyTrackParser.parseGqlSimpleArtist(it.jsonObject)
                } ?: emptyList(),
            images = SpotifyTrackParser.parseGqlImages(data.obj("coverArt")?.arr("sources")),
            releaseDate = data.obj("date")?.int("year")?.toString(),
            uri = uri.ifEmpty { null },
        )
    }

    fun parseGqlSearchArtist(data: JsonObject): SpotifyArtist {
        val uri = data.str("uri") ?: ""
        return SpotifyArtist(
            id = uri.substringAfterLast(":"),
            name = data.obj("profile")?.str("name") ?: "",
            images = SpotifyTrackParser.parseGqlImages(data.obj("visuals")?.obj("avatarImage")?.arr("sources")),
            uri = uri.ifEmpty { null },
        )
    }
}
