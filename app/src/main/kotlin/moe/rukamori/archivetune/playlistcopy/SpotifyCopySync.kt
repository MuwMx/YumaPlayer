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

@Singleton
class SpotifyCopySync
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val repository: SpotifyLibraryRepository,
        private val writer: PlaylistCopyWriter,
        private val snapshots: SpotifyCopySnapshots,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private var job: Job? = null

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

        suspend fun sync(): SpotifyCopySyncReport? =
            mutex.withLock {
                val startedAt = System.currentTimeMillis()
                if (!repository.restoreSession().isAuthenticated) return@withLock null
                val spotifyPlaylists = repository.playlists.value.ifEmpty { repository.playlistsWithoutCounts() }
                if (spotifyPlaylists.isEmpty()) return@withLock null
                val listedAt = System.currentTimeMillis()
                database.deleteLegacyLocalFileMatches()

                val copiesBySource =
                    database
                        .playlistsByCreateDateAsc()
                        .first()
                        .filter { it.playlist.spotifySourceId != null }
                        .associateBy { it.playlist.spotifySourceId!! }

                val countPermits = Semaphore(COUNT_PARALLELISM)
                val readPermits = Semaphore(READ_PARALLELISM)
                val results =
                    coroutineScope {
                        spotifyPlaylists
                            .map { playlist ->
                                async { planFor(playlist, copiesBySource[playlist.id], countPermits, readPermits) }
                            }.awaitAll()
                    }
                val plans = results.filterIsInstance<PlanResult.Write>().map { it.plan }
                val readAt = System.currentTimeMillis()

                val written = if (plans.isEmpty()) SpotifyCopyWriteResult(0, 0, 0) else writer.writeSpotifyCopies(plans)
                plans.forEach { plan -> snapshots.write(plan.spotifyId, plan.tracks.map { it.songId }) }
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
            countPermits: Semaphore,
            readPermits: Semaphore,
        ): PlanResult {
            if (copy != null && copy.playlist.browseId != null) return PlanResult.UpToDate
            val synced = copy?.let { snapshots.read(playlist.id) }
            val canSkip = copy != null && synced != null && database.legacyLocalFileMaps(copy.id).isEmpty()
            val total =
                playlist.tracks?.total
                    ?: if (canSkip) countPermits.withPermit { repository.playlistTrackCount(playlist.id) } else null
            if (canSkip && total != null && total == copy!!.playlist.remoteSongCount) return PlanResult.UpToDate
            return try {
                val tracks = readPermits.withPermit { repository.playlistTracks(playlist.id) }
                PlanResult.Write(
                    SpotifyCopyPlan(
                        spotifyId = playlist.id,
                        name = playlist.name,
                        thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(playlist),
                        remoteCount = total ?: tracks.size,
                        tracks = tracks.mapNotNull { it.toPlaceholderMetadata() }.map { CopyTrack(songId = it.id, metadata = it) },
                        existingCopyId = copy?.id,
                        syncedSongIds = synced,
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
            const val COUNT_PARALLELISM = 8
            const val READ_PARALLELISM = 2
        }
    }
