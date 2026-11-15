/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.ExtraIsMusicVideo
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = false

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    val isContextQueue: Boolean get() = false
    val isContextLoading: Boolean get() = false
    val hasPendingContextItems: Boolean get() = false
    val isFullyLoaded: Boolean get() = !hasNextPage()
    val isLoadFailed: Boolean get() = false

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.metadata?.explicit != true }
            } else {
                this
            }

        fun filterVideo(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) != true }
            } else {
                this
            }

        fun filterBlockedArtists(
            blockedArtistIds: Set<String>,
            blockedArtistNames: Set<String> = emptySet(),
        ) =
            if (blockedArtistIds.isEmpty() && blockedArtistNames.isEmpty()) {
                this
            } else {
                filterItems { !it.hasBlockedArtist(blockedArtistIds, blockedArtistNames) }
            }

        private inline fun filterItems(keep: (MediaItem) -> Boolean): Status {
            if (items.isEmpty()) return this

            val currentIndex = mediaItemIndex.coerceIn(items.indices)
            var filteredIndex = 0
            val filteredItems =
                buildList(items.size) {
                    items.forEachIndexed { index, item ->
                        if (keep(item)) {
                            if (index < currentIndex) {
                                filteredIndex++
                            }
                            add(item)
                        }
                    }
                }

            if (filteredItems.isEmpty()) {
                return copy(items = emptyList(), mediaItemIndex = 0)
            }

            return copy(
                items = filteredItems,
                mediaItemIndex = filteredIndex.coerceIn(filteredItems.indices),
            )
        }
    }
}

interface SpotifyQueue : Queue {
    data class PreviousPage(
        val items: List<MediaItem>,
        val generation: Long,
    )

    override val isContextQueue: Boolean get() = true

    val contextGeneration: Long get() = 0L

    fun hasPreviousPage(): Boolean = false

    suspend fun previousPage(): PreviousPage = PreviousPage(emptyList(), contextGeneration)
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterBlockedArtists(
    blockedArtistIds: Set<String>,
    blockedArtistNames: Set<String> = emptySet(),
) =
    if (blockedArtistIds.isEmpty() && blockedArtistNames.isEmpty()) {
        this
    } else {
        filterNot { it.hasBlockedArtist(blockedArtistIds, blockedArtistNames) }
    }

fun MediaItem.hasBlockedArtist(
    blockedArtistIds: Set<String>,
    blockedArtistNames: Set<String> = emptySet(),
): Boolean {
    val metaArtists = metadata?.artists
    if (!metaArtists.isNullOrEmpty()) {
        return metaArtists.any { artist ->
            (artist.id != null && artist.id in blockedArtistIds) ||
                artist.name.trim().lowercase() in blockedArtistNames
        }
    }
    val fallbackArtistName = mediaMetadata.artist?.toString()?.trim()?.lowercase()
    if (!fallbackArtistName.isNullOrEmpty() && fallbackArtistName in blockedArtistNames) {
        return true
    }
    return false
}
