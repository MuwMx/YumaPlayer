/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.remote

import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube

/**
 * Removes a song from a synced remote playlist. Lives in the domain layer
 * so [RoomPlaylistSource] can call it without importing the UI menu package.
 */
suspend fun removeSongFromRemotePlaylist(
    playlistBrowseId: String,
    playlistSongMap: PlaylistSongMap,
): Result<Unit> =
    runCatching {
        val setVideoIds =
            playlistSongMap.setVideoId?.let(::listOf)
                ?: YouTube.playlistEntrySetVideoIds(playlistBrowseId, playlistSongMap.songId).getOrThrow()

        setVideoIds
            .distinct()
            .forEach { setVideoId ->
                YouTube.removeFromPlaylist(playlistBrowseId, playlistSongMap.songId, setVideoId).getOrThrow()
            }
    }
