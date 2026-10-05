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
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queue.QueueAutomixController
import moe.rukamori.archivetune.playback.queue.QueueLibraryToggles
import moe.rukamori.archivetune.playback.queue.ReadOnlyTimelineDelegate
import moe.rukamori.archivetune.playback.queue.buildPlayNextShuffleOrder
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.SyncUtils

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

    interface Delegate : ReadOnlyTimelineDelegate, TogetherGuestCoordinator.Delegate {
        fun getMediaItemAt(index: Int): MediaItem; fun getPlaybackState(): Int; fun getCurrentMetadata(): MediaMetadata?
        fun isShuffleModeEnabled(): Boolean; fun setShuffleModeEnabled(enabled: Boolean); fun setShuffleOrder(shuffleOrder: ShuffleOrder)
        fun setMediaItem(item: MediaItem); fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long)
        fun addMediaItems(items: List<MediaItem>); fun addMediaItems(index: Int, items: List<MediaItem>)
        fun removeMediaItem(index: Int); fun removeMediaItems(fromIndex: Int, toIndex: Int); fun clearMediaItems()
        fun prepare(); fun play(); fun stop(); fun seekToNext()
        fun setPlayWhenReady(playWhenReady: Boolean); fun isPlayWhenReady(): Boolean
        fun cancelIdleStop(); fun promoteToStartedService(); fun ensureStartedAsForeground()
        fun cancelRestoredQueueHydration(); fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean)
        fun setSuppressAutoPlayback(suppress: Boolean); fun setInitializingQueue(isInitializing: Boolean); fun setInfiniteQueueLoading(isLoading: Boolean)
        fun cancelNetworkStallRecovery(); fun setWaitingForNetworkConnection(waiting: Boolean); fun clearCurrentMediaMetadata()
        fun abandonAudioFocus(); fun closeAudioEffectSession(); fun resetConsecutivePlaybackErr()
        fun recordAutoAddedMediaId(mediaId: String); fun clearAutoAddedMediaIds()
        fun showToast(resId: Int)
        fun isCurrentSongLocal(): Boolean; fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean
        fun getCurrentSong(): Song?; fun getCurrentMediaMetadata(): MediaMetadata?; fun downloadSong(song: SongEntity)
        suspend fun resolveVoiceMediaItems(query: String): List<MediaItem>
    }

    private val togetherGuestCoordinator =
        TogetherGuestCoordinator(scope, dataStore, delegate, ioDispatcher)

    private val queueDelegate = object : QueuePlayOrchestrator.Delegate,
        QueueMutationCommands.Delegate,
        QueueAutomixController.Delegate,
        QueueLibraryToggles.Delegate {
        override var currentQueue: Queue
            get() = getActiveQueue()
            set(value) { playOrchestrator.currentQueue = value; queueHolder.currentQueue = value }
        override var queueTitle: String?
            get() = playOrchestrator.queueTitle
            set(value) { playOrchestrator.queueTitle = value; queueHolder.queueTitle = value }
        override fun getActiveQueue(): Queue = this@PlaybackQueueManager.getActiveQueue()
        override fun getCurrentTimeline(): Timeline = delegate.getCurrentTimeline()
        override fun getMediaItemCount(): Int = delegate.getMediaItemCount()
        override fun getCurrentMediaItemIndex(): Int = delegate.getCurrentMediaItemIndex()
        override fun getMediaItemAt(index: Int): MediaItem = delegate.getMediaItemAt(index)
        override fun getCurrentMetadata(): MediaMetadata? = delegate.getCurrentMetadata()
        override fun isCurrentSongLocal(): Boolean = delegate.isCurrentSongLocal()
        override fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean = delegate.isCurrentPlaybackItemLocal(metadata)
        override fun getPlaybackState(): Int = delegate.getPlaybackState()
        override fun isShuffleModeEnabled(): Boolean = delegate.isShuffleModeEnabled()
        override fun setShuffleModeEnabled(enabled: Boolean) = delegate.setShuffleModeEnabled(enabled)
        override fun setShuffleOrder(shuffleOrder: ShuffleOrder) = delegate.setShuffleOrder(shuffleOrder)
        override fun addMediaItems(items: List<MediaItem>) = delegate.addMediaItems(items)
        override fun addMediaItems(index: Int, items: List<MediaItem>) = delegate.addMediaItems(index, items)
        override fun removeMediaItem(index: Int) = delegate.removeMediaItem(index)
        override fun removeMediaItems(fromIndex: Int, toIndex: Int) = delegate.removeMediaItems(fromIndex, toIndex)
        override fun prepare() = delegate.prepare(); override fun play() = delegate.play(); override fun stop() = delegate.stop(); override fun seekToNext() = delegate.seekToNext()
        override fun clearMediaItems() = delegate.clearMediaItems()
        override fun setPlayWhenReady(playWhenReady: Boolean) = delegate.setPlayWhenReady(playWhenReady)
        override fun cancelRestoredQueueHydration() = delegate.cancelRestoredQueueHydration()
        override fun cancelInfiniteQueueBootstrap() = this@PlaybackQueueManager.cancelInfiniteQueueBootstrap()
        override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
            delegate.cancelCrossfade(resetVolume, resetPauseAtEnd)
        override fun setSuppressAutoPlayback(suppress: Boolean) = delegate.setSuppressAutoPlayback(suppress)
        override fun setInfiniteQueueLoading(isLoading: Boolean) = delegate.setInfiniteQueueLoading(isLoading)
        override fun recordAutoAddedMediaId(mediaId: String) = delegate.recordAutoAddedMediaId(mediaId)
        override fun clearAutoAddedMediaIds() = delegate.clearAutoAddedMediaIds()
        override fun getTogetherSessionState(): Any? = delegate.getTogetherSessionState()
        override fun isTogetherApplyingRemote(): Boolean = delegate.isTogetherApplyingRemote()
        override fun showTogetherNotice(message: String, key: String) = delegate.showTogetherNotice(message, key)
        override fun getString(resId: Int): String = delegate.getString(resId)
        override fun showToast(resId: Int) = delegate.showToast(resId)
        override fun cancelNetworkStallRecovery() = delegate.cancelNetworkStallRecovery()
        override fun setWaitingForNetworkConnection(waiting: Boolean) = delegate.setWaitingForNetworkConnection(waiting)
        override fun clearCurrentMediaMetadata() = delegate.clearCurrentMediaMetadata()
        override fun abandonAudioFocus() = delegate.abandonAudioFocus()
        override fun closeAudioEffectSession() = delegate.closeAudioEffectSession()
        override fun resetConsecutivePlaybackErr() = delegate.resetConsecutivePlaybackErr()
        override fun clearAutomix() = this@PlaybackQueueManager.clearAutomix()
        override fun clearPersistedQueueFiles() = persistenceStore.clearPersistedQueueFiles()
        override fun resetQueueHolders() = playOrchestrator.resetQueueHolders()
        override fun gatePlayQueue(queue: Queue, playWhenReady: Boolean): Boolean =
            togetherGuestCoordinator.gatePlayQueue(queue, playWhenReady)
        override fun cancelIdleStop() = delegate.cancelIdleStop(); override fun promoteToStartedService() = delegate.promoteToStartedService()
        override fun ensureStartedAsForeground() = delegate.ensureStartedAsForeground(); override fun ensureScopesActive() = delegate.ensureScopesActive()
        override fun setInitializingQueue(isInitializing: Boolean) = delegate.setInitializingQueue(isInitializing)
        override fun setMediaItem(item: MediaItem) = delegate.setMediaItem(item)
        override fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long) =
            delegate.setMediaItems(items, index, positionMs)
        override fun onInfiniteQueueEnabled() = this@PlaybackQueueManager.onInfiniteQueueEnabled()
        override fun playQueue(queue: Queue) = this@PlaybackQueueManager.playQueue(queue)
        override fun getCurrentSong() = delegate.getCurrentSong()
        override fun getCurrentMediaMetadata() = delegate.getCurrentMediaMetadata()
        override fun downloadSong(song: SongEntity) = delegate.downloadSong(song)
        override suspend fun resolveVoiceMediaItems(query: String) = delegate.resolveVoiceMediaItems(query)
    }

    private val automixController: QueueAutomixController =
        QueueAutomixController(scope, dataStore, queueDelegate, ioDispatcher)

    private val libraryToggles: QueueLibraryToggles = QueueLibraryToggles(
        scope, ioScope, database, syncUtils, dataStore, queueDelegate, ioDispatcher
    )

    private val queueMutationCommands: QueueMutationCommands =
        QueueMutationCommands(delegate = queueDelegate)

    private val playOrchestrator: QueuePlayOrchestrator = QueuePlayOrchestrator(
        queueHolder, scope, dataStore, queueDelegate, ioDispatcher
    )

    fun getCurrentQueue(): Queue = playOrchestrator.getCurrentQueue()
    fun getQueueTitle(): String? = playOrchestrator.getQueueTitle()

    internal fun getActiveQueue(): Queue = playOrchestrator.getActiveQueue()

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) =
        playOrchestrator.playQueue(queue, playWhenReady)

    fun applyCurrentFirstShuffleOrder() = playOrchestrator.applyCurrentFirstShuffleOrder()

    fun buildPlayNextShuffleOrder(
        currentIndex: Int, insertionIndex: Int, insertionCount: Int,
    ): DefaultShuffleOrder? = buildPlayNextShuffleOrder(
        delegate.getCurrentTimeline(), delegate.getMediaItemCount(), currentIndex, insertionIndex, insertionCount
    )

    fun startRadioSeamlessly() = automixController.startRadioSeamlessly()
    fun clearAutomix() = automixController.clearAutomix()
    fun clearQueue() = queueMutationCommands.clearQueue()
    fun onInfiniteQueueDisabled() = automixController.onInfiniteQueueDisabled()
    fun onInfiniteQueueEnabled() = automixController.onInfiniteQueueEnabled()
    fun cancelInfiniteQueueBootstrap() = automixController.cancelInfiniteQueueBootstrap()
    fun stopAndClearPlayback(clearPersistentState: Boolean = false) =
        queueMutationCommands.stopAndClearPlayback(clearPersistentState)

    fun playNext(items: List<MediaItem>) {
        if (!togetherGuestCoordinator.gatePlayNext(items)) queueMutationCommands.performPlayNextHostInsert(items)
    }

    fun addToQueue(items: List<MediaItem>) {
        if (!togetherGuestCoordinator.gateAddToQueue(items)) queueMutationCommands.performAddToQueueHostInsert(items)
    }

    fun playFromVoiceSearch(query: String) = libraryToggles.playFromVoiceSearch(query)
    fun toggleLibrary() = libraryToggles.toggleLibrary()
    fun toggleLike() = libraryToggles.toggleLike()
    fun toggleStartRadio() = automixController.toggleStartRadio()
}
