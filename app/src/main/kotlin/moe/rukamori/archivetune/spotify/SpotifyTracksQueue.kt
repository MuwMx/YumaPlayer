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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

open class SpotifyTracksQueue(
    protected val title: String? = null,
    protected val initialTracks: List<SpotifyTrack> = emptyList(),
    protected val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
    protected val totalCount: Int? = null,
    protected val hasCustomOrder: Boolean = false,
) : Queue {
    constructor(
        allTracks: List<SpotifyTrack>,
        startIndex: Int = 0,
        preloadItem: MediaMetadata? = null,
        title: String? = null,
        totalCount: Int? = null,
        hasCustomOrder: Boolean = false,
    ) : this(
        title = title,
        initialTracks = allTracks,
        startIndex = startIndex,
        preloadItem = preloadItem,
        totalCount = totalCount,
        hasCustomOrder = hasCustomOrder,
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
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            try {
                allTracks.clear()
                apiFetchOffset = 0
                apiTotal = 0
                apiHasMore = true

                val provided = providedTracks
                if (provided != null) {
                    allTracks.addAll(provided)
                    if (hasCustomOrder) {
                        apiTotal = provided.size
                        apiFetchOffset = provided.size
                        apiHasMore = false
                    } else if (totalCount != null) {
                        apiTotal = totalCount
                        apiFetchOffset = initialTracks.size
                        apiHasMore = apiFetchOffset < apiTotal
                    } else {
                        apiTotal = provided.size
                        apiFetchOffset = apiTotal
                        apiHasMore = false
                    }
                } else {
                    val page = fetchPage(offset = 0, limit = SPOTIFY_PAGE_SIZE)
                    apiTotal = totalCount ?: page.total
                    allTracks.addAll(page.tracks)
                    apiFetchOffset = page.rawCount
                    apiHasMore = if (hasCustomOrder) false else apiFetchOffset < apiTotal
                }

                if (providedTracks == null && allTracks.size <= startIndex) {
                    while (apiHasMore && allTracks.size <= startIndex) {
                        val prevSize = allTracks.size
                        fetchNextApiPage()
                        if (allTracks.size <= prevSize) break
                    }
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

                val preloaded = preloadItem
                val semaphore = Semaphore(RESOLVE_BATCH_SIZE)
                val resolved =
                    coroutineScope {
                        allTracks.mapIndexed { index, track ->
                            async {
                                if (index == targetIndex && preloaded != null) {
                                    preloaded.toMediaItem()
                                } else {
                                    semaphore.withPermit {
                                        SpotifyPlaybackResolver.resolveToMediaItem(track)
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                val resolvedItems = resolved.filterNotNull()
                if (resolvedItems.isEmpty()) {
                    Timber.tag("SpotifyPipeline").w("Could not resolve any track for initial status")
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                val targetResolved = resolved.getOrNull(targetIndex) != null
                if (!targetResolved) {
                    Timber.tag("SpotifyPipeline").w("Target track at index $targetIndex resolved to null")
                }

                val mediaItemIndex =
                    resolved
                        .take(targetIndex)
                        .count { it != null }
                        .coerceIn(0, resolvedItems.size - 1)

                Timber.tag("SpotifyPipeline").d(
                    "Initial status resolved ${resolvedItems.size} tracks (targetIndex=$targetIndex, mediaItemIndex=$mediaItemIndex, total=$apiTotal)"
                )

                Queue.Status(
                    title = title,
                    items = resolvedItems,
                    mediaItemIndex = mediaItemIndex,
                )
            } catch (e: Exception) {
                Timber.tag("SpotifyPipeline").e(e, "Failed initial fetch")
                Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }
        }

    override fun hasNextPage(): Boolean = apiHasMore

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (!apiHasMore) return@withContext emptyList()

            val previousCount = allTracks.size
            fetchNextApiPage(limit = SPOTIFY_PAGE_SIZE)

            if (allTracks.size <= previousCount) {
                return@withContext emptyList()
            }

            val newTracks = allTracks.subList(previousCount, allTracks.size)
            val semaphore = Semaphore(RESOLVE_BATCH_SIZE)
            val resolvedBatch =
                coroutineScope {
                    newTracks
                        .map { track ->
                            async {
                                semaphore.withPermit {
                                    SpotifyPlaybackResolver.resolveToMediaItem(track)
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }

            Timber.tag("SpotifyPipeline").d(
                "nextPage resolved ${resolvedBatch.size}/${newTracks.size} tracks"
            )

            resolvedBatch
        }

    private suspend fun fetchNextApiPage(limit: Int = SPOTIFY_PAGE_SIZE) {
        if (!apiHasMore) return
        try {
            val page = fetchPage(offset = apiFetchOffset, limit = limit)
            val nonLocalTracks = page.tracks.filter { !it.isLocal }
            allTracks.addAll(nonLocalTracks)
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
    }
}
