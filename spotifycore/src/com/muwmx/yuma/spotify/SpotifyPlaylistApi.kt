/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.spotify.Spotify.PlaylistItemRef
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef

object SpotifyPlaylistApi {
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
                        add("LIKED_SONGS")
                        add("YOUR_EPISODES_V2")
                        add("PRERELEASES")
                        add("EVENTS")
                    }
                    put("limit", limit)
                    put("offset", offset)
                    // Ask Spotify to return every leaf playlist regardless of folder
                    // nesting. Without flatten=true the response only contains root-
                    // level items: top-level playlists plus FolderResponseWrapper
                    // entries whose contents are never expanded — the parser below
                    // ignores non-PlaylistResponseWrapper items, so anything inside
                    // a folder would otherwise be invisible (issues #46, #78).
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

    /**
     * Returns one level of the user's library tree. When [folderUri] is null the
     * response is the library root: top-level playlists plus folder containers.
     * When [folderUri] is set, Spotify treats that folder as the root and returns
     * its direct children (which may include sub-folders).
     *
     * Use this for UIs that want to mirror the user's folder organization. For a
     * flat list of every playlist regardless of nesting, use [myPlaylists].
     */
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
                        add("LIKED_SONGS")
                        add("YOUR_EPISODES_V2")
                        add("PRERELEASES")
                        add("EVENTS")
                    }
                    put("limit", limit)
                    put("offset", offset)
                    // flatten=false preserves folder boundaries; folderUri scopes
                    // the response to a single level (null = root).
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
            // Diagnostic: dump every wrapper's __typename so we can spot any
            // schema variation Spotify ships (the names have shifted historically).
            // Trim once we're confident the recognized set is stable.
            SpotifyGraphqlClient.log("D", "myLibraryNode(folder=$folderUri): ${rawItems.size} raw items")
            val typeCounts = mutableMapOf<String, Int>()
            rawItems.forEach { itemElem ->
                val wrapper = itemElem.jsonObject.obj("item")
                val tn = wrapper?.str("__typename") ?: "<missing>"
                typeCounts[tn] = (typeCounts[tn] ?: 0) + 1
            }
            SpotifyGraphqlClient.log("D", "myLibraryNode: typename counts = $typeCounts")

            val items =
                rawItems.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    val typeName = wrapper.str("__typename") ?: ""
                    when {
                        typeName == "PlaylistResponseWrapper" || typeName.contains("Playlist", ignoreCase = true) -> {
                            SpotifyParsers.parsePlaylistWrapper(wrapper)
                                ?.let {
                                    SpotifyLibraryItem.Playlist(it)
                                }
                        }

                        typeName == "FolderResponseWrapper" || typeName.contains("Folder", ignoreCase = true) -> {
                            SpotifyParsers.parseFolderWrapper(wrapper)
                                ?.let {
                                    SpotifyLibraryItem.Folder(it)
                                }
                                ?: run {
                                    // Folder typename matched but parsing returned null —
                                    // likely a shape we don't know. Dump the keys so we
                                    // can update SpotifyParsers.parseFolderWrapper.
                                    SpotifyGraphqlClient.log(
                                        "W",
                                        "myLibraryNode: failed to parse folder wrapper, keys=${wrapper.keys}, dataKeys=${wrapper.obj(
                                            "data",
                                        )?.keys}",
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

    /**
     * Adds tracks to a Spotify playlist via GQL mutation.
     * @param playlistId Playlist ID (without the `spotify:playlist:` prefix).
     * @param trackUris Full Spotify URIs, e.g. `["spotify:track:abc123"]`.
     */
    suspend fun addTracksToPlaylist(
        playlistId: String,
        trackUris: List<String>,
    ): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("playlistUri", "spotify:playlist:$playlistId")
                    putJsonArray("playlistItemUris") {
                        trackUris.forEach { add(it) }
                    }
                    putJsonObject("newPosition") {
                        put("moveType", "BOTTOM_OF_PLAYLIST")
                        put("fromUid", JsonNull)
                    }
                }
            SpotifyGraphqlClient.log("D", "addTracksToPlaylist: sending mutation for $playlistId with ${trackUris.size} tracks, vars=$vars")
            withTimeout(20_000L) {
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "addToPlaylist",
                    variables = vars,
                )
            }
            SpotifyGraphqlClient.log("D", "addTracksToPlaylist: added ${trackUris.size} tracks to $playlistId")
        }

    /**
     * Removes tracks from a Spotify playlist via GQL mutation.
     * Requires the playlist-scoped [uid] for each item
     * (returned by fetchPlaylist in each content item).
     */
    suspend fun removeTracksFromPlaylist(
        playlistId: String,
        items: List<PlaylistItemRef>,
    ): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("playlistUri", "spotify:playlist:$playlistId")
                    putJsonArray("uids") {
                        items.forEach { item -> add(item.uid) }
                    }
                }
            SpotifyGraphqlClient.graphqlPost(
                operationName = "removeFromPlaylist",
                variables = vars,
            )
            SpotifyGraphqlClient.log("D", "removeTracksFromPlaylist: removed ${items.size} items from $playlistId")
        }

    /**
     * Moves items within a Spotify playlist via GQL mutation.
     * [uids] are playlist-scoped item identifiers returned by fetchPlaylist.
     * [beforeUid] is the uid of the item the moved items should be placed before,
     * or null to move to the end of the playlist.
     */
    suspend fun moveItemsInPlaylist(
        playlistId: String,
        uids: List<String>,
        beforeUid: String?,
    ): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("playlistUri", "spotify:playlist:$playlistId")
                    putJsonArray("uids") {
                        uids.forEach { add(it) }
                    }
                    putJsonObject("newPosition") {
                        if (beforeUid != null) {
                            put("moveType", "BEFORE_UID")
                            put("fromUid", beforeUid)
                        } else {
                            put("moveType", "BOTTOM_OF_PLAYLIST")
                            put("fromUid", JsonNull)
                        }
                    }
                }
            SpotifyGraphqlClient.graphqlPost(
                operationName = "moveItemsInPlaylist",
                variables = vars,
            )
            SpotifyGraphqlClient.log("D", "moveItemsInPlaylist: moved ${uids.size} items (before=$beforeUid) in $playlistId")
        }

    /**
     * Renames a playlist and/or updates its description via GQL mutation.
     */
    suspend fun editPlaylistAttributes(
        playlistId: String,
        newName: String? = null,
        newDescription: String? = null,
    ): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("playlistUri", "spotify:playlist:$playlistId")
                    if (newName != null) put("newName", newName)
                    if (newDescription != null) put("newDescription", newDescription)
                }
            SpotifyGraphqlClient.graphqlPost(
                operationName = "editPlaylistAttributes",
                variables = vars,
            )
            SpotifyGraphqlClient.log("D", "editPlaylistAttributes: updated $playlistId (name=$newName)")
        }
}
