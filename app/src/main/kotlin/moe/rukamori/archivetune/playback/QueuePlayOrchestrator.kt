/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queue.ReadOnlyTimelineDelegate
import moe.rukamori.archivetune.playback.queue.applyCurrentFirstShuffleOrder
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.get

@OptIn(UnstableApi::class)
internal class QueuePlayOrchestrator(
    private val queueHolder: PlaybackQueueManager.QueueHolder,
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val delegate: Delegate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    interface Delegate : ReadOnlyTimelineDelegate {
        fun gatePlayQueue(queue: Queue, playWhenReady: Boolean): Boolean
        fun cancelIdleStop()
        fun promoteToStartedService()
        fun ensureStartedAsForeground()
        fun cancelRestoredQueueHydration()
        fun ensureScopesActive()
        fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)
        fun cancelInfiniteQueueBootstrap()
        fun setSuppressAutoPlayback(suppress: Boolean)
        fun setInitializingQueue(isInitializing: Boolean)
        fun isShuffleModeEnabled(): Boolean
        fun setShuffleModeEnabled(enabled: Boolean)
        fun setShuffleOrder(shuffleOrder: ShuffleOrder)
        fun clearAutomix()
        fun setMediaItem(item: MediaItem)
        fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long)
        fun addMediaItems(items: List<MediaItem>)
        fun addMediaItems(index: Int, items: List<MediaItem>)
        fun prepare()
        fun setPlayWhenReady(playWhenReady: Boolean)
        fun onInfiniteQueueEnabled()
    }

    internal var currentQueue: Queue = queueHolder.currentQueue
    internal var queueTitle: String? = queueHolder.queueTitle
    private var playQueueJob: Job? = null
    @Volatile
    internal var isInitializingQueue = false
        private set

    fun getCurrentQueue(): Queue = getActiveQueue()
    fun getQueueTitle(): String? = queueTitle

    fun getActiveQueue(): Queue {
        if (queueHolder.currentQueue !== currentQueue && queueHolder.currentQueue != EmptyQueue) {
            currentQueue = queueHolder.currentQueue
            queueTitle = queueHolder.queueTitle
        }
        return currentQueue
    }

    fun resetQueueHolders() {
        currentQueue = EmptyQueue
        queueTitle = null
        queueHolder.currentQueue = EmptyQueue
        queueHolder.queueTitle = null
    }

    private fun setInitializingQueue(initializing: Boolean) {
        isInitializingQueue = initializing
        delegate.setInitializingQueue(initializing)
    }

    fun applyCurrentFirstShuffleOrder() {
        applyCurrentFirstShuffleOrder(delegate.getMediaItemCount(), delegate.getCurrentMediaItemIndex())
            ?.let(delegate::setShuffleOrder)
    }

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) {
        if (delegate.gatePlayQueue(queue, playWhenReady)) return
        if (playWhenReady) {
            delegate.cancelIdleStop()
            delegate.promoteToStartedService()
            delegate.ensureStartedAsForeground()
        }
        delegate.cancelRestoredQueueHydration()
        delegate.ensureScopesActive()
        delegate.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        delegate.cancelInfiniteQueueBootstrap()
        delegate.setSuppressAutoPlayback(false)
        playQueueJob?.cancel()
        setInitializingQueue(true)
        currentQueue = queue
        queueHolder.currentQueue = queue
        queueTitle = null
        queueHolder.queueTitle = null
        val permanentShuffle = dataStore.get(PermanentShuffleKey, false)
        if (!permanentShuffle) {
            delegate.setShuffleModeEnabled(false)
        }

        delegate.clearAutomix()
        if (queue.preloadItem != null) {
            delegate.setMediaItem(queue.preloadItem!!.toMediaItem())
            delegate.prepare()
            delegate.setPlayWhenReady(playWhenReady)
        }
        playQueueJob = scope.launch(SilentHandler) {
            try {
                val hideExplicit = dataStore.get(HideExplicitKey, false)
                val hideVideo = dataStore.get(HideVideoKey, false)
                val initialStatus = withContext(ioDispatcher) {
                    queue.getInitialStatus().filterExplicit(hideExplicit).filterVideo(hideVideo)
                }
                if (!isActive) return@launch
                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                    queueHolder.queueTitle = initialStatus.title
                }
                if (initialStatus.items.isEmpty()) return@launch
                if (queue.preloadItem != null) {
                    val before = initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                    val after = initialStatus.items.subList(initialStatus.mediaItemIndex + 1, initialStatus.items.size)
                    if (before.isNotEmpty()) {
                        delegate.addMediaItems(0, before)
                    }
                    if (after.isNotEmpty()) {
                        delegate.addMediaItems(after)
                    }
                    if (delegate.isShuffleModeEnabled()) {
                        applyCurrentFirstShuffleOrder()
                    }
                } else {
                    val items = initialStatus.items
                    val index = initialStatus.mediaItemIndex

                    delegate.setMediaItems(items, index, initialStatus.position)
                    delegate.prepare()
                    delegate.setPlayWhenReady(playWhenReady)
                    if (delegate.isShuffleModeEnabled()) {
                        applyCurrentFirstShuffleOrder()
                    }
                }
            } finally {
                setInitializingQueue(false)
            }
            scope.launch(SilentHandler) {
                if (delegate.getMediaItemCount() - delegate.getCurrentMediaItemIndex() <= 3 &&
                    !getActiveQueue().hasNextPage() &&
                    dataStore.get(AutoLoadMoreKey, true)
                ) {
                    delegate.onInfiniteQueueEnabled()
                }
            }
        }
    }
}
