/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.Spotify.PlaylistItemRef
import moe.rukamori.archivetune.spotify.api.SpotifyPlaylistMutationApi
import moe.rukamori.archivetune.spotify.api.SpotifyPlaylistQueryApi
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryItem
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack

object SpotifyPlaylistApi {
    suspend fun myPlaylists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylist>> =
        SpotifyPlaylistQueryApi.myPlaylists(limit, offset)

    suspend fun myLibraryNode(
        folderUri: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyLibraryItem>> =
        SpotifyPlaylistQueryApi.myLibraryNode(folderUri, limit, offset)

    suspend fun playlist(playlistId: String): Result<SpotifyPlaylist> =
        SpotifyPlaylistQueryApi.playlist(playlistId)

    suspend fun playlistMetadata(playlistId: String): Result<SpotifyPlaylist> =
        SpotifyPlaylistQueryApi.playlistMetadata(playlistId)

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylistTrack>> =
        SpotifyPlaylistQueryApi.playlistTracks(playlistId, limit, offset)

    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ): Result<SpotifyPlaylist> =
        SpotifyPlaylistMutationApi.createPlaylist(name, description, isPublic)

    suspend fun addTracksToPlaylist(
        playlistId: String,
        trackUris: List<String>,
    ): Result<Unit> =
        SpotifyPlaylistMutationApi.addTracksToPlaylist(playlistId, trackUris)

    suspend fun removeTracksFromPlaylist(
        playlistId: String,
        items: List<PlaylistItemRef>,
    ): Result<Unit> =
        SpotifyPlaylistMutationApi.removeTracksFromPlaylist(playlistId, items)

    suspend fun moveItemsInPlaylist(
        playlistId: String,
        uids: List<String>,
        beforeUid: String?,
    ): Result<Unit> =
        SpotifyPlaylistMutationApi.moveItemsInPlaylist(playlistId, uids, beforeUid)

    suspend fun editPlaylistAttributes(
        playlistId: String,
        newName: String? = null,
        newDescription: String? = null,
    ): Result<Unit> =
        SpotifyPlaylistMutationApi.editPlaylistAttributes(playlistId, newName, newDescription)
}
