/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.api

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.Spotify.PlaylistItemRef
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.int
import moe.rukamori.archivetune.spotify.obj
import moe.rukamori.archivetune.spotify.str
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist

object SpotifyPlaylistMutationApi {
    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ): Result<SpotifyPlaylist> =
        runCatching {
            val body =
                buildJsonObject {
                    put("name", name)
                    if (description != null) put("description", description)
                    put("public", isPublic)
                }
            SpotifyGraphqlClient.restPost<SpotifyPlaylist>("me/playlists", body = body)
        }

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
