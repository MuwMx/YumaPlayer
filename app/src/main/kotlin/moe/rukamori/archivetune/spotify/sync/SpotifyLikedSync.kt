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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.SyncState
import moe.rukamori.archivetune.utils.likedSongTimestamp
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLikedSync
    @Inject
    constructor(
        private val state: SyncState,
    ) {
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
                                        val metadata = SpotifyPlaybackResolver.resolveToMetadata(track, state.database)
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
                            }.map { it.song.copy(liked = it.song.likedYtm, likedSpotify = false, likedDate = if (it.song.likedYtm) it.song.likedDate else null) }
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
                            state.database.insert(resolved.metadata) { it.copy(liked = true, likedSpotify = true, likedDate = timestamp) }
                        } else {
                            val finalTimestamp = dbSong.song.likedDate ?: timestamp
                            state.database.update(dbSong.song.copy(liked = true, likedSpotify = true, likedDate = finalTimestamp))
                        }

                        val existingMatch = state.database.getSpotifyMatch(resolved.track.id)
                        val score = existingMatch?.matchScore ?: 0.0
                        val isrc = existingMatch?.isrc ?: resolved.metadata.isrc

                        state.database.insert(
                            SpotifyMatchEntity(
                                spotifyId = resolved.track.id,
                                youtubeId = resolved.metadata.id,
                                title = resolved.track.name,
                                artist = resolved.track.artists.joinToString(" ") { it.name },
                                matchScore = score,
                                isrc = isrc,
                            ),
                        )
                    }

                    staleLikedSongs.forEach { updatedSong ->
                        state.database.update(updatedSong)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during syncSpotifyLikedSongs")
            }
        }
    }
