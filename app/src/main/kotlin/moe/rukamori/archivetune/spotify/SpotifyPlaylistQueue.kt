/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.SpotifyQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

class SpotifyPlaylistQueue(
    private val playlistId: String,
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
    private val totalCount: Int? = null,
    private val hasCustomOrder: Boolean = false,
) : SpotifyQueue {
    override val isContextQueue: Boolean = true

    @Volatile
    override var isContextLoading: Boolean = false
        private set

    @Volatile
    override var isLoadFailed: Boolean = false
        private set

    override val hasPendingContextItems: Boolean
        get() = resolveOffset < allTracks.size || apiHasMore

    override val isFullyLoaded: Boolean
        get() = isInitialized && !isContextLoading && !hasPendingContextItems && !isLoadFailed

    override fun hasNextPage(): Boolean =
        isInitialized && !isLoadFailed && (resolveOffset < allTracks.size || apiHasMore)

    private val pagingMutex = Mutex()
    private val allTracks = mutableListOf<SpotifyTrack>()
    private var resolveOffset = 0
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true
    private var isInitialized = false

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            pagingMutex.withLock {
                isContextLoading = true
                isLoadFailed = false
                allTracks.clear()
                resolveOffset = 0
                apiFetchOffset = 0
                apiTotal = 0
                apiHasMore = true
                try {
                    if (initialTracks.isNotEmpty()) {
                        allTracks.addAll(initialTracks)
                        apiTotal = totalCount ?: initialTracks.size
                        apiFetchOffset = initialTracks.size
                        apiHasMore = if (hasCustomOrder) false else apiFetchOffset < apiTotal
                    } else {
                        fetchNextApiPage()
                    }

                    while (startIndex >= allTracks.size && apiHasMore) {
                        fetchNextApiPage()
                    }

                    if (allTracks.isEmpty()) {
                        isLoadFailed = true
                        return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                    }

                    val targetIndex =
                        if (initialTracks.isNotEmpty() && startIndex in initialTracks.indices) {
                            val targetTrack = initialTracks[startIndex]
                            if (startIndex in allTracks.indices && allTracks[startIndex].id == targetTrack.id) {
                                startIndex
                            } else {
                                val idx = allTracks.indexOfFirst { it.id == targetTrack.id }
                                if (idx >= 0) idx else startIndex.coerceIn(allTracks.indices)
                            }
                        } else {
                            startIndex.coerceIn(allTracks.indices)
                        }

                    val resolvedEntries = resolveTrackEntries(allTracks, targetIndex, preloadItem)
                    val resolvedItems = resolvedEntries.map { it.second }

                    resolveOffset = allTracks.size
                    if (resolvedItems.isEmpty()) {
                        isLoadFailed = true
                        return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                    }

                    val mediaItemIndex =
                        resolvedEntries
                            .indexOfFirst { it.first == targetIndex }
                            .takeIf { it >= 0 }
                            ?: resolvedEntries
                                .indexOfFirst { it.first >= targetIndex }
                                .takeIf { it >= 0 }
                            ?: resolvedItems.lastIndex

                    Queue.Status(
                        title = title,
                        items = resolvedItems,
                        mediaItemIndex = mediaItemIndex,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    isLoadFailed = true
                    Timber.tag("SpotifyPipeline").e(e, "Failed initial status in SpotifyPlaylistQueue")
                    Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                } finally {
                    isContextLoading = false
                    isInitialized = true
                }
            }
        }

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            pagingMutex.withLock {
                if (isLoadFailed || !isInitialized) return@withContext emptyList()
                isContextLoading = true
                try {
                    if (resolveOffset >= allTracks.size && apiHasMore) {
                        fetchNextApiPage()
                    }
                    if (resolveOffset >= allTracks.size) return@withContext emptyList()

                    val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
                    val batch = allTracks.subList(resolveOffset, end)
                    val resolved = resolveTracks(batch)
                    resolveOffset = end
                    resolved
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    isLoadFailed = true
                    Timber.tag("SpotifyPipeline").e(e, "Failed nextPage in SpotifyPlaylistQueue")
                    emptyList()
                } finally {
                    isContextLoading = false
                }
            }
        }

    private suspend fun resolveTracks(tracks: List<SpotifyTrack>): List<MediaItem> = resolveTrackEntries(tracks).map { it.second }

    private suspend fun resolveTrackEntries(
        tracks: List<SpotifyTrack>,
        targetIndex: Int = -1,
        preloadedItem: MediaMetadata? = null,
    ): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = chunkIndex * RESOLVE_BATCH_SIZE
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                val trackIndex = chunkOffset + index
                                async {
                                    val item =
                                        if (trackIndex == targetIndex && preloadedItem != null) {
                                            preloadedItem.toMediaItem()
                                        } else {
                                            SpotifyPlaybackResolver.resolveToMediaItem(track)
                                        }
                                    item?.let { trackIndex to it }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                addAll(resolvedChunk)
            }
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        val result =
            Spotify
                .playlistTracks(
                    playlistId = playlistId,
                    limit = SPOTIFY_PAGE_SIZE,
                    offset = apiFetchOffset,
                ).getOrThrow()
        apiTotal = result.total
        val fetched = result.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
        allTracks += fetched
        val rawCount = result.items.size
        apiFetchOffset += rawCount
        if (rawCount == 0) {
            if (apiFetchOffset < apiTotal) {
                isLoadFailed = true
                Timber.tag("SpotifyPipeline").e(
                    "Empty API page with offset $apiFetchOffset < total $apiTotal"
                )
            }
            apiHasMore = false
        } else {
            apiHasMore = apiFetchOffset < apiTotal
        }
    }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
    }
}
