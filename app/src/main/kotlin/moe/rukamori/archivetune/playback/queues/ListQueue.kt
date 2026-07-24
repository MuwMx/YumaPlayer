/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val safeStartIndex =
            if (items.isEmpty()) {
                0
            } else {
                startIndex.coerceIn(items.indices)
            }

        return Queue.Status(title, items, safeStartIndex, position)
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
