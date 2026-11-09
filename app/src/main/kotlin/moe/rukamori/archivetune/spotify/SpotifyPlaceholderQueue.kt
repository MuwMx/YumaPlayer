/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import timber.log.Timber

fun spotifyAwareQueue(
    title: String?,
    items: List<MediaMetadata>,
    startIndex: Int = 0,
): Queue =
    if (items.any { SpotifyPlaceholders.isPlaceholder(it.id) }) {
        SpotifyPlaceholderQueue(title = title, items = items, startIndex = startIndex)
    } else {
        ListQueue(title = title, items = items.map { it.toMediaItem() }, startIndex = startIndex)
    }

class SpotifyPlaceholderQueue(
    private val title: String?,
    private val items: List<MediaMetadata>,
    private val startIndex: Int = 0,
    private val storedMatches: suspend (List<MediaMetadata>) -> Map<String, MediaMetadata> = {
        SpotifyPlaybackResolver.resolveStoredPlaceholders(it)
    },
) : Queue {

    init {
        SpotifyPlaceholders.register(items)
    }
    private val safeStartIndex: Int
        get() = if (items.isEmpty()) 0 else startIndex.coerceIn(items.indices)

    override val preloadItem: MediaMetadata?
        get() = items.getOrNull(safeStartIndex)

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            val placeholders = items.filter { SpotifyPlaceholders.isPlaceholder(it.id) }
            val matches =
                try {
                    storedMatches(placeholders)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("SpotifyQueue").w(e, "Could not read stored matches")
                    emptyMap()
                }
            val resolved =
                items.mapIndexed { index, item ->
                    if (index == safeStartIndex) item else matches[item.id] ?: item
                }
            Timber.tag("SpotifyQueue").d(
                "Queue '%s': %d items, %d placeholders, %d with a stored match, start=%d",
                title,
                items.size,
                placeholders.size,
                matches.size,
                safeStartIndex,
            )
            Queue.Status(
                title = title,
                items = resolved.map(MediaMetadata::toMediaItem),
                mediaItemIndex = safeStartIndex,
            )
        }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
