/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/** A playlist that was written, and how many tracks went into it. */
data class PlaylistWrite(
    val playlistId: String,
    val added: Int,
)

/** The tracks that are not in [existingSongIds] yet, without repeats, in their original order. */
fun tracksToAdd(
    existingSongIds: Set<String>,
    tracks: List<CopyTrack>,
): List<CopyTrack> {
    val seen = existingSongIds.toMutableSet()
    return tracks.filter { seen.add(it.songId) }
}

/** Writes copied tracks to a local playlist or to a YouTube playlist and its local mirror. */
class PlaylistCopyWriter
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun createPersonal(
            name: String,
            sourceSpotifyId: String?,
            thumbnailUrl: String?,
            tracks: List<CopyTrack>,
        ): PlaylistWrite =
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val entity = newPlaylistEntity(name, browseId = null, sourceSpotifyId, thumbnailUrl, tracks.size)
                    insert(entity)
                    insertTracks(getPlaylistById(entity.id) ?: error("Playlist insert failed"), tracks)
                    PlaylistWrite(entity.id, tracks.size)
                }
            }

        /** Returns how many tracks were added. */
        suspend fun appendPersonal(
            playlistId: String,
            tracks: List<CopyTrack>,
        ): Int =
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val playlist = getPlaylistById(playlistId) ?: error("Playlist not found")
                    val missing = tracksToAdd(playlistSongIds(playlistId).toSet(), tracks)
                    insertTracks(playlist, missing)
                    missing.size
                }
            }

        /**
         * Creates the playlist on YouTube first and mirrors it locally. When adding the songs or
         * writing the local rows fails, the YouTube playlist is deleted again so no empty
         * playlist is left behind.
         */
        suspend fun createYouTube(
            name: String,
            thumbnailUrl: String?,
            tracks: List<CopyTrack>,
        ): PlaylistWrite =
            withContext(Dispatchers.IO) {
                val uploadable = tracks.filterNot { it.isLocalFile }
                val browseId = YouTube.createPlaylist(name).getOrThrow()
                try {
                    val setVideoIds = YouTube.addSongsToPlaylist(browseId, uploadable.map { it.songId }).getOrThrow()
                    database.withTransaction {
                        val entity = newPlaylistEntity(name, browseId, sourceSpotifyId = null, thumbnailUrl, uploadable.size)
                        insert(entity)
                        insertTracks(getPlaylistById(entity.id) ?: error("Playlist insert failed"), uploadable, setVideoIds)
                        PlaylistWrite(entity.id, uploadable.size)
                    }
                } catch (e: Throwable) {
                    withContext(NonCancellable) {
                        YouTube.deletePlaylist(browseId).onFailure {
                            Timber.tag(TAG).w(it, "Could not delete the incomplete YouTube playlist %s", browseId)
                        }
                    }
                    throw e
                }
            }

        /** Returns how many tracks were added. */
        suspend fun appendYouTube(
            playlistId: String,
            tracks: List<CopyTrack>,
        ): Int =
            withContext(Dispatchers.IO) {
                val playlist = database.getPlaylistById(playlistId) ?: error("Playlist not found")
                val browseId = playlist.playlist.browseId ?: error("Not a YouTube playlist")
                val missing = tracksToAdd(database.playlistSongIds(playlistId).toSet(), tracks.filterNot { it.isLocalFile })
                if (missing.isEmpty()) return@withContext 0
                val setVideoIds = YouTube.addSongsToPlaylist(browseId, missing.map { it.songId }).getOrThrow()
                database.withTransaction { insertTracks(playlist, missing, setVideoIds) }
                missing.size
            }

        private fun newPlaylistEntity(
            name: String,
            browseId: String?,
            sourceSpotifyId: String?,
            thumbnailUrl: String?,
            songCount: Int,
        ) = PlaylistEntity(
            name = name,
            browseId = browseId,
            isEditable = true,
            spotifySourceId = sourceSpotifyId,
            thumbnailUrl = thumbnailUrl,
            bookmarkedAt = LocalDateTime.now(),
            lastUpdateTime = LocalDateTime.now(),
            remoteSongCount = songCount,
        )

        /** Adds [tracks] to the end of [playlist]; [setVideoIds] are the YouTube ids in the same order. */
        private fun MusicDatabase.insertTracks(
            playlist: Playlist,
            tracks: List<CopyTrack>,
            setVideoIds: List<String?> = emptyList(),
        ) {
            if (tracks.isEmpty()) return
            val (placeholders, matched) =
                tracks.mapNotNull { it.metadata }.distinctBy { it.id }.partition { SpotifyPlaceholders.isPlaceholder(it.id) }

            // A Spotify playlist can have thousands of placeholders, which are written in bulk.
            // Matched songs go through the regular insert, which also links albums and reuses artists.
            insertSongsIfAbsent(placeholders.map { it.toSongEntity() })
            val artists = ArrayList<ArtistEntity>()
            val artistMaps = ArrayList<SongArtistMap>()
            placeholders.forEach { song ->
                song.artists.forEachIndexed { index, artist ->
                    // Placeholder metadata always gives its artists an id.
                    val artistId = artist.id ?: return@forEachIndexed
                    artists += ArtistEntity(id = artistId, name = artist.name)
                    artistMaps += SongArtistMap(songId = song.id, artistId = artistId, position = index)
                }
            }
            insertArtistsIfAbsent(artists.distinctBy { it.id })
            insertSongArtistMapsIfAbsent(artistMaps)
            matched.forEach { insert(it) }

            insertPlaylistSongMaps(
                tracks.mapIndexed { index, track ->
                    PlaylistSongMap(
                        songId = track.songId,
                        playlistId = playlist.id,
                        position = playlist.songCount + index,
                        setVideoId = setVideoIds.getOrNull(index),
                    )
                },
            )
        }

        private companion object {
            const val TAG = "PlaylistCopy"
        }
    }
