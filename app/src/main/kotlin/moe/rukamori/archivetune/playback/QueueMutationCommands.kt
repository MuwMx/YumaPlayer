/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import moe.rukamori.archivetune.playback.queue.ReadOnlyTimelineDelegate
import moe.rukamori.archivetune.playback.queue.performAddToQueueHostInsert
import moe.rukamori.archivetune.playback.queue.performPlayNextHostInsert

@OptIn(UnstableApi::class)
internal class QueueMutationCommands(
    private val delegate: Delegate,
) {
    interface Delegate : ReadOnlyTimelineDelegate {
        override fun getCurrentTimeline(): Timeline
        override fun getMediaItemCount(): Int
        override fun getCurrentMediaItemIndex(): Int

        fun isShuffleModeEnabled(): Boolean
        fun setSuppressAutoPlayback(suppress: Boolean)
        fun addMediaItems(items: List<MediaItem>)
        fun addMediaItems(
            index: Int,
            items: List<MediaItem>,
        )
        fun removeMediaItems(
            fromIndex: Int,
            toIndex: Int,
        )
        fun setShuffleOrder(shuffleOrder: ShuffleOrder)
        fun prepare()
        fun stop()
        fun clearMediaItems()
        fun setPlayWhenReady(playWhenReady: Boolean)

        fun cancelRestoredQueueHydration()
        fun cancelInfiniteQueueBootstrap()
        fun cancelCrossfade(
            resetVolume: Boolean,
            resetPauseAtEnd: Boolean,
        )
        fun cancelNetworkStallRecovery()
        fun setWaitingForNetworkConnection(waiting: Boolean)
        fun clearCurrentMediaMetadata()
        fun abandonAudioFocus()
        fun closeAudioEffectSession()
        fun resetConsecutivePlaybackErr()

        fun clearAutomix()
        fun clearPersistedQueueFiles()
        fun resetQueueHolders()
    }

    fun clearQueue() {
        delegate.cancelInfiniteQueueBootstrap()
        val currentIdx = delegate.getCurrentMediaItemIndex()
        val count = delegate.getMediaItemCount()
        if (currentIdx < 0 || count <= 1) return

        val countAfter = count - (currentIdx + 1)
        if (countAfter > 0) {
            delegate.removeMediaItems(currentIdx + 1, count)
        }
        if (currentIdx > 0) {
            delegate.removeMediaItems(0, currentIdx)
        }
        delegate.resetQueueHolders()
    }

    fun stopAndClearPlayback(clearPersistentState: Boolean = false) {
        delegate.cancelRestoredQueueHydration()
        delegate.cancelInfiniteQueueBootstrap()
        delegate.setSuppressAutoPlayback(true)
        delegate.cancelCrossfade(
            resetVolume = true,
            resetPauseAtEnd = true,
        )
        delegate.clearAutomix()
        delegate.resetQueueHolders()
        delegate.cancelNetworkStallRecovery()
        delegate.setWaitingForNetworkConnection(false)
        delegate.clearCurrentMediaMetadata()
        delegate.setPlayWhenReady(false)
        delegate.stop()
        delegate.clearMediaItems()
        delegate.abandonAudioFocus()
        delegate.closeAudioEffectSession()
        delegate.resetConsecutivePlaybackErr()
        if (clearPersistentState) {
            delegate.clearPersistedQueueFiles()
        }
    }

    fun performPlayNextHostInsert(items: List<MediaItem>) {
        performPlayNextHostInsert(
            timeline = delegate.getCurrentTimeline(),
            mediaItemCount = delegate.getMediaItemCount(),
            currentIndex = delegate.getCurrentMediaItemIndex(),
            items = items,
            isShuffleModeEnabled = delegate.isShuffleModeEnabled(),
            onSetSuppressAutoPlayback = delegate::setSuppressAutoPlayback,
            onAddMediaItems = delegate::addMediaItems,
            onSetShuffleOrder = delegate::setShuffleOrder,
            onPrepare = delegate::prepare,
        )
    }

    fun performAddToQueueHostInsert(items: List<MediaItem>) {
        performAddToQueueHostInsert(
            items = items,
            onSetSuppressAutoPlayback = delegate::setSuppressAutoPlayback,
            onAddMediaItems = delegate::addMediaItems,
            onPrepare = delegate::prepare,
        )
    }
}
