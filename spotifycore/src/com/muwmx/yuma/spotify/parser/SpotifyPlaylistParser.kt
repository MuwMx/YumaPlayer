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
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryFolder
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef

internal object SpotifyPlaylistParser {
    fun parseGqlPlaylistImages(imagesObj: JsonObject?): List<SpotifyImage> =
        imagesObj?.arr("items")?.flatMap { imageGroup ->
            SpotifyTrackParser.parseGqlImages(imageGroup.jsonObject.arr("sources"))
        } ?: emptyList()

    fun parsePlaylistWrapper(wrapper: JsonObject): SpotifyPlaylist? {
        val data = wrapper.obj("data") ?: return null
        if (data.str("__typename") != "Playlist") return null
        val playlistUri = wrapper.str("_uri") ?: return null
        val playlistId = playlistUri.substringAfterLast(":")
        val ownerData = data.obj("ownerV2")?.obj("data")
        val ownerId = ownerData?.str("uri")?.substringAfterLast(":") ?: ownerData?.str("id") ?: ""
        return SpotifyPlaylist(
            id = playlistId,
            name = data.str("name") ?: "",
            description = data.str("description"),
            images = parseGqlPlaylistImages(data.obj("images")),
            owner =
                SpotifyPlaylistOwner(
                    id = ownerId,
                    displayName = ownerData?.str("name"),
                    uri = ownerData?.str("uri"),
                ),
            tracks = SpotifyPlaylistTracksRef(total = parsePlaylistTrackCount(data)),
            uri = playlistUri,
        )
    }

    fun parsePlaylistTrackCount(data: JsonObject): Int? =
        data.obj("content")?.int("totalCount")
            ?: data.obj("contents")?.int("totalCount")
            ?: data.obj("tracks")?.int("totalCount")
            ?: data.obj("tracksV2")?.int("totalCount")
            ?: data.int("totalCount")
            ?: data.int("trackCount")
            ?: data.int("numTracks")

    fun parseFolderWrapper(wrapper: JsonObject): SpotifyLibraryFolder? {
        val uri = wrapper.str("_uri") ?: return null
        val name =
            wrapper.obj("data")?.str("name")
                ?: wrapper.str("name")
                ?: return null
        val total =
            wrapper.obj("data")?.int("totalLength")
                ?: wrapper.obj("data")?.int("numberOfItems")
                ?: wrapper.int("totalLength")
                ?: 0
        return SpotifyLibraryFolder(
            uri = uri,
            name = name,
            totalChildren = total,
        )
    }

    fun parseGqlSearchPlaylist(data: JsonObject): SpotifyPlaylist {
        val uri = data.str("uri") ?: ""
        val ownerData = data.obj("ownerV2")?.obj("data")
        val ownerUri = ownerData?.str("uri") ?: ""

        return SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            description = data.str("description"),
            images = parseGqlPlaylistImages(data.obj("images")),
            owner =
                SpotifyPlaylistOwner(
                    id = ownerUri.substringAfterLast(":"),
                    displayName = ownerData?.str("name"),
                    uri = ownerUri.ifEmpty { null },
                ),
            uri = uri.ifEmpty { null },
        )
    }
}
