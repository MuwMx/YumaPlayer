/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.listeners

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.playback.listeners.MusicServicePlayerListeners
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.SpotifyQueue
import moe.rukamori.archivetune.playback.queues.filterExplicit
import moe.rukamori.archivetune.playback.queues.filterVideo
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

/**
 * Resolves one backward Spotify batch per explicit request and prepends it at index 0.
 *
 * This path is user-intent driven: it never runs on timeline transitions and never seeds
 * an infinite queue. Playback state and the current media item stay owned by Media3 because
 * the batch is inserted before the current item without seek, prepare or play requests.
 *
 * A resolved batch is kept when the player is temporarily idle or the same queue is being
 * reinitialized; it is retried on the next request instead of being dropped. The queue
 * generation rejects batches resolved before a reinitialization of the same queue instance.
 */
internal class QueueBackwardPaging(
    private val scope: CoroutineScope,
    private val playerDelegate: MusicServicePlayerListeners.PlayerDelegate,
    private val queueDelegate: MusicServicePlayerListeners.QueueDelegate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private data class PendingBatch(
        val queue: SpotifyQueue,
        val generation: Long,
        val items: List<MediaItem>,
    )

    private var job: Job? = null
    private var jobQueue: Queue? = null
    private var pendingBatch: PendingBatch? = null

    fun hasPendingBatch(): Boolean {
        val retained = pendingBatch ?: return false
        val current = queueDelegate.currentQueue
        if (retained.queue !== current || retained.generation != retained.queue.contextGeneration) {
            pendingBatch = null
            return false
        }
        return true
    }

    fun request(): Boolean {
        val target = queueDelegate.currentQueue as? SpotifyQueue ?: return false
        if (queueDelegate.isInitializingQueue) return false
        val player = playerDelegate.player
        if (player.playbackState == Player.STATE_IDLE) return false

        val active = job
        if (active?.isActive == true) {
            if (jobQueue === target) return false
            active.cancel()
        }

        val retained = pendingBatch
        if (retained != null) {
            if (retained.queue !== target || retained.generation != target.contextGeneration) {
                pendingBatch = null
            } else {
                player.addMediaItems(0, retained.items)
                pendingBatch = null
                Timber.tag(TAG).d("Retried ${retained.items.size} retained backward tracks")
                return true
            }
        }

        if (!target.hasPreviousPage()) return false

        val newJob =
            scope.launch(start = CoroutineStart.LAZY, context = SilentHandler) {
                queueDelegate.setPreviousQueueLoading(true)
                try {
                    var loadedItems = emptyList<MediaItem>()
                    var batchGeneration = target.contextGeneration
                    while (target.hasPreviousPage() && loadedItems.isEmpty()) {
                        val batch =
                            withContext(ioDispatcher) {
                                val page = target.previousPage()
                                batchGeneration = page.generation
                                page.items
                                    .filterExplicit(queueDelegate.dataStore.get(HideExplicitKey, false))
                                    .filterVideo(queueDelegate.dataStore.get(HideVideoKey, false))
                            }
                        if (!isActive || queueDelegate.currentQueue !== target) return@launch
                        if (batchGeneration != target.contextGeneration) {
                            Timber.tag(TAG).d("Discarded backward batch after queue reinitialization")
                            return@launch
                        }
                        loadedItems = batch
                    }

                    if (!isActive || queueDelegate.currentQueue !== target) return@launch
                    if (loadedItems.isEmpty()) return@launch
                    if (batchGeneration != target.contextGeneration) return@launch

                    if (queueDelegate.isInitializingQueue ||
                        playerDelegate.player.playbackState == Player.STATE_IDLE
                    ) {
                        pendingBatch = PendingBatch(target, batchGeneration, loadedItems)
                        Timber.tag(TAG).w("Retained backward batch until the queue can accept it")
                        return@launch
                    }

                    playerDelegate.player.addMediaItems(0, loadedItems)
                    Timber.tag(TAG).d("Prepended ${loadedItems.size} backward tracks")
                } finally {
                    if (job === coroutineContext[Job]) {
                        job = null
                        jobQueue = null
                        queueDelegate.setPreviousQueueLoading(false)
                    }
                }
            }
        job = newJob
        jobQueue = target
        newJob.start()
        return true
    }

    private companion object {
        const val TAG = "QueueBackwardPaging"
    }
}
