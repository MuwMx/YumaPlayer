/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import javax.inject.Inject

class SpotifyPlaylistReader
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
    ) {
        /** Every track of the playlist, in order. Local files are left out. */
        suspend fun read(playlistId: String): List<SpotifyTrack> = repository.playlistTracks(playlistId)
    }

class PersonalPlaylistReader
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun read(playlistId: String): List<CopyTrack> =
            withContext(Dispatchers.IO) {
                database.playlistSongs(playlistId).first().map(PlaylistSong::toCopyTrack)
            }
    }

class YouTubePlaylistReader
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        /**
         * Reads the playlist from the library when it is fully synced and from YouTube
         * otherwise. Throws when the playlist cannot be read completely, because copying
         * part of a playlist without saying so would be worse than failing.
         */
        suspend fun read(browseId: String): List<CopyTrack> =
            withContext(Dispatchers.IO) {
                val playlist = database.playlistByBrowseId(browseId).first()
                val synced = playlist?.let { database.playlistSongs(it.id).first() }.orEmpty()
                val remoteCount = playlist?.playlist?.remoteSongCount
                if (synced.isNotEmpty() && remoteCount != null && synced.size >= remoteCount) {
                    return@withContext synced.map(PlaylistSong::toCopyTrack)
                }

                val page =
                    YouTube
                        .playlist(browseId)
                        .completed()
                        .getOrElse { error -> throw IllegalStateException("Could not read the YouTube playlist", error) }
                check(remoteCount == null || page.songs.size >= remoteCount) {
                    "YouTube playlist is incomplete (${page.songs.size}/$remoteCount)"
                }
                page.songs.map { song ->
                    val metadata = song.toMediaMetadata()
                    CopyTrack(songId = metadata.id, metadata = metadata)
                }
            }
    }

private fun PlaylistSong.toCopyTrack() =
    CopyTrack(
        songId = song.id,
        metadata = song.toMediaMetadata(),
        isLocalFile = song.song.isLocal,
    )
