/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

open class SpotifyTracksQueue(
    protected val title: String? = null,
    protected val initialTracks: List<SpotifyTrack> = emptyList(),
    protected val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    constructor(
        allTracks: List<SpotifyTrack>,
        startIndex: Int = 0,
        preloadItem: MediaMetadata? = null,
        title: String? = null,
    ) : this(
        title = title,
        initialTracks = allTracks,
        startIndex = startIndex,
        preloadItem = preloadItem,
    )

    data class PageResult(
        val tracks: List<SpotifyTrack>,
        val total: Int,
        val rawCount: Int,
    )

    protected open val providedTracks: List<SpotifyTrack>? =
        initialTracks.filter { !it.isLocal }.takeIf { it.isNotEmpty() }

    protected open suspend fun fetchPage(offset: Int, limit: Int): PageResult =
        PageResult(tracks = emptyList(), total = 0, rawCount = 0)

    private val allTracks = mutableListOf<SpotifyTrack>()
    private var resolveOffset = 0
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true
    private var isInitialized = false

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            try {
                allTracks.clear()
                resolveOffset = 0
                apiFetchOffset = 0
                apiTotal = 0
                apiHasMore = true

                val provided = providedTracks
                if (provided != null) {
                    allTracks.addAll(provided)
                    apiTotal = provided.size
                    apiFetchOffset = apiTotal
                    apiHasMore = false
                } else {
                    val page = fetchPage(offset = 0, limit = SPOTIFY_PAGE_SIZE)
                    apiTotal = page.total
                    allTracks.addAll(page.tracks)
                    apiFetchOffset = page.rawCount
                    apiHasMore = apiFetchOffset < apiTotal
                }

                while (startIndex >= allTracks.size && apiHasMore) {
                    fetchNextApiPage()
                }

                if (allTracks.isEmpty()) {
                    Timber.tag("SpotifyPipeline").w("No tracks found for initial status")
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                val targetIndex =
                    if (provided != null && startIndex in initialTracks.indices) {
                        val targetTrack = initialTracks[startIndex]
                        val idx = allTracks.indexOfFirst { it.id == targetTrack.id }
                        if (idx >= 0) idx else startIndex.coerceIn(0, allTracks.size - 1)
                    } else {
                        startIndex.coerceIn(0, allTracks.size - 1)
                    }

                val windowStart = (targetIndex - FAST_START_BEFORE).coerceAtLeast(0)
                val windowEnd = (targetIndex + FAST_START_AFTER + 1).coerceAtMost(allTracks.size)
                val windowTracks = allTracks.subList(windowStart, windowEnd)

                val resolvedEntries =
                    coroutineScope {
                        windowTracks
                            .mapIndexed { index, track ->
                                async {
                                    SpotifyPlaybackResolver
                                        .resolveToMediaItem(track)
                                        ?.let { mediaItem -> (windowStart + index) to mediaItem }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }

                if (resolvedEntries.isEmpty()) {
                    Timber.tag("SpotifyPipeline").w("Could not resolve any track in initial window")
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                resolveOffset = windowEnd

                val resolvedItems = resolvedEntries.map { it.second }
                val mediaItemIndex =
                    resolvedEntries
                        .indexOfFirst { it.first >= targetIndex }
                        .takeIf { it >= 0 }
                        ?: resolvedItems.lastIndex

                Timber.tag("SpotifyPipeline").d(
                    "Fast-start resolved ${resolvedItems.size} tracks " +
                        "(window $windowStart..$windowEnd, target=$targetIndex, total=$apiTotal)"
                )

                Queue.Status(
                    title = title,
                    items = resolvedItems,
                    mediaItemIndex = mediaItemIndex,
                )
            } catch (e: Exception) {
                Timber.tag("SpotifyPipeline").e(e, "Failed initial fetch")
                Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            } finally {
                isInitialized = true
            }
        }

    override fun hasNextPage(): Boolean = isInitialized && (resolveOffset < allTracks.size || apiHasMore)

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (resolveOffset >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            if (resolveOffset >= allTracks.size) {
                return@withContext emptyList()
            }

            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val currentOffset = resolveOffset
            val batch = allTracks.subList(currentOffset, end)
            resolveOffset = end

            Timber.tag("SpotifyPipeline").d(
                "Starting batch resolve: offset=$currentOffset, size=${batch.size}"
            )

            val resolvedBatch =
                coroutineScope {
                    batch
                        .map { track ->
                            async {
                                SpotifyPlaybackResolver.resolveToMediaItem(track)
                            }
                        }.awaitAll()
                        .filterNotNull()
                }

            Timber.tag("SpotifyPipeline").d(
                "Batch resolve finished: offset=$currentOffset, resolved=${resolvedBatch.size}/${batch.size}"
            )

            resolvedBatch
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        try {
            val page = fetchPage(offset = apiFetchOffset, limit = SPOTIFY_PAGE_SIZE)
            allTracks.addAll(page.tracks)
            apiFetchOffset += page.rawCount
            apiHasMore = apiFetchOffset < apiTotal
            Timber.tag("SpotifyPipeline").d(
                "Fetched API page, now have ${allTracks.size} tracks (total=$apiTotal)"
            )
        } catch (e: Exception) {
            Timber.tag("SpotifyPipeline").e(e, "Failed to fetch next API page")
            apiHasMore = false
        }
    }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
        private const val FAST_START_BEFORE = 0
        private const val FAST_START_AFTER = 2
    }
}
