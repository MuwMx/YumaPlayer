/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyRecentlyPlayed
import moe.rukamori.archivetune.spotify.models.SpotifyRecommendations
import moe.rukamori.archivetune.spotify.models.SpotifySavedTrack
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.spotify.models.SpotifyUser

/**
 * Spotify API client that uses the internal GraphQL API (api-partner.spotify.com)
 * for most operations, falling back to the public REST API (api.spotify.com/v1/)
 * only for endpoints without a GraphQL equivalent (top tracks/artists,
 * recommendations, related artists).
 *
 * GraphQL persisted-query hashes sourced from:
 * https://github.com/sonic-liberation/hetu_spotify_gql_client
 */
object Spotify {
    var accessToken: String?
        get() = SpotifyGraphqlClient.accessToken
        set(value) {
            SpotifyGraphqlClient.accessToken = value
        }

    class SpotifyException(
        val statusCode: Int,
        override val message: String,
        val retryAfterSec: Long = 0,
    ) : Exception(message)

    var logger: ((level: String, message: String) -> Unit)?
        get() = SpotifyGraphqlClient.logger
        set(value) {
            SpotifyGraphqlClient.logger = value
        }

    private fun log(
        level: String,
        message: String,
    ) {
        SpotifyGraphqlClient.log(level, message)
    }

    var onHashExpired: ((operationName: String) -> Unit)?
        get() = SpotifyGraphqlClient.onHashExpired
        set(value) {
            SpotifyGraphqlClient.onHashExpired = value
        }

