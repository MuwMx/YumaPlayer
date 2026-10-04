/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoDownloadOnLikeKey
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
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.EmptyQueue
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherGuestOp
import moe.rukamori.archivetune.together.TogetherGuestPlaybackPlanner
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.util.ArrayDeque
import java.util.Collections

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

    interface Delegate {
        fun getMediaItemCount(): Int
        fun getCurrentMediaItemIndex(): Int
        fun getMediaItemAt(index: Int): MediaItem
        fun getCurrentTimeline(): Timeline
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
    private var infiniteQueueJob: Job? = null
    @Volatile
    private var isInitializingQueue = false
    @Volatile
    private var isInfiniteQueueLoading = false
    private val autoAddedMediaIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val toggleLikeMutex = Mutex()

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

    private fun setInfiniteQueueLoading(loading: Boolean) {
        isInfiniteQueueLoading = loading
        delegate.setInfiniteQueueLoading(loading)
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        if (!delegate.isTogetherApplyingRemote() && joined?.role is TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_DISABLED")
                return
            }
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

                val meta = targetItem?.metadata
                val trackId =
                    meta?.id?.trim().orEmpty().ifBlank {
                        targetItem?.mediaId?.trim().orEmpty()
                    }
                if (trackId.isBlank()) {
                    delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_NO_TRACK")
                    return@launch
                }

                val track =
                    TogetherTrack(
                        id = trackId,
                        title = meta?.title ?: trackId,
                        artists = meta?.artists?.map { it.name }.orEmpty(),
                        durationSec = meta?.duration ?: -1,
                        thumbnailUrl = meta?.thumbnailUrl,
                    )

                val ops =
                    TogetherGuestPlaybackPlanner.planPlayTrackNow(
                        roomState = joined.roomState,
                        track = track,
                        positionMs = initialStatus.position,
                        playWhenReady = playWhenReady,
                    )

                if (ops.isEmpty()) {
                    delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = "GUEST_PLAYQUEUE_BLOCKED")
                    return@launch
                }

                delegate.showTogetherNotice(delegate.getString(R.string.together_requesting_song_change), key = "GUEST_PLAYQUEUE_REQUEST")
                ops.forEach { op ->
                    when (op) {
                        is TogetherGuestOp.Control -> delegate.requestTogetherControl(op.action)
                        is TogetherGuestOp.AddTrack -> delegate.requestTogetherAddTrack(op.track, op.mode)
                    }
                }
            }
            return
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
        autoAddedMediaIds.clear()
        delegate.clearAutoAddedMediaIds()
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
        val count = delegate.getMediaItemCount()
        if (count <= 1) return
        val currentIndex = delegate.getCurrentMediaItemIndex().coerceIn(0, count - 1)
        val shuffledIndices = IntArray(count) { it }
        shuffledIndices.shuffle()
        val currentPos = shuffledIndices.indexOf(currentIndex)
        if (currentPos >= 0) {
            shuffledIndices[currentPos] = shuffledIndices[0]
        }
        shuffledIndices[0] = currentIndex
        delegate.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
    }

    fun buildPlayNextShuffleOrder(
        currentIndex: Int,
        insertionIndex: Int,
        insertionCount: Int,
    ): DefaultShuffleOrder? {
        val timeline = delegate.getCurrentTimeline()
        if (insertionCount <= 0 || timeline.isEmpty) return null

        fun adjustedIndex(index: Int): Int =
            if (index >= insertionIndex) {
                index + insertionCount
            } else {
                index
            }

        val previousIndices = ArrayDeque<Int>()
        var traversalIndex = currentIndex
        while (true) {
            traversalIndex = timeline.getPreviousWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
            if (traversalIndex == C.INDEX_UNSET) {
                break
            }
            previousIndices.addFirst(adjustedIndex(traversalIndex))
        }

        val nextIndices = mutableListOf<Int>()
        traversalIndex = currentIndex
        while (true) {
            traversalIndex = timeline.getNextWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
            if (traversalIndex == C.INDEX_UNSET) {
                break
            }
            nextIndices += adjustedIndex(traversalIndex)
        }

        val shuffledIndices =
            buildList(delegate.getMediaItemCount() + insertionCount) {
                addAll(previousIndices)
                add(currentIndex)
                repeat(insertionCount) { offset ->
                    add(insertionIndex + offset)
                }
                addAll(nextIndices)
            }.toIntArray()

        return DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis())
    }

    fun startRadioSeamlessly() {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        if (!delegate.isTogetherApplyingRemote() && joined?.role is TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = "GUEST_RADIO_DISABLED")
                return
            }
            delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = "GUEST_RADIO_UNSUPPORTED")
            return
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
            val initialStatus =
                withContext(ioDispatcher) {
                    radioQueue
                        .getInitialStatus()
                        .filterExplicit(
                            dataStore.get(HideExplicitKey, false),
                        ).filterVideo(dataStore.get(HideVideoKey, false))
                }

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
                queueHolder.queueTitle = initialStatus.title
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

            currentQueue = radioQueue
            queueHolder.currentQueue = radioQueue
        }
    }

    fun clearAutomix() {
        autoAddedMediaIds.clear()
        delegate.clearAutoAddedMediaIds()
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
        currentQueue = EmptyQueue
        queueHolder.currentQueue = EmptyQueue
    }

    fun onInfiniteQueueEnabled() {
        if (infiniteQueueJob?.isActive == true) return
        if (getActiveQueue() is SpotifyTracksQueue) return
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

                    currentQueue = radioQueue
                    queueHolder.currentQueue = radioQueue

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
        if (joined?.role is TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.asReversed().forEach { track ->
                delegate.requestTogetherAddTrack(track, AddTrackMode.PLAY_NEXT)
            }
            return
        }
        delegate.setSuppressAutoPlayback(false)
        val itemCount = delegate.getMediaItemCount()
        val insertionIndex = if (itemCount == 0) 0 else delegate.getCurrentMediaItemIndex() + 1
        val playNextShuffleOrder =
            if (delegate.isShuffleModeEnabled() && itemCount > 0) {
                buildPlayNextShuffleOrder(
                    currentIndex = delegate.getCurrentMediaItemIndex(),
                    insertionIndex = insertionIndex,
                    insertionCount = items.size,
                )
            } else {
                null
            }

        delegate.addMediaItems(insertionIndex, items)
        playNextShuffleOrder?.let(delegate::setShuffleOrder)
        delegate.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToAddTracks) {
                return
            }
            val tracks =
                items.mapNotNull { it.metadata }.map { meta ->
                    TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }
            tracks.forEach { track ->
                delegate.requestTogetherAddTrack(track, AddTrackMode.ADD_TO_QUEUE)
            }
            return
        }
        delegate.setSuppressAutoPlayback(false)
        delegate.addMediaItems(items)
        delegate.prepare()
    }

    fun playFromVoiceSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        delegate.ensureScopesActive()
        scope.launch(SilentHandler) {
            val mediaItems =
                withContext(ioDispatcher) {
                    delegate.resolveVoiceMediaItems(trimmed)
                }
            if (mediaItems.isEmpty()) return@launch
            playQueue(ListQueue(items = mediaItems))
        }
    }

    fun toggleLibrary() {
        database.query {
            delegate.getCurrentSong()?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        val mediaMetadata = delegate.getCurrentMediaMetadata() ?: delegate.getCurrentMetadata() ?: return
        Timber.tag("MediaNotification").d("toggleLike() called for mediaId=${mediaMetadata.id}, title=${mediaMetadata.title}")
        val currentSongSong = delegate.getCurrentSong()?.song
        val source = LikeSourceResolver.resolve(
            mediaId = mediaMetadata.id,
            spotifyTrackId = mediaMetadata.spotifyTrackId,
            queue = getActiveQueue(),
            isLocal = currentSongSong?.isLocal ?: mediaMetadata.id.isLocalMediaId(),
        )
        ioScope.launch {
            try {
                val song =
                    toggleLikeMutex.withLock {
                        database.withTransaction {
                            val currentSongEntity =
                                getSongById(mediaMetadata.id)
                                    ?: run {
                                        insert(mediaMetadata) {
                                            it.copy(isLocal = mediaMetadata.id.isLocalMediaId())
                                        }
                                        getSongById(mediaMetadata.id)
                                    }
                                    ?: return@withTransaction null
                            currentSongEntity.song.localToggleLike(source).also(::update)
                        }
                    } ?: return@launch

                Timber.tag("MediaNotification").d("toggleLike() successful: song=${song.id}, liked=${song.liked}")
                val spotifyId = if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
                    mediaMetadata.spotifyTrackId
                } else if (LikeSourceResolver.isSpotifyId(song.id, song.isLocal)) {
                    song.id
                } else {
                    null
                }
                syncUtils.likeSong(song, source, spotifyId)

                if (!song.isLocal && dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                    delegate.downloadSong(song)
                }
            } catch (e: Exception) {
                Timber.tag("MediaNotification").e(e, "toggleLike() failed for mediaId=${mediaMetadata.id}")
                reportException(e)
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }
}
