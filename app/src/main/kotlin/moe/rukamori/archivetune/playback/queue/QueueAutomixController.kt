/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queue

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber
import java.util.Collections

@OptIn(UnstableApi::class)
internal class QueueAutomixController(
    private val scope: CoroutineScope,
    private val delegate: Delegate,
    private val dataStore: DataStore<Preferences>? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        scope: CoroutineScope,
        dataStore: DataStore<Preferences>,
        delegate: Delegate,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(scope, delegate, dataStore, ioDispatcher)

    interface Delegate : ReadOnlyTimelineDelegate {
        var currentQueue: Queue
        var queueTitle: String?

        fun getActiveQueue(): Queue = currentQueue

        fun getCurrentMetadata(): MediaMetadata?
        fun isCurrentSongLocal(): Boolean
        fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean

        fun getPlaybackState(): Int
        fun getMediaItemAt(index: Int): MediaItem
        fun addMediaItems(items: List<MediaItem>)
        fun addMediaItems(index: Int, items: List<MediaItem>)
        fun removeMediaItem(index: Int)
        fun removeMediaItems(fromIndex: Int, toIndex: Int)
        fun seekToNext()
        fun play()
        fun setSuppressAutoPlayback(suppress: Boolean)
        fun setInfiniteQueueLoading(isLoading: Boolean)
        fun recordAutoAddedMediaId(mediaId: String)
        fun clearAutoAddedMediaIds()

        fun getTogetherSessionState(): Any?
        fun isTogetherApplyingRemote(): Boolean
        fun showTogetherNotice(message: String, key: String)

        fun getString(resId: Int): String
        fun showToast(resId: Int)

        val dataStore: DataStore<Preferences>? get() = null
    }

    private var infiniteQueueJob: Job? = null
    @Volatile
    private var isInfiniteQueueLoading = false
    private val autoAddedMediaIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val effectiveDataStore: DataStore<Preferences>?
        get() = dataStore ?: delegate.dataStore

    private fun setInfiniteQueueLoading(loading: Boolean) {
        isInfiniteQueueLoading = loading
        delegate.setInfiniteQueueLoading(loading)
    }

    fun startRadioSeamlessly() {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        when (
            val decision =
                gateStartRadio(
                    role = joined?.role,
                    allowGuestsToControlPlayback = joined?.roomState?.settings?.allowGuestsToControlPlayback ?: false,
                    isApplyingRemote = delegate.isTogetherApplyingRemote(),
                )
        ) {
            is GuestRadioGateDecision.Denied -> {
                delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = decision.noticeKey)
                return
            }

            GuestRadioGateDecision.PassThrough -> Unit
        }
        cancelInfiniteQueueBootstrap()
        delegate.setSuppressAutoPlayback(false)
        val currentMediaMetadata = delegate.getCurrentMetadata() ?: return

        val currentIndex = delegate.getCurrentMediaItemIndex()
        val currentMediaId = currentMediaMetadata.id
        if (delegate.isCurrentSongLocal() || currentMediaId.isLocalMediaId()) {
            return
        }

        scope.launch(
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Failed to start radio seamlessly")
            },
        ) {
            val radioQueue =
                YouTubeQueue(
                    endpoint = WatchEndpoint(videoId = currentMediaId),
                    followAutomixPreview = true,
                )
            val ds = effectiveDataStore
            val hideExplicit = ds?.get(HideExplicitKey, false) ?: false
            val hideVideo = ds?.get(HideVideoKey, false) ?: false
            val initialStatus =
                withContext(ioDispatcher) {
                    radioQueue
                        .getInitialStatus()
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)
                }

            if (initialStatus.title != null) {
                delegate.queueTitle = initialStatus.title
            }

            val radioItems =
                initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

            if (radioItems.isNotEmpty()) {
                val itemCount = delegate.getMediaItemCount()

                if (itemCount > currentIndex + 1) {
                    delegate.removeMediaItems(currentIndex + 1, itemCount)
                }

                delegate.addMediaItems(currentIndex + 1, radioItems)
            } else {
                withContext(Dispatchers.Main) {
                    delegate.showToast(R.string.no_results_found)
                }
            }

            delegate.currentQueue = radioQueue
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    fun clearAutomix() {
        autoAddedMediaIds.clear()
        delegate.clearAutoAddedMediaIds()
    }

    fun onInfiniteQueueDisabled() {
        cancelInfiniteQueueBootstrap()
        val currentIndex = delegate.getCurrentMediaItemIndex()
        val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
        if (idsToRemove.isEmpty()) {
            return
        }
        for (i in delegate.getMediaItemCount() - 1 downTo 0) {
            if (i == currentIndex) continue
            val item = delegate.getMediaItemAt(i)
            if (item.mediaId in idsToRemove) {
                delegate.removeMediaItem(i)
            }
        }
        autoAddedMediaIds.clear()
        delegate.clearAutoAddedMediaIds()
        delegate.currentQueue = EmptyQueue
    }

    fun onInfiniteQueueEnabled() {
        if (infiniteQueueJob?.isActive == true) return
        if (delegate.getActiveQueue() is SpotifyTracksQueue) return
        val currentMeta = delegate.getCurrentMetadata() ?: return
        if (delegate.isCurrentPlaybackItemLocal(currentMeta)) return
        if (isInfiniteQueueLoading) return
        setInfiniteQueueLoading(true)

        infiniteQueueJob =
            scope.launch(SilentHandler) {
                try {
                    val radioQueue = YouTubeQueue(WatchEndpoint(videoId = currentMeta.id), followAutomixPreview = true)
                    val status = withContext(ioDispatcher) { radioQueue.getInitialStatus() }

                    val count = delegate.getMediaItemCount()
                    val existingIds = (0 until count).map { delegate.getMediaItemAt(it).mediaId }.toSet()
                    val newItems = status.items.filter { it.mediaId !in existingIds }

                    if (newItems.isNotEmpty()) {
                        delegate.addMediaItems(newItems)
                        newItems.forEach {
                            autoAddedMediaIds.add(it.mediaId)
                            delegate.recordAutoAddedMediaId(it.mediaId)
                        }
                    }

                    delegate.currentQueue = radioQueue

                    if (delegate.getPlaybackState() == Player.STATE_ENDED || delegate.getMediaItemCount() == delegate.getCurrentMediaItemIndex() + 1) {
                        delegate.seekToNext()
                        delegate.play()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to bootstrap auto-queue")
                } finally {
                    infiniteQueueJob = null
                    setInfiniteQueueLoading(false)
                }
            }
    }

    fun cancelInfiniteQueueBootstrap() {
        infiniteQueueJob?.cancel()
        infiniteQueueJob = null
        setInfiniteQueueLoading(false)
    }
}
