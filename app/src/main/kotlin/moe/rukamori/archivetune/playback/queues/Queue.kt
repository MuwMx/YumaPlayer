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

        fun filterBlockedArtists(blockedArtistIds: Set<String>) =
            if (blockedArtistIds.isEmpty()) {
                this
            } else {
                filterItems { !it.hasBlockedArtist(blockedArtistIds) }
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
    override val isContextQueue: Boolean get() = true
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

fun List<MediaItem>.filterBlockedArtists(blockedArtistIds: Set<String>) =
    if (blockedArtistIds.isEmpty()) {
        this
    } else {
        filterNot { it.hasBlockedArtist(blockedArtistIds) }
    }

fun MediaItem.hasBlockedArtist(blockedArtistIds: Set<String>): Boolean =
    metadata?.artists?.any { artist -> artist.id != null && artist.id in blockedArtistIds } == true
