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
        fun getPlaybackState(): Int
        fun seekToNext()
        fun isPlayWhenReady(): Boolean
        fun isSuppressAutoPlayback(): Boolean
        fun getRepeatMode(): Int
        fun triggerPagination(isPlaybackEnded: Boolean)
    }

    private sealed interface FollowUpAction {
        data object TriggerPaginationEnded : FollowUpAction
        data object TriggerPaginationNearEnd : FollowUpAction
        data object InfiniteQueue : FollowUpAction
    }

    internal var currentQueue: Queue = queueHolder.currentQueue
    internal var queueTitle: String? = queueHolder.queueTitle
    private var playQueueJob: Job? = null
    @Volatile
    private var playGeneration = 0L
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
        val generation = ++playGeneration
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
            var followUpAction: FollowUpAction? = null
            try {
                val hideExplicit = dataStore.get(HideExplicitKey, false)
                val hideVideo = dataStore.get(HideVideoKey, false)
                val initialStatus = withContext(ioDispatcher) {
                    queue.getInitialStatus().filterExplicit(hideExplicit).filterVideo(hideVideo)
                }
                if (!isActive || generation != playGeneration || getActiveQueue() !== queue) return@launch
                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                    queueHolder.queueTitle = initialStatus.title
                }
                if (initialStatus.items.isEmpty()) return@launch
                if (queue.preloadItem != null) {
                    val preloadMediaId = queue.preloadItem!!.toMediaItem().mediaId
                    val candidateIndex = initialStatus.mediaItemIndex
                    val preloadIndexInItems =
                        if (candidateIndex in initialStatus.items.indices && initialStatus.items[candidateIndex].mediaId == preloadMediaId) {
                            candidateIndex
                        } else {
                            val indices = initialStatus.items.indices.filter { initialStatus.items[it].mediaId == preloadMediaId }
                            indices.minByOrNull { kotlin.math.abs(it - candidateIndex) } ?: -1
                        }

                    val wasEndedBeforeAdd = delegate.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED && delegate.isPlayWhenReady()

                    if (preloadIndexInItems >= 0) {
                        val before = initialStatus.items.subList(0, preloadIndexInItems)
                        val after = initialStatus.items.subList(preloadIndexInItems + 1, initialStatus.items.size)
                        if (before.isNotEmpty()) {
                            delegate.addMediaItems(0, before)
                        }
                        if (after.isNotEmpty()) {
                            delegate.addMediaItems(after)
                        }
                        if (delegate.isShuffleModeEnabled()) {
                            applyCurrentFirstShuffleOrder()
                        }
                        if (wasEndedBeforeAdd) {
                            if (after.isNotEmpty()) {
                                delegate.seekToNext()
                                delegate.prepare()
                                delegate.setPlayWhenReady(playWhenReady)
                            } else if (queue.hasNextPage() || queue.hasPendingContextItems) {
                                followUpAction = FollowUpAction.TriggerPaginationEnded
                            } else if (dataStore.get(AutoLoadMoreKey, true) &&
                                !delegate.isSuppressAutoPlayback() &&
                                delegate.getRepeatMode() == androidx.media3.common.Player.REPEAT_MODE_OFF
                            ) {
                                followUpAction = FollowUpAction.InfiniteQueue
                            }
                        } else {
                            val remaining = delegate.getMediaItemCount() - delegate.getCurrentMediaItemIndex()
                            if (queue.isContextQueue && remaining <= 5 && (queue.hasNextPage() || queue.hasPendingContextItems)) {
                                followUpAction = FollowUpAction.TriggerPaginationNearEnd
                            }
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
                } else {
                    val items = initialStatus.items
                    val index = initialStatus.mediaItemIndex

                    delegate.setMediaItems(items, index, initialStatus.position)
                    delegate.prepare()
                    delegate.setPlayWhenReady(playWhenReady)
                    if (delegate.isShuffleModeEnabled()) {
                        applyCurrentFirstShuffleOrder()
                    }
                    val remaining = delegate.getMediaItemCount() - delegate.getCurrentMediaItemIndex()
                    if (queue.isContextQueue && remaining <= 5 && (queue.hasNextPage() || queue.hasPendingContextItems)) {
                        followUpAction = FollowUpAction.TriggerPaginationNearEnd
                    }
                }
            } finally {
                if (generation == playGeneration) {
                    setInitializingQueue(false)
                }
            }

            if (!isActive || generation != playGeneration || getActiveQueue() !== queue) return@launch
            when (followUpAction) {
                FollowUpAction.TriggerPaginationEnded -> {
                    delegate.triggerPagination(isPlaybackEnded = true)
                }
                FollowUpAction.TriggerPaginationNearEnd -> {
                    delegate.triggerPagination(isPlaybackEnded = false)
                }
                FollowUpAction.InfiniteQueue -> {
                    delegate.onInfiniteQueueEnabled()
                }
                null -> Unit
            }

            if (queue.isContextQueue) {
                return@launch
            }
            scope.launch(SilentHandler) {
                if (generation != playGeneration || getActiveQueue() !== queue) return@launch
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
