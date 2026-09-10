/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryFolder
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

internal object SpotifyParsers {
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

    /**
     * Parses the common track data structure shared across multiple GQL
     * operations (fetchPlaylist, fetchLibraryTracks, queryArtistOverview, etc.).
     *
     * @param albumOverride When non-null, used instead of the `albumOfTrack`
     *   field (needed for album-track responses where no albumOfTrack is present).
     * @param uriOverride When non-null, used as the track URI instead of
     *   reading it from [trackData]. Needed when the URI lives on a wrapper
     *   object (e.g. `track._uri`) rather than inside `track.data`.
     */
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

        return SpotifyTrack(
            id = trackId,
            name = trackData.str("name") ?: "",
            artists = artists,
            album = album,
            durationMs = parseGqlTrackDurationMs(trackData),
            uri = uri.ifEmpty { null },
        )
    }

    /**
     * Extracts track duration in ms from GQL track payload.
     * Tries multiple keys because different operations may return duration
     * as nested (duration.totalMilliseconds) or flat (durationMs / duration_ms).
     */
    fun parseGqlTrackDurationMs(trackData: JsonObject): Int {
        trackData.obj("duration")?.int("totalMilliseconds")?.let { if (it > 0) return it }
        trackData.int("durationMs")?.let { if (it > 0) return it }
        trackData.int("duration_ms")?.let { if (it > 0) return it }
        // Some APIs return duration in seconds
        trackData.int("duration")?.let { sec -> if (sec > 0) return sec * 1000 }
        return 0
    }

    /**
     * Flattens the nested `images.items[].sources[]` structure used by
     * playlists in the GQL response.
     */
    fun parseGqlPlaylistImages(imagesObj: JsonObject?): List<SpotifyImage> =
        imagesObj?.arr("items")?.flatMap { imageGroup ->
            parseGqlImages(imageGroup.jsonObject.arr("sources"))
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
        // Spotify has shipped this object under several shapes over time; the name
        // and child count have lived in `data` and at the root of the wrapper.
        // Try both so we don't break on a future field reshuffle.
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

    fun parseGqlSearchAlbum(data: JsonObject): SpotifyAlbum {
        val uri = data.str("uri") ?: ""
        return SpotifyAlbum(
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            albumType = data.str("type")?.lowercase(),
            artists =
                data.obj("artists")?.arr("items")?.mapNotNull {
                    parseGqlSimpleArtist(it.jsonObject)
                } ?: emptyList(),
            images = parseGqlImages(data.obj("coverArt")?.arr("sources")),
            releaseDate = data.obj("date")?.int("year")?.toString(),
            uri = uri.ifEmpty { null },
        )
    }

    fun parseGqlSearchArtist(data: JsonObject): SpotifyArtist {
        val uri = data.str("uri") ?: ""
        return SpotifyArtist(
            id = uri.substringAfterLast(":"),
            name = data.obj("profile")?.str("name") ?: "",
            images = parseGqlImages(data.obj("visuals")?.obj("avatarImage")?.arr("sources")),
            uri = uri.ifEmpty { null },
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
                parseGqlSimpleArtist(it.jsonObject)
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
}
