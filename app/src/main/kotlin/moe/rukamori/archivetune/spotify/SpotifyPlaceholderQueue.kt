/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
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

/**
 * Queues the whole list in order with [startIndex] as the current song, like any
 * other playlist. Lists without Spotify placeholders stay a plain [ListQueue].
 */
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

/**
 * A full, ordered playlist queue that may contain Spotify placeholders.
 *
 * Placeholders that already have a stored YouTube match are swapped for the
 * matched song. The rest stay in the queue and the player matches them just
 * before they play. The queue is never paged, so nothing is inserted above the
 * current song later.
 */
class SpotifyPlaceholderQueue(
    private val title: String?,
    private val items: List<MediaMetadata>,
    private val startIndex: Int = 0,
    private val storedMatches: suspend (List<MediaMetadata>) -> Map<String, MediaMetadata> = {
        SpotifyPlaybackResolver.resolveStoredPlaceholders(it)
    },
) : Queue {
    private val safeStartIndex: Int
        get() = if (items.isEmpty()) 0 else startIndex.coerceIn(items.indices)

    override val preloadItem: MediaMetadata?
        get() = items.getOrNull(safeStartIndex)

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            SpotifyPlaceholders.register(items)
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
            // The selected song keeps the id it was started with: the player is
            // already loading it from [preloadItem], and a different id at the
            // same position would make it restart. A placeholder there is
            // matched by the player itself.
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
