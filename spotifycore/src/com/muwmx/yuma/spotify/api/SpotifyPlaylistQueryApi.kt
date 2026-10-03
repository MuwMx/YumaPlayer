/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.api

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyParsers
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef

object SpotifyPlaylistQueryApi {
    suspend fun myPlaylists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylist>> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("filters") { add("Playlists") }
                    put("order", null as String?)
                    put("textFilter", "")
                    putJsonArray("features") {
                        listOf("LIKED_SONGS", "YOUR_EPISODES_V2", "PRERELEASES", "EVENTS").forEach { add(it) }
                    }
                    put("limit", limit)
                    put("offset", offset)
                    put("flatten", true)
                    putJsonArray("expandedFolders") {}
                    put("folderUri", null as String?)
                    put("includeFoldersWhenFlattening", false)
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

            val playlists =
                libraryData.arr("items")?.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    if (wrapper.str("__typename") != "PlaylistResponseWrapper") return@mapNotNull null
                    SpotifyParsers.parsePlaylistWrapper(wrapper)
                } ?: emptyList()

            SpotifyPaging(
                items = playlists,
                total = totalCount,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }

    suspend fun myLibraryNode(
        folderUri: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyLibraryItem>> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("filters") { add("Playlists") }
                    put("order", null as String?)
                    put("textFilter", "")
                    putJsonArray("features") {
                        listOf("LIKED_SONGS", "YOUR_EPISODES_V2", "PRERELEASES", "EVENTS").forEach { add(it) }
                    }
                    put("limit", limit)
                    put("offset", offset)
                    put("flatten", false)
                    putJsonArray("expandedFolders") {}
                    if (folderUri != null) put("folderUri", folderUri) else put("folderUri", null as String?)
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
            val rawItems = libraryData.arr("items").orEmpty()

            val items =
                rawItems.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    val typeName = wrapper.str("__typename") ?: ""
                    when {
                        typeName == "PlaylistResponseWrapper" || typeName.contains("Playlist", ignoreCase = true) -> {
                            SpotifyParsers.parsePlaylistWrapper(wrapper)?.let {
                                SpotifyLibraryItem.Playlist(it)
                            }
                        }

                        typeName == "FolderResponseWrapper" || typeName.contains("Folder", ignoreCase = true) -> {
                            SpotifyParsers.parseFolderWrapper(wrapper)?.let {
                                SpotifyLibraryItem.Folder(it)
                            } ?: run {
                                SpotifyGraphqlClient.log(
                                    "W",
                                    "myLibraryNode: failed to parse folder wrapper, keys=${wrapper.keys}, dataKeys=${wrapper.obj("data")?.keys}",
                                )
                                null
                            }
                        }

                        else -> {
                            SpotifyGraphqlClient.log("D", "myLibraryNode: skipping unknown __typename='$typeName'")
                            null
                        }
                    }
                }

            SpotifyPaging(
                items = items,
                total = totalCount,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }

    suspend fun playlist(playlistId: String): Result<SpotifyPlaylist> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", 0)
                    put("limit", 25)
                    put("enableWatchFeedEntrypoint", true)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "fetchPlaylist",
                    variables = vars,
                )

            val playlist =
                response.obj("data")?.obj("playlistV2")
                    ?: throw SpotifyException(500, "Invalid fetchPlaylist response")

            val ownerData = playlist.obj("ownerV2")?.obj("data")
            val ownerUri = ownerData?.str("uri") ?: ""

            val images =
                playlist.obj("images")?.arr("items")?.firstOrNull()?.let {
                    SpotifyParsers.parseGqlImages(it.jsonObject.arr("sources"))
                } ?: emptyList()

            SpotifyPlaylist(
                id = playlistId,
                name = playlist.str("name") ?: "",
                description = playlist.str("description"),
                images = images,
                owner =
                    SpotifyPlaylistOwner(
                        id = ownerUri.substringAfterLast(":"),
                        displayName = ownerData?.str("name"),
                        uri = ownerUri.ifEmpty { null },
                    ),
                tracks = SpotifyPlaylistTracksRef(total = SpotifyParsers.parsePlaylistTrackCount(playlist)),
                collaborative = (playlist.obj("members")?.arr("items")?.size ?: 0) > 1,
            )
        }

    suspend fun playlistMetadata(playlistId: String): Result<SpotifyPlaylist> =
        playlist(playlistId)

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylistTrack>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", offset)
                    put("limit", limit)
                    put("enableWatchFeedEntrypoint", false)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "fetchPlaylist",
                    variables = vars,
                )

            val content =
                response.obj("data")?.obj("playlistV2")?.obj("content")
                    ?: throw SpotifyException(500, "No content in fetchPlaylist response")

            val tracks =
                content.arr("items")?.mapNotNull { elem ->
                    val itemWrapper = elem.jsonObject.obj("itemV2") ?: return@mapNotNull null
                    val itemData = itemWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    val uid = elem.jsonObject.str("uid") ?: itemWrapper.str("uid")
                    SpotifyPlaylistTrack(
                        track = SpotifyParsers.parseGqlTrack(itemData, uriOverride = wrapperUri),
                        uid = uid,
                    )
                } ?: emptyList()

            SpotifyPaging(
                items = tracks,
                total = content.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }
}
