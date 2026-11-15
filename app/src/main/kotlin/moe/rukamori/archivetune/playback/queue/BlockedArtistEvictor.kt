/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.playback.queues.hasBlockedArtist

internal class BlockedArtistEvictor(
    private val scope: CoroutineScope,
    private val database: MusicDatabase,
    private val delegate: Delegate,
) {
    interface Delegate {
        fun getMediaItemCount(): Int
        fun getCurrentMediaItemIndex(): Int
        fun getMediaItemAt(index: Int): MediaItem
        fun seekToDefaultPosition(index: Int)
        fun removeMediaItem(index: Int)
        fun stopAndClearPlayback()
    }

    fun start() {
        scope.launch(SilentHandler) {
            combine(
                database.blockedArtistIds(),
                database.blockedArtistNames(),
            ) { ids, names ->
                ids.toSet() to names.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            }
                .distinctUntilChanged()
                .collect { (blockedIds, blockedNames) ->
                    if (blockedIds.isEmpty() && blockedNames.isEmpty()) return@collect
                    withContext(Dispatchers.Main) {
                        evictBlockedArtists(blockedIds, blockedNames)
                    }
                }
        }
    }

    internal fun evictBlockedArtists(blockedIds: Set<String>, blockedNames: Set<String>) {
        val totalCount = delegate.getMediaItemCount()
        if (totalCount == 0) return

        val blockedIndices = mutableListOf<Int>()
        for (i in 0 until totalCount) {
            val item = delegate.getMediaItemAt(i)
            if (item.hasBlockedArtist(blockedIds, blockedNames)) {
                blockedIndices.add(i)
            }
        }

        if (blockedIndices.isEmpty()) return

        if (blockedIndices.size == totalCount) {
            delegate.stopAndClearPlayback()
            return
        }

        val currentIndex = delegate.getCurrentMediaItemIndex()
        if (currentIndex in blockedIndices) {
            val nextPlayableIndex = (currentIndex + 1 until totalCount).firstOrNull { it !in blockedIndices }
                ?: (0 until currentIndex).firstOrNull { it !in blockedIndices }
            if (nextPlayableIndex != null) {
                delegate.seekToDefaultPosition(nextPlayableIndex)
            }
        }

        for (i in blockedIndices.asReversed()) {
            delegate.removeMediaItem(i)
        }
    }
}