    internal suspend fun graphqlPost(
        operationName: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject = SpotifyGraphqlClient.graphqlPost(operationName, variables)

    internal suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGraphqlClient.authenticatedGet(endpoint, failFastOn429, block)

    internal suspend inline fun <reified T> restGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGraphqlClient.restGet(endpoint, failFastOn429, block)

    // ── User Profile (GQL with REST fallback) ──────────────────────────

    suspend fun me(): Result<SpotifyUser> =
        runCatching {
            try {
                val response =
                    graphqlPost(
                        operationName = "profileAttributes",
                    )
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
                log("W", "GQL me() failed, falling back to REST: ${e.message}")
                authenticatedGet<SpotifyUser>("me")
            }
        }

    // ── Playlists (GQL: libraryV3) ──────────────────────────────────────

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
                graphqlPost(
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

    // ── Library hierarchy (GQL: libraryV3, folders preserved) ───────────

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
    ): Result<SpotifyPaging<moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem>> =
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
                graphqlPost(
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
            log("D", "myLibraryNode(folder=$folderUri): ${rawItems.size} raw items")
            val typeCounts = mutableMapOf<String, Int>()
            rawItems.forEach { itemElem ->
                val wrapper = itemElem.jsonObject.obj("item")
                val tn = wrapper?.str("__typename") ?: "<missing>"
                typeCounts[tn] = (typeCounts[tn] ?: 0) + 1
            }
            log("D", "myLibraryNode: typename counts = $typeCounts")

            val items =
                rawItems.mapNotNull { itemElem ->
                    val wrapper = itemElem.jsonObject.obj("item") ?: return@mapNotNull null
                    val typeName = wrapper.str("__typename") ?: ""
                    when {
                        typeName == "PlaylistResponseWrapper" || typeName.contains("Playlist", ignoreCase = true) -> {
                            SpotifyParsers.parsePlaylistWrapper(wrapper)
                                ?.let {
                                    moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
                                        .Playlist(it)
                                }
                        }

                        typeName == "FolderResponseWrapper" || typeName.contains("Folder", ignoreCase = true) -> {
                            SpotifyParsers.parseFolderWrapper(wrapper)
                                ?.let {
                                    moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
                                        .Folder(it)
                                }
                                ?: run {
                                    // Folder typename matched but parsing returned null —
                                    // likely a shape we don't know. Dump the keys so we
                                    // can update SpotifyParsers.parseFolderWrapper.
                                    log(
                                        "W",
                                        "myLibraryNode: failed to parse folder wrapper, keys=${wrapper.keys}, dataKeys=${wrapper.obj(
                                            "data",
                                        )?.keys}",
                                    )
                                    null
                                }
                        }

                        else -> {
                            log("D", "myLibraryNode: skipping unknown __typename='$typeName'")
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

    // ── Library Artists (GQL: libraryV3 with Artists filter) ───────────

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
                graphqlPost(
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

    // ── Playlist detail (GQL: fetchPlaylist) ────────────────────────────

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
                graphqlPost(
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
                graphqlPost(
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

    // ── Playlist Mutations (GQL) ──────────────────────────────────────

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
            log("D", "addTracksToPlaylist: sending mutation for $playlistId with ${trackUris.size} tracks, vars=$vars")
            kotlinx.coroutines.withTimeout(20_000L) {
                graphqlPost(
                    operationName = "addToPlaylist",
                    variables = vars,
                )
            }
            log("D", "addTracksToPlaylist: added ${trackUris.size} tracks to $playlistId")
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
            graphqlPost(
                operationName = "removeFromPlaylist",
                variables = vars,
            )
            log("D", "removeTracksFromPlaylist: removed ${items.size} items from $playlistId")
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
            graphqlPost(
                operationName = "moveItemsInPlaylist",
                variables = vars,
            )
            log("D", "moveItemsInPlaylist: moved ${uids.size} items (before=$beforeUid) in $playlistId")
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
            graphqlPost(
                operationName = "editPlaylistAttributes",
                variables = vars,
            )
            log("D", "editPlaylistAttributes: updated $playlistId (name=$newName)")
        }

    /**
     * Reference to a specific item inside a playlist, needed for removal/reorder.
     */
    data class PlaylistItemRef(
        val uri: String,
        val uid: String,
    )

    // ── Liked Songs (GQL: fetchLibraryTracks) ───────────────────────────

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
                graphqlPost(
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

            val savedTracks =
                tracksData.arr("items")?.mapNotNull { elem ->
                    val trackWrapper = elem.jsonObject.obj("track") ?: return@mapNotNull null
                    val trackData = trackWrapper.obj("data") ?: return@mapNotNull null
                    val wrapperUri = trackWrapper.str("_uri") ?: trackWrapper.str("uri")
                    SpotifySavedTrack(track = SpotifyParsers.parseGqlTrack(trackData, uriOverride = wrapperUri))
                } ?: emptyList()

            SpotifyPaging(
                items = savedTracks,
                total = tracksData.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }

    // ── Library Mutations (GQL: addToLibrary / removeFromLibrary) ──────

    /**
     * Saves tracks/albums/playlists to the user's Spotify library (like).
     * @param uris Full Spotify URIs, e.g. `["spotify:track:abc123"]`.
     */
    suspend fun addToLibrary(uris: List<String>): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("libraryItemUris") {
                        uris.forEach { add(it) }
                    }
                }
            graphqlPost(
                operationName = "addToLibrary",
                variables = vars,
            )
            log("D", "addToLibrary: added ${uris.size} items")
        }

    /**
     * Removes tracks/albums/playlists from the user's Spotify library (unlike).
     * @param uris Full Spotify URIs, e.g. `["spotify:track:abc123"]`.
     */
    suspend fun removeFromLibrary(uris: List<String>): Result<Unit> =
        runCatching {
            val vars =
                buildJsonObject {
                    putJsonArray("libraryItemUris") {
                        uris.forEach { add(it) }
                    }
                }
            graphqlPost(
                operationName = "removeFromLibrary",
                variables = vars,
            )
            log("D", "removeFromLibrary: removed ${uris.size} items")
        }

    // ── Top Tracks (REST fallback — no GQL equivalent) ──────────────────

    suspend fun recentlyPlayed(limit: Int = 20): Result<SpotifyRecentlyPlayed> =
        runCatching {
            authenticatedGet("me/player/recently-played", failFastOn429 = true) {
                parameter("limit", limit)
            }
        }

    suspend fun topTracks(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyTrack>> =
        runCatching {
            authenticatedGet("me/top/tracks", failFastOn429 = true) {
                parameter("time_range", timeRange)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    // ── Top Artists (REST fallback — no GQL equivalent) ─────────────────

    suspend fun topArtists(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> =
        runCatching {
            authenticatedGet("me/top/artists", failFastOn429 = true) {
                parameter("time_range", timeRange)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    // ── Recommendations (REST fallback — no GQL equivalent) ─────────────

    suspend fun recommendations(
        seedTrackIds: List<String> = emptyList(),
        seedArtistIds: List<String> = emptyList(),
        seedGenres: List<String> = emptyList(),
        limit: Int = 50,
    ): Result<SpotifyRecommendations> =
        runCatching {
            authenticatedGet("recommendations") {
                if (seedTrackIds.isNotEmpty()) parameter("seed_tracks", seedTrackIds.joinToString(","))
                if (seedArtistIds.isNotEmpty()) parameter("seed_artists", seedArtistIds.joinToString(","))
                if (seedGenres.isNotEmpty()) parameter("seed_genres", seedGenres.joinToString(","))
                parameter("limit", limit)
            }
        }

    // ── Search (GQL: searchDesktop) ─────────────────────────────────────

    suspend fun search(
        query: String,
        types: List<String> = listOf("track"),
        limit: Int = 20,
        offset: Int = 0,
    ): Result<SpotifySearchResult> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("searchTerm", query)
                    put("offset", offset)
                    put("limit", limit)
                    put("numberOfTopResults", 5)
                    put("includeAudiobooks", false)
                    put("includeArtistHasConcertsField", false)
                    put("includePreReleases", false)
                    put("includeLocalConcertsField", false)
                    put("includeAuthors", false)
                }

            val response =
                graphqlPost(
                    operationName = "searchDesktop",
                    variables = vars,
                )

            val searchData =
                response.obj("data")?.obj("searchV2")
                    ?: throw SpotifyException(500, "Invalid searchDesktop response")

            val tracksSection = searchData.obj("tracksV2")
            val trackItems =
                tracksSection?.arr("items")?.mapNotNull { elem ->
                    val itemWrapper = elem.jsonObject.obj("item") ?: return@mapNotNull null
                    if (itemWrapper.str("__typename") != "TrackResponseWrapper") return@mapNotNull null
                    val data = itemWrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Track") return@mapNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    SpotifyParsers.parseGqlTrack(data, uriOverride = wrapperUri)
                } ?: emptyList()

            val albumsSection = searchData.obj("albumsV2")
            val albumItems =
                albumsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "AlbumResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Album") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchAlbum(data)
                } ?: emptyList()

            val artistsSection = searchData.obj("artists")
            val artistItems =
                artistsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "ArtistResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Artist") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchArtist(data)
                } ?: emptyList()

            val playlistsSection = searchData.obj("playlists")
            val playlistItems =
                playlistsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "PlaylistResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Playlist") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchPlaylist(data)
                } ?: emptyList()

            SpotifySearchResult(
                tracks =
                    SpotifyPaging(
                        items = trackItems,
                        total = tracksSection?.int("totalCount") ?: 0,
                        limit = limit,
                        offset = offset,
                    ),
                albums =
                    if (albumItems.isNotEmpty()) {
                        SpotifyPaging(items = albumItems, total = albumsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                artists =
                    if (artistItems.isNotEmpty()) {
                        SpotifyPaging(items = artistItems, total = artistsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                playlists =
                    if (playlistItems.isNotEmpty()) {
                        SpotifyPaging(
                            items = playlistItems,
                            total = playlistsSection?.int("totalCount") ?: 0,
                            limit = limit,
                            offset = offset,
                        )
                    } else {
                        null
                    },
            )
        }

    // ── Browse: New Releases (GQL: queryWhatsNewFeed) ───────────────────

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
                graphqlPost(
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

    // ── Home feed (GQL: home) ──────────────────────────────────────────
    //
    // Returns the fully personalized Spotify home: Daily Mix, Discover Weekly,
    // Release Radar, "Jump back in", "More like <artist>", daylist, etc.
    // Shape matches open.spotify.com landing page, one request for ~21 sections.

    suspend fun home(
        sectionItemsLimit: Int = 10,
        timeZone: String =
            java.util.TimeZone
                .getDefault()
                .id,
    ): Result<moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed> =
        runCatching {
            log("D", "spotifyHome: GQL home() request — timeZone=$timeZone limit=$sectionItemsLimit")
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
                graphqlPost(
                    operationName = "home",
                    variables = vars,
                )

            val homeData =
                response.obj("data")?.obj("home")
                    ?: run {
                        log("E", "spotifyHome: GQL response has no data.home — keys=${response.obj("data")?.keys}")
                        throw SpotifyException(500, "Invalid home response")
                    }

            val greeting = homeData.obj("greeting")?.str("transformedLabel")
            log("D", "spotifyHome: GQL home() OK greeting='$greeting'")

            val sectionElements =
                homeData
                    .obj("sectionContainer")
                    ?.obj("sections")
                    ?.arr("items")
                    ?: run {
                        log("W", "spotifyHome: no sectionContainer.sections.items in response")
                        return@runCatching moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed(
                            greeting = greeting,
                            sections = emptyList(),
                        )
                    }

            log("D", "spotifyHome: parsing ${sectionElements.size} raw sections")
            val sections =
                sectionElements.mapNotNull { elem ->
                    SpotifyParsers.parseHomeSection(elem.jsonObject)
                }
            log("D", "spotifyHome: parsed ${sections.size}/${sectionElements.size} sections successfully")

            moe.rukamori.archivetune.spotify.models.SpotifyHomeFeed(
                greeting = greeting,
                sections = sections,
            )
        }

    // ── Albums (GQL: getAlbum) ──────────────────────────────────────────

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
                graphqlPost(
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

    // ── Artists (GQL: queryArtistOverview) ───────────────────────────────

    suspend fun artist(artistId: String): Result<SpotifyArtist> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:artist:$artistId")
                    put("locale", "")
                }

            val response =
                graphqlPost(
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
                graphqlPost(
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

    /**
     * Extracts related artists from the GQL queryArtistOverview endpoint.
     * This avoids the rate-limited REST /related-artists endpoint entirely.
     */
    suspend fun artistRelatedArtists(artistId: String): Result<List<SpotifyArtist>> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("uri", "spotify:artist:$artistId")
                    put("locale", "")
                }

            val response =
                graphqlPost(
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

    // ── Related Artists (REST fallback) ─────────────────────────────────

    suspend fun relatedArtists(artistId: String): Result<RelatedArtistsResponse> =
        runCatching {
            authenticatedGet("artists/$artistId/related-artists")
        }

    fun isAuthenticated(): Boolean = SpotifyGraphqlClient.isAuthenticated()
}

@kotlinx.serialization.Serializable
data class ArtistTopTracksResponse(
    val tracks: List<SpotifyTrack> = emptyList(),
)

@kotlinx.serialization.Serializable
data class RelatedArtistsResponse(
    val artists: List<SpotifyArtist> = emptyList(),
)

@kotlinx.serialization.Serializable
data class NewReleasesResponse(
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
)
