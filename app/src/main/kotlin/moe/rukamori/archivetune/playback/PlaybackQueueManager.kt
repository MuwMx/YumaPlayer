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
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PermanentShuffleKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queue.GuestAddTracksGateDecision
import moe.rukamori.archivetune.playback.queue.GuestPlayQueuePlan
import moe.rukamori.archivetune.playback.queue.PlayQueueGateDecision
import moe.rukamori.archivetune.playback.queue.QueueAutomixController
import moe.rukamori.archivetune.playback.queue.QueueLibraryToggles
import moe.rukamori.archivetune.playback.queue.ReadOnlyTimelineDelegate
import moe.rukamori.archivetune.playback.queue.applyCurrentFirstShuffleOrder
import moe.rukamori.archivetune.playback.queue.buildPlayNextShuffleOrder
import moe.rukamori.archivetune.playback.queue.gateAddTracks
import moe.rukamori.archivetune.playback.queue.gatePlayQueue
import moe.rukamori.archivetune.playback.queue.performAddToQueueHostInsert
import moe.rukamori.archivetune.playback.queue.performPlayNextHostInsert
import moe.rukamori.archivetune.playback.queue.planGuestPlayQueue
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherGuestOp
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.get

@OptIn(UnstableApi::class)
internal class PlaybackQueueManager(
    private val queueHolder: QueueHolder,
    private val persistenceStore: QueuePersistenceStore,
    private val scope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val delegate: Delegate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    interface QueueHolder {
        var currentQueue: Queue
        var queueTitle: String?
    }

    interface Delegate : ReadOnlyTimelineDelegate {
        override fun getMediaItemCount(): Int
        override fun getCurrentMediaItemIndex(): Int
        fun getMediaItemAt(index: Int): MediaItem
        override fun getCurrentTimeline(): Timeline
        fun getPlaybackState(): Int
        fun getCurrentMetadata(): MediaMetadata?
        fun isShuffleModeEnabled(): Boolean
        fun setShuffleModeEnabled(enabled: Boolean)
        fun setShuffleOrder(shuffleOrder: ShuffleOrder)
        fun setMediaItem(item: MediaItem)
        fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long)
        fun addMediaItems(items: List<MediaItem>)
        fun addMediaItems(index: Int, items: List<MediaItem>)
        fun removeMediaItem(index: Int)
        fun removeMediaItems(fromIndex: Int, toIndex: Int)
        fun clearMediaItems()
        fun prepare()
        fun play()
        fun stop()
        fun seekToNext()
        fun setPlayWhenReady(playWhenReady: Boolean)
        fun isPlayWhenReady(): Boolean

        fun ensureScopesActive()
        fun cancelIdleStop()
        fun promoteToStartedService()
        fun ensureStartedAsForeground()
        fun cancelRestoredQueueHydration()
        fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)
        fun setSuppressAutoPlayback(suppress: Boolean)
        fun setInitializingQueue(isInitializing: Boolean)
        fun setInfiniteQueueLoading(isLoading: Boolean)
        fun cancelNetworkStallRecovery()
        fun setWaitingForNetworkConnection(waiting: Boolean)
        fun clearCurrentMediaMetadata()
        fun abandonAudioFocus()
        fun closeAudioEffectSession()
        fun resetConsecutivePlaybackErr()

        fun recordAutoAddedMediaId(mediaId: String)
        fun clearAutoAddedMediaIds()

        fun getTogetherSessionState(): Any?
        fun isTogetherApplyingRemote(): Boolean
        fun showTogetherNotice(message: String, key: String)
        fun requestTogetherControl(action: ControlAction)
        fun requestTogetherAddTrack(track: TogetherTrack, mode: AddTrackMode)

        fun getString(resId: Int): String
        fun showToast(resId: Int)

        fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean
        fun isCurrentSongLocal(): Boolean
        fun getCurrentSong(): Song?
        fun getCurrentMediaMetadata(): MediaMetadata?
        fun downloadSong(song: SongEntity)
        suspend fun resolveVoiceMediaItems(query: String): List<MediaItem>
    }

    private var currentQueue: Queue = queueHolder.currentQueue
    private var queueTitle: String? = queueHolder.queueTitle
    private var playQueueJob: Job? = null
    @Volatile
    private var isInitializingQueue = false

    private val automixController =
        QueueAutomixController(
            scope = scope,
            dataStore = dataStore,
            ioDispatcher = ioDispatcher,
            delegate = object : QueueAutomixController.Delegate {
                override var currentQueue: Queue
                    get() = getActiveQueue()
                    set(value) {
                        this@PlaybackQueueManager.currentQueue = value
                        queueHolder.currentQueue = value
                    }

                override var queueTitle: String?
                    get() = this@PlaybackQueueManager.queueTitle
                    set(value) {
                        this@PlaybackQueueManager.queueTitle = value
                        queueHolder.queueTitle = value
                    }

                override fun getActiveQueue(): Queue = this@PlaybackQueueManager.getActiveQueue()

                override fun getCurrentTimeline(): Timeline = delegate.getCurrentTimeline()
                override fun getMediaItemCount(): Int = delegate.getMediaItemCount()
                override fun getCurrentMediaItemIndex(): Int = delegate.getCurrentMediaItemIndex()
                override fun getMediaItemAt(index: Int): MediaItem = delegate.getMediaItemAt(index)
                override fun getCurrentMetadata(): MediaMetadata? = delegate.getCurrentMetadata()
                override fun isCurrentSongLocal(): Boolean = delegate.isCurrentSongLocal()
                override fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean =
                    delegate.isCurrentPlaybackItemLocal(metadata)
                override fun getPlaybackState(): Int = delegate.getPlaybackState()
                override fun addMediaItems(items: List<MediaItem>) = delegate.addMediaItems(items)
                override fun addMediaItems(index: Int, items: List<MediaItem>) =
                    delegate.addMediaItems(index, items)
                override fun removeMediaItem(index: Int) = delegate.removeMediaItem(index)
                override fun removeMediaItems(fromIndex: Int, toIndex: Int) =
                    delegate.removeMediaItems(fromIndex, toIndex)
                override fun seekToNext() = delegate.seekToNext()
                override fun play() = delegate.play()
                override fun setSuppressAutoPlayback(suppress: Boolean) =
                    delegate.setSuppressAutoPlayback(suppress)
                override fun setInfiniteQueueLoading(isLoading: Boolean) =
                    delegate.setInfiniteQueueLoading(isLoading)
                override fun recordAutoAddedMediaId(mediaId: String) =
                    delegate.recordAutoAddedMediaId(mediaId)
                override fun clearAutoAddedMediaIds() = delegate.clearAutoAddedMediaIds()
                override fun getTogetherSessionState(): Any? = delegate.getTogetherSessionState()
                override fun isTogetherApplyingRemote(): Boolean = delegate.isTogetherApplyingRemote()
                override fun showTogetherNotice(message: String, key: String) =
                    delegate.showTogetherNotice(message, key)
                override fun getString(resId: Int): String = delegate.getString(resId)
                override fun showToast(resId: Int) = delegate.showToast(resId)
            },
        )

    private val libraryToggles =
        QueueLibraryToggles(
            scope = scope,
            ioScope = ioScope,
            database = database,
            syncUtils = syncUtils,
            dataStore = dataStore,
            ioDispatcher = ioDispatcher,
            delegate = object : QueueLibraryToggles.Delegate {
                override fun ensureScopesActive() = delegate.ensureScopesActive()
                override suspend fun resolveVoiceMediaItems(query: String): List<MediaItem> =
                    delegate.resolveVoiceMediaItems(query)
                override fun playQueue(queue: Queue) = this@PlaybackQueueManager.playQueue(queue)
                override fun getCurrentSong(): Song? = delegate.getCurrentSong()
                override fun getCurrentMediaMetadata(): MediaMetadata? = delegate.getCurrentMediaMetadata()
                override fun getCurrentMetadata(): MediaMetadata? = delegate.getCurrentMetadata()
                override fun getActiveQueue(): Queue = this@PlaybackQueueManager.getActiveQueue()
                override fun downloadSong(song: SongEntity) = delegate.downloadSong(song)
            },
        )

    fun getCurrentQueue(): Queue = getActiveQueue()
    fun getQueueTitle(): String? = queueTitle

    private fun getActiveQueue(): Queue {
        if (queueHolder.currentQueue !== currentQueue && queueHolder.currentQueue != EmptyQueue) {
            currentQueue = queueHolder.currentQueue
            queueTitle = queueHolder.queueTitle
        }
        return currentQueue
    }

    private fun setInitializingQueue(initializing: Boolean) {
        isInitializingQueue = initializing
        delegate.setInitializingQueue(initializing)
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        when (
            val decision =
                gatePlayQueue(
                    role = joined?.role,
                    allowGuestsToControlPlayback = joined?.roomState?.settings?.allowGuestsToControlPlayback ?: false,
                    isApplyingRemote = delegate.isTogetherApplyingRemote(),
                )
        ) {
            is PlayQueueGateDecision.Denied -> {
                delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = decision.noticeKey)
                return
            }

            PlayQueueGateDecision.HandleAsGuest -> {
                if (joined == null) return
                delegate.ensureScopesActive()
                scope.launch(SilentHandler) {
                    val initialStatus =
                        withContext(ioDispatcher) {
                            queue
                                .getInitialStatus()
                                .filterExplicit(dataStore.get(HideExplicitKey, false))
                                .filterVideo(dataStore.get(HideVideoKey, false))
                        }

                    val targetItem =
                        initialStatus.items.getOrNull(initialStatus.mediaItemIndex)
                            ?: queue.preloadItem?.toMediaItem()

                    when (
                        val plan =
                            planGuestPlayQueue(
                                roomState = joined.roomState,
                                targetItem = targetItem,
                                positionMs = initialStatus.position,
                                playWhenReady = playWhenReady,
                            )
                    ) {
                        is GuestPlayQueuePlan.Blocked -> {
                            delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = plan.noticeKey)
                        }

                        is GuestPlayQueuePlan.Execute -> {
                            delegate.showTogetherNotice(
                                delegate.getString(R.string.together_requesting_song_change),
                                key = plan.noticeKey,
                            )
                            plan.ops.forEach { op ->
                                when (op) {
                                    is TogetherGuestOp.Control -> delegate.requestTogetherControl(op.action)
                                    is TogetherGuestOp.AddTrack -> delegate.requestTogetherAddTrack(op.track, op.mode)
                                }
                            }
                        }
                    }
                }
                return
            }

            PlayQueueGateDecision.PassThrough -> Unit
        }
        if (playWhenReady) {
            delegate.cancelIdleStop()
            delegate.promoteToStartedService()
            delegate.ensureStartedAsForeground()
        }
        delegate.cancelRestoredQueueHydration()
        delegate.ensureScopesActive()
        delegate.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        cancelInfiniteQueueBootstrap()
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

        clearAutomix()
        if (queue.preloadItem != null) {
            delegate.setMediaItem(queue.preloadItem!!.toMediaItem())
            delegate.prepare()
            delegate.setPlayWhenReady(playWhenReady)
        }
        playQueueJob = scope.launch(SilentHandler) {
            try {
                val hideExplicit = dataStore.get(HideExplicitKey, false)
                val hideVideo = dataStore.get(HideVideoKey, false)
                val initialStatus =
                    withContext(ioDispatcher) {
                        queue
                            .getInitialStatus()
                            .filterExplicit(hideExplicit)
                            .filterVideo(hideVideo)
                    }
                if (!isActive) return@launch
                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                    queueHolder.queueTitle = initialStatus.title
                }
                if (initialStatus.items.isEmpty()) return@launch
                if (queue.preloadItem != null) {
                    val before = initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                    val after = initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size,
                    )
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
                    onInfiniteQueueEnabled()
                }
            }
        }
    }

    fun applyCurrentFirstShuffleOrder() {
        applyCurrentFirstShuffleOrder(
            itemCount = delegate.getMediaItemCount(),
            currentIndex = delegate.getCurrentMediaItemIndex(),
        )?.let(delegate::setShuffleOrder)
    }

    fun buildPlayNextShuffleOrder(
        currentIndex: Int,
        insertionIndex: Int,
        insertionCount: Int,
    ): DefaultShuffleOrder? =
        buildPlayNextShuffleOrder(
            timeline = delegate.getCurrentTimeline(),
            mediaItemCount = delegate.getMediaItemCount(),
            currentIndex = currentIndex,
            insertionIndex = insertionIndex,
            insertionCount = insertionCount,
        )

    fun startRadioSeamlessly() {
        automixController.startRadioSeamlessly()
    }

    fun clearAutomix() {
        automixController.clearAutomix()
    }

    fun clearQueue() {
        cancelInfiniteQueueBootstrap()
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
        currentQueue = EmptyQueue
        queueTitle = null
        queueHolder.currentQueue = EmptyQueue
        queueHolder.queueTitle = null
    }

    fun onInfiniteQueueDisabled() {
        automixController.onInfiniteQueueDisabled()
    }

    fun onInfiniteQueueEnabled() {
        automixController.onInfiniteQueueEnabled()
    }

    fun cancelInfiniteQueueBootstrap() {
        automixController.cancelInfiniteQueueBootstrap()
    }

    fun stopAndClearPlayback(clearPersistentState: Boolean = false) {
        delegate.cancelRestoredQueueHydration()
        cancelInfiniteQueueBootstrap()
        delegate.setSuppressAutoPlayback(true)
        delegate.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        clearAutomix()
        currentQueue = EmptyQueue
        queueTitle = null
        queueHolder.currentQueue = EmptyQueue
        queueHolder.queueTitle = null
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
            persistenceStore.clearPersistedQueueFiles()
        }
    }

    fun playNext(items: List<MediaItem>) {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        when (
            val decision =
                gateAddTracks(
                    role = joined?.role,
                    allowGuestAdd = joined?.roomState?.settings?.allowGuestsToAddTracks ?: false,
                    items = items,
                )
        ) {
            is GuestAddTracksGateDecision.Allowed -> {
                decision.tracks.asReversed().forEach { track ->
                    delegate.requestTogetherAddTrack(track, AddTrackMode.PLAY_NEXT)
                }
                return
            }

            GuestAddTracksGateDecision.Denied -> return
            GuestAddTracksGateDecision.PassThrough -> Unit
        }
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

    fun addToQueue(items: List<MediaItem>) {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        when (
            val decision =
                gateAddTracks(
                    role = joined?.role,
                    allowGuestAdd = joined?.roomState?.settings?.allowGuestsToAddTracks ?: false,
                    items = items,
                )
        ) {
            is GuestAddTracksGateDecision.Allowed -> {
                decision.tracks.forEach { track ->
                    delegate.requestTogetherAddTrack(track, AddTrackMode.ADD_TO_QUEUE)
                }
                return
            }

            GuestAddTracksGateDecision.Denied -> return
            GuestAddTracksGateDecision.PassThrough -> Unit
        }
        performAddToQueueHostInsert(
            items = items,
            onSetSuppressAutoPlayback = delegate::setSuppressAutoPlayback,
            onAddMediaItems = delegate::addMediaItems,
            onPrepare = delegate::prepare,
        )
    }

    fun playFromVoiceSearch(query: String) {
        libraryToggles.playFromVoiceSearch(query)
    }

    fun toggleLibrary() {
        libraryToggles.toggleLibrary()
    }

    fun toggleLike() {
        libraryToggles.toggleLike()
    }

    fun toggleStartRadio() {
        automixController.toggleStartRadio()
    }
}
