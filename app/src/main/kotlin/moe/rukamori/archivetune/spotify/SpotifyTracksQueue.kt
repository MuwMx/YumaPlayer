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
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
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

    private val allTracks = initialTracks.toList()
    private var resolveOffset = 0
    private var isInitialized = false

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            try {
                if (allTracks.isEmpty()) {
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                val targetIndex = startIndex.coerceIn(allTracks.indices)
                val resolvedEntries = resolveTrackEntries(allTracks)
                val resolvedItems = resolvedEntries.map { it.second }

                resolveOffset = allTracks.size
                if (resolvedItems.isEmpty()) {
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                Queue.Status(
                    title = title,
                    items = resolvedItems,
                    mediaItemIndex =
                        resolvedEntries
                            .indexOfFirst { it.first >= targetIndex }
                            .takeIf { it >= 0 }
                            ?: resolvedItems.lastIndex,
                )
            } finally {
                isInitialized = true
            }
        }

    override fun hasNextPage(): Boolean = isInitialized && resolveOffset < allTracks.size

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (resolveOffset >= allTracks.size) return@withContext emptyList()

            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val currentOffset = resolveOffset
            val batch = allTracks.subList(currentOffset, end)
            resolveOffset = end
            resolveTrackEntries(batch, baseOffset = currentOffset).map { it.second }
        }

    private suspend fun resolveTracks(
        tracks: List<SpotifyTrack>,
        baseOffset: Int = 0,
    ): List<MediaItem> = resolveTrackEntries(tracks, baseOffset = baseOffset).map { it.second }

    private suspend fun resolveTrackEntries(
        tracks: List<SpotifyTrack>,
        baseOffset: Int = 0,
    ): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = baseOffset + chunkIndex * RESOLVE_BATCH_SIZE
                val trackNames = chunk.joinToString(", ") { "${it.name} (${it.id})" }
                Timber.tag("SpotifyPipeline").d(
                    "Starting batch resolve: offset=$chunkOffset, size=${chunk.size}, tracks=[$trackNames]"
                )
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                async {
                                    SpotifyPlaybackResolver
                                        .resolveToMediaItem(track)
                                        ?.let { mediaItem -> chunkOffset + index to mediaItem }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                Timber.tag("SpotifyPipeline").d(
                    "Batch resolve finished: offset=$chunkOffset, resolved=${resolvedChunk.size}/${chunk.size}"
                )
                addAll(resolvedChunk)
            }
        }

    companion object {
        private const val RESOLVE_BATCH_SIZE = 20
    }
}
