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
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

data class PlaylistWrite(
    val playlistId: String,
    val added: Int,
)

fun tracksToAdd(
    existingSongIds: Set<String>,
    tracks: List<CopyTrack>,
): List<CopyTrack> {
    val seen = existingSongIds.toMutableSet()
    return tracks.filter { seen.add(it.songId) }
}

fun tracksNewOnSpotify(
    syncedSongIds: Set<String>?,
    tracks: List<CopyTrack>,
): List<CopyTrack> = if (syncedSongIds == null) emptyList() else tracks.filter { it.songId !in syncedSongIds }

data class SpotifyCopyPlan(
    val spotifyId: String,
    val name: String,
    val thumbnailUrl: String?,
    val remoteCount: Int,
    val tracks: List<CopyTrack>,
    val existingCopyId: String?,
    val syncedSongIds: Set<String>? = null,
)

data class SpotifyCopyWriteResult(
    val created: Int,
    val updated: Int,
    val addedTracks: Int,
)

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
                    // A playlist has one editable copy: when another request made it first, add to that one.
                    val existing = sourceSpotifyId?.let { playlistBySpotifySourceId(it) }
                    if (existing != null) {
                        return@withTransaction PlaylistWrite(existing.id, addMissingTracks(existing, tracks).size)
                    }
                    val entity = newPlaylistEntity(name, browseId = null, sourceSpotifyId, thumbnailUrl, tracks.size)
                    insert(entity)
                    insertTracks(getPlaylistById(entity.id) ?: error("Playlist insert failed"), tracks)
                    PlaylistWrite(entity.id, tracks.size)
                }
            }

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

        suspend fun writeSpotifyCopies(plans: List<SpotifyCopyPlan>): SpotifyCopyWriteResult =
            withContext(Dispatchers.IO) {
                var created = 0
                var updated = 0
                var addedTracks = 0
                database.withTransaction {
                    plans.forEach { plan ->
                        val existing =
                            plan.existingCopyId?.let { getPlaylistById(it) } ?: playlistBySpotifySourceId(plan.spotifyId)
                        if (existing == null) {
                            val entity =
                                newPlaylistEntity(plan.name, browseId = null, plan.spotifyId, plan.thumbnailUrl, plan.remoteCount)
                            insert(entity)
                            insertTracks(getPlaylistById(entity.id) ?: error("Playlist insert failed"), plan.tracks)
                            created++
                            addedTracks += plan.tracks.size
                        } else {
                            val repaired = replaceLegacyLocalFiles(existing, plan.tracks)
                            val missing = addMissingTracks(repaired, tracksNewOnSpotify(plan.syncedSongIds, plan.tracks))
                            update(
                                existing.playlist.copy(
                                    remoteSongCount = plan.remoteCount,
                                    thumbnailUrl = existing.playlist.thumbnailUrl ?: plan.thumbnailUrl,
                                    lastUpdateTime = LocalDateTime.now(),
                                ),
                            )
                            updated++
                            addedTracks += missing.size
                        }
                    }
                }
                SpotifyCopyWriteResult(created, updated, addedTracks)
            }

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

        /**
         * Points the rows older versions wrote for Spotify local files (`spotify:<seconds>`) at the
         * local file each stood for, keeping their place, and removes the ones with none left.
         * Returns the playlist as it is afterwards.
         */
        private suspend fun MusicDatabase.replaceLegacyLocalFiles(
            playlist: Playlist,
            tracks: List<CopyTrack>,
        ): Playlist {
            val legacy = legacyLocalFileMaps(playlist.id)
            if (legacy.isEmpty()) return playlist
            val present = playlistSongIds(playlist.id).toHashSet()
            val replacements = assignLegacyLocalFiles(legacy.map { it.songId }, tracks.filter { it.songId !in present })
            insertPlaceholderSongs(replacements.mapNotNull { it?.metadata })
            val removed = ArrayList<PlaylistSongMap>()
            legacy.zip(replacements).forEach { (map, replacement) ->
                if (replacement != null) update(map.copy(songId = replacement.songId)) else removed += map
            }
            // From the end, so removing a row does not move the ones still to be removed.
            removed.sortedByDescending { it.position }.forEach { map ->
                move(map.playlistId, map.position, Int.MAX_VALUE)
                delete(map.copy(position = Int.MAX_VALUE))
            }
            return getPlaylistById(playlist.id) ?: playlist
        }

        /** Adds the tracks [playlist] does not have yet and returns them. */
        private suspend fun MusicDatabase.addMissingTracks(
            playlist: Playlist,
            tracks: List<CopyTrack>,
        ): List<CopyTrack> {
            val present = playlistSongIds(playlist.id).toHashSet()
            return tracks.filter { present.add(it.songId) }.also { insertTracks(playlist, it) }
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

        private fun MusicDatabase.insertTracks(
            playlist: Playlist,
            tracks: List<CopyTrack>,
            setVideoIds: List<String?> = emptyList(),
        ) {
            if (tracks.isEmpty()) return
            val (placeholders, matched) =
                tracks.mapNotNull { it.metadata }.distinctBy { it.id }.partition { SpotifyPlaceholders.isPlaceholder(it.id) }

            insertPlaceholderSongs(placeholders)
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

        private fun MusicDatabase.insertPlaceholderSongs(placeholders: List<MediaMetadata>) {
            if (placeholders.isEmpty()) return
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
        }

        private companion object {
            const val TAG = "PlaylistCopy"
        }
    }
