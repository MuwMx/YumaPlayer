/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.toPlaceholderMetadata
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class SpotifyCopySyncReport(
    val playlists: Int,
    val created: Int,
    val updated: Int,
    val failed: Int,
    val addedTracks: Int,
)

/**
 * Keeps one editable Personal copy of every Spotify playlist. The track lists are read from
 * Spotify in parallel and written as placeholder rows in a single transaction, so nothing waits
 * for YouTube matching. Copies only ever gain songs.
 */
@Singleton
class SpotifyCopySync
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val repository: SpotifyLibraryRepository,
        private val writer: PlaylistCopyWriter,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private var job: Job? = null

        /** Starts [sync] unless it is already running. */
        fun syncInBackground() {
            if (job?.isActive == true) return
            job =
                scope.launch {
                    try {
                        sync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Spotify copy sync failed")
                    }
                }
        }

        /** Returns null when there is no Spotify session or no playlist. */
        suspend fun sync(): SpotifyCopySyncReport? =
            mutex.withLock {
                val startedAt = System.currentTimeMillis()
                if (!repository.restoreSession().isAuthenticated) return@withLock null
                // A playlist without a track count is simply read, so the per-playlist count requests
                // refreshPlaylists makes would only slow this down.
                val spotifyPlaylists = repository.playlists.value.ifEmpty { repository.playlistsWithoutCounts() }
                if (spotifyPlaylists.isEmpty()) return@withLock null
                val listedAt = System.currentTimeMillis()

                val copiesBySource =
                    database
                        .playlistsByCreateDateAsc()
                        .first()
                        .filter { it.playlist.spotifySourceId != null }
                        .associateBy { it.playlist.spotifySourceId!! }

                val permits = Semaphore(READ_PARALLELISM)
                val results =
                    coroutineScope {
                        spotifyPlaylists
                            .map { playlist ->
                                async { permits.withPermit { planFor(playlist, copiesBySource[playlist.id]) } }
                            }.awaitAll()
                    }
                val plans = results.filterIsInstance<PlanResult.Write>().map { it.plan }
                val readAt = System.currentTimeMillis()

                val written = if (plans.isEmpty()) SpotifyCopyWriteResult(0, 0, 0) else writer.writeSpotifyCopies(plans)
                SpotifyCopySyncReport(
                    playlists = spotifyPlaylists.size,
                    created = written.created,
                    updated = written.updated,
                    failed = results.count { it is PlanResult.Failed },
                    addedTracks = written.addedTracks,
                ).also {
                    val now = System.currentTimeMillis()
                    Timber.tag(TAG).i(
                        "Spotify copy sync: %s in %d ms (listing %d ms, reading %d ms, writing %d ms)",
                        it,
                        now - startedAt,
                        listedAt - startedAt,
                        readAt - listedAt,
                        now - readAt,
                    )
                }
            }

        private suspend fun planFor(
            playlist: SpotifyPlaylist,
            copy: Playlist?,
        ): PlanResult {
            val total = playlist.tracks?.total
            // A copy that already has every song Spotify reports is left alone, and so is a
            // copy that was moved to YouTube, which is only changed by explicit actions.
            if (copy != null && (copy.playlist.browseId != null || (copy.songCount > 0 && total == copy.playlist.remoteSongCount))) {
                return PlanResult.UpToDate
            }
            return try {
                val tracks = repository.playlistTracks(playlist.id)
                PlanResult.Write(
                    SpotifyCopyPlan(
                        spotifyId = playlist.id,
                        name = playlist.name,
                        thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                        remoteCount = total ?: tracks.size,
                        tracks = tracks.mapNotNull { it.toPlaceholderMetadata() }.map { CopyTrack(songId = it.id, metadata = it) },
                        existingCopyId = copy?.id,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not read Spotify playlist '%s'", playlist.name)
                PlanResult.Failed
            }
        }

        private sealed interface PlanResult {
            data object UpToDate : PlanResult

            data object Failed : PlanResult

            data class Write(
                val plan: SpotifyCopyPlan,
            ) : PlanResult
        }

        private companion object {
            const val TAG = "SpotifyCopySync"
            const val READ_PARALLELISM = 12
        }
    }
