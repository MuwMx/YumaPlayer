/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.utils.SyncState
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyPlaylistSync
    @Inject
    constructor(
        private val state: SyncState,
    ) {
        suspend fun syncSpotifyPlaylists(authoritative: Boolean = false) =
            state.spotifyPlaylistSyncMutex.withLock {
                try {
                    val session = state.spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) {
                        Timber.w("Skipping syncSpotifyPlaylists - user not logged in to Spotify")
                        return@withLock
                    }

                    val gen = state.syncGeneration.get()
                    if (!state.isSyncStillEnabled(gen)) return@withLock

                    val remotePlaylists = state.spotifyRepository.refreshPlaylists()
                    val remotePlaylistIds = remotePlaylists.map { it.id }.toSet()
                    val now = LocalDateTime.now()

                    if (authoritative) {
                        val localPlaylists = state.database.playlistsByNameAsc().first()
                        if (!state.isSyncStillEnabled(gen)) return@withLock
                        val stalePlaylists =
                            localPlaylists
                                .map { it.playlist }
                                .filter { it.spotifyId != null && it.spotifyId !in remotePlaylistIds }

                        if (stalePlaylists.isNotEmpty()) {
                            state.database.withTransaction {
                                if (!state.isSyncStillEnabled(gen)) return@withTransaction
                                stalePlaylists.forEach { playlist ->
                                    state.database.clearPlaylist(playlist.id)
                                    state.database.delete(playlist)
                                }
                            }
                        }
                    }

                    val resolveSemaphore = Semaphore(4)

                    for (playlist in remotePlaylists) {
                        if (!state.isSyncStillEnabled(gen)) return@withLock
                        try {
                            val existingPlaylist = state.database.playlistBySpotifyId(playlist.id).firstOrNull()
                            val playlistEntity =
                                if (existingPlaylist == null) {
                                    val newEntity =
                                        PlaylistEntity(
                                            name = playlist.name,
                                            spotifyId = playlist.id,
                                            thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                                            remoteSongCount = playlist.tracks?.total,
                                            isEditable = false,
                                            bookmarkedAt = now,
                                        )
                                    state.database.insert(newEntity)
                                    state.database.playlistBySpotifyId(playlist.id).firstOrNull()?.playlist ?: newEntity
                                } else {
                                    val updatedEntity =
                                        existingPlaylist.playlist.copy(
                                            name = playlist.name,
                                            thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                                            remoteSongCount = playlist.tracks?.total,
                                            lastUpdateTime = now,
                                        )
                                    state.database.update(updatedEntity)
                                    updatedEntity
                                }

                            val tracks = state.spotifyRepository.playlistTracks(playlist.id)
                            if (!state.isSyncStillEnabled(gen)) return@withLock
                            val resolvedTracks =
                                coroutineScope {
                                    tracks
                                        .map { track ->
                                            async {
                                                resolveSemaphore.withPermit {
                                                    if (!state.isSyncStillEnabled(gen)) return@withPermit null
                                                    val metadata = SpotifyPlaybackResolver.resolveToMetadata(track, state.database)
                                                    if (metadata != null && state.isSyncStillEnabled(gen)) {
                                                        track to metadata
                                                    } else {
                                                        null
                                                    }
                                                }
                                            }
                                        }.awaitAll()
                                        .filterNotNull()
                                }

                            if (!state.isSyncStillEnabled(gen)) return@withLock
                            state.database.withTransaction {
                                if (!state.isSyncStillEnabled(gen)) return@withTransaction
                                state.database.clearPlaylist(playlistEntity.id)
                                resolvedTracks.forEachIndexed { idx, (track, metadata) ->
                                    val dbSong = state.database.getSongByIdBlocking(metadata.id)
                                    if (dbSong == null) {
                                        state.database.insert(metadata)
                                    } else {
                                        state.database.update(dbSong, metadata)
                                    }

                                    state.database.insert(
                                        PlaylistSongMap(
                                            playlistId = playlistEntity.id,
                                            songId = metadata.id,
                                            position = idx,
                                        ),
                                    )

                                    val existingMatch = state.database.getSpotifyMatch(track.id)
                                    val score = existingMatch?.matchScore ?: 0.0
                                    val isrc = existingMatch?.isrc ?: metadata.isrc

                                    state.database.insert(
                                        SpotifyMatchEntity(
                                            spotifyId = track.id,
                                            youtubeId = metadata.id,
                                            title = track.name,
                                            artist = track.artists.joinToString(" ") { it.name },
                                            matchScore = score,
                                            isrc = isrc,
                                        ),
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync Spotify playlist ${playlist.name}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during syncSpotifyPlaylists")
                }
            }
    }
