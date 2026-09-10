/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.constants.LastSpotifySyncKey
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifySyncOps
    @Inject
    constructor(
        private val state: SyncState,
    ) {
        private val isAutoSyncInFlight = AtomicBoolean(false)

        suspend fun syncSpotifyPlaylists(authoritative: Boolean = false) =
            state.spotifyPlaylistSyncMutex.withLock {
                var remotePlaylistsCount = 0
                try {
                    val session = state.spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) {
                        Timber.w("Skipping syncSpotifyPlaylists - user not logged in to Spotify")
                        return@withLock
                    }

                    val gen = state.syncGeneration.get()
                    if (!state.isSyncStillEnabled(gen)) return@withLock

                    val remotePlaylists = state.spotifyRepository.refreshPlaylists()
                    remotePlaylistsCount = remotePlaylists.size
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
                                                    val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
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

                                    state.database.insert(
                                        SpotifyMatchEntity(
                                            spotifyId = track.id,
                                            youtubeId = metadata.id,
                                            title = track.name,
                                            artist = track.artists.joinToString(" ") { it.name },
                                            matchScore = 1.0,
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
                } finally {
                }
            }

        suspend fun syncSpotifyLikedSongs(
            authoritative: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = state.spotifyPlaylistSyncMutex.withLock {
            try {
                val session = state.spotifyRepository.restoreSession()
                if (!session.isAuthenticated) {
                    Timber.w("Skipping syncSpotifyLikedSongs - user not logged in to Spotify")
                    return@withLock
                }

                val gen = state.syncGeneration.get()
                if (!state.isSyncStillEnabled(gen)) return@withLock

                val tracks = mutableListOf<SpotifyTrack>()
                var offset = 0
                val limit = 50
                val maxPages = 60

                for (page in 0 until maxPages) {
                    if (!state.isSyncStillEnabled(gen)) break
                    val result = Spotify.likedSongs(limit = limit, offset = offset).getOrNull()
                    if (result == null || result.items.isEmpty()) break
                    tracks.addAll(result.items.mapNotNull { it.track })
                    offset += result.items.size
                    if (offset >= result.total || result.items.size < limit) break
                }

                val resolveSemaphore = Semaphore(4)
                data class Resolved(val track: SpotifyTrack, val metadata: MediaMetadata)

                val completed = AtomicInteger(0)
                val total = tracks.size
                onProgress(0, total)

                val resolvedTracks =
                    coroutineScope {
                        tracks
                            .map { track ->
                                async {
                                    resolveSemaphore.withPermit {
                                        if (!state.isSyncStillEnabled(gen)) return@withPermit null
                                        val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
                                        val result =
                                            if (metadata != null && state.isSyncStillEnabled(gen)) {
                                                Resolved(track, metadata)
                                            } else {
                                                null
                                            }
                                        onProgress(completed.incrementAndGet(), total)
                                        result
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }

                if (!state.isSyncStillEnabled(gen)) return@withLock
                val localLikedSongs = state.database.likedSongsByNameAsc(LikeSource.SPOTIFY).first()
                if (!state.isSyncStillEnabled(gen)) return@withLock
                val localLikedIds = localLikedSongs.map { it.id }.toSet()
                val resolvedYoutubeIds = resolvedTracks.map { it.metadata.id }.toSet()
                val resolvedSpotifyIds = resolvedTracks.map { it.track.id }.toSet()

                val staleLikedSongs =
                    if (authoritative) {
                        val unlikeCandidateIds = localLikedSongs.map { it.id }.filter { it !in resolvedYoutubeIds }
                        val matchEntities = state.database.getSpotifyMatchesByYouTubeIds(unlikeCandidateIds)
                        val matchByYtId = matchEntities.associateBy { it.youtubeId }
                        localLikedSongs
                            .filter { song ->
                                if (song.id in resolvedYoutubeIds) return@filter false
                                val match = matchByYtId[song.id] ?: return@filter false
                                match.spotifyId !in resolvedSpotifyIds
                            }.map { it.song.copy(likedSpotify = false, likedDate = if (it.song.likedYtm) it.song.likedDate else null) }
                    } else {
                        emptyList()
                    }

                val newResolvedTracks = resolvedTracks.filter { it.metadata.id !in localLikedIds }

                if (newResolvedTracks.isEmpty() && staleLikedSongs.isEmpty()) {
                    Timber.d("syncSpotifyLikedSongs: No changes detected (stale: 0, new: 0), skipping database writes")
                    return@withLock
                }

                val now = LocalDateTime.now()

                state.database.withTransaction {
                    if (!state.isSyncStillEnabled(gen)) return@withTransaction
                    newResolvedTracks.forEachIndexed { index, resolved ->
                        val timestamp = likedSongTimestamp(now, index)
                        val dbSong = state.database.getSongByIdBlocking(resolved.metadata.id)

                        if (dbSong == null) {
                            state.database.insert(resolved.metadata) { it.copy(likedSpotify = true, likedDate = timestamp) }
                        } else {
                            val finalTimestamp = dbSong.song.likedDate ?: timestamp
                            state.database.update(dbSong.song.copy(likedSpotify = true, likedDate = finalTimestamp))
                        }

                        state.database.insert(
                            SpotifyMatchEntity(
                                spotifyId = resolved.track.id,
                                youtubeId = resolved.metadata.id,
                                title = resolved.track.name,
                                artist = resolved.track.artists.joinToString(" ") { it.name },
                                matchScore = 1.0,
                            ),
                        )
                    }

                    staleLikedSongs.forEach { updatedSong ->
                        state.database.update(updatedSong)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during syncSpotifyLikedSongs")
            } finally {
            }
        }

        fun trySpotifyAutoSync(authoritative: Boolean = false) {
            if (!isAutoSyncInFlight.compareAndSet(false, true)) {
                Timber.d("Spotify auto-sync already in flight, skipping")
                return
            }
            state.syncScope.launch {
                try {
                    val session = state.spotifyRepository.restoreSession()
                    if (!session.isAuthenticated) return@launch

                    val lastSync = state.context.dataStore.data.map { it[LastSpotifySyncKey] ?: 0L }.first()
                    val currentTime = System.currentTimeMillis()
                    if (!authoritative && lastSync > 0 && (currentTime - lastSync) < SPOTIFY_SYNC_COOLDOWN_MS) {
                        Timber.d("Skipping Spotify auto-sync - cooldown active")
                        return@launch
                    }

                    syncSpotifyPlaylists(authoritative = authoritative)
                    syncSpotifyLikedSongs(authoritative = authoritative)
                } catch (e: Exception) {
                    Timber.e(e, "Failed trySpotifyAutoSync")
                } finally {
                    try {
                        state.context.dataStore.edit { prefs ->
                            prefs[LastSpotifySyncKey] = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to update LastSpotifySyncKey")
                    } finally {
                        isAutoSyncInFlight.set(false)
                    }
                }
            }
        }

        companion object {
            private const val SPOTIFY_SYNC_COOLDOWN_MS = 30 * 60 * 1000L
        }
    }
