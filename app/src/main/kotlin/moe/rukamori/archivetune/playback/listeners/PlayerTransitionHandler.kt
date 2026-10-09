/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.listeners

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.playback.MusicServicePlayerListeners
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playback.queues.filterExplicit
import moe.rukamori.archivetune.playback.queues.filterVideo
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

@OptIn(UnstableApi::class)
internal class PlayerTransitionHandler(
    private val scope: CoroutineScope,
    private val playerDelegate: MusicServicePlayerListeners.PlayerDelegate,
    private val queueDelegate: MusicServicePlayerListeners.QueueDelegate,
    private val historyDelegate: MusicServicePlayerListeners.HistoryDelegate,
    private val widgetDelegate: MusicServicePlayerListeners.WidgetDelegate,
    private val metadataDelegate: MusicServicePlayerListeners.MetadataDelegate,
    private val notificationDelegate: MusicServicePlayerListeners.NotificationDelegate,
    private val togetherPlaybackEchoPolicy: TogetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
    private val crossfadeHooks: MusicServicePlayerListeners.CrossfadeHooks,
) {
    constructor(
        scope: CoroutineScope,
        playerDelegate: MusicServicePlayerListeners.PlayerDelegate,
        queueDelegate: MusicServicePlayerListeners.QueueDelegate,
        historyDelegate: MusicServicePlayerListeners.HistoryDelegate,
        widgetDelegate: MusicServicePlayerListeners.WidgetDelegate,
        metadataDelegate: MusicServicePlayerListeners.MetadataDelegate,
        notificationDelegate: MusicServicePlayerListeners.NotificationDelegate,
        crossfadeHooks: MusicServicePlayerListeners.CrossfadeHooks,
    ) : this(
        scope = scope,
        playerDelegate = playerDelegate,
        queueDelegate = queueDelegate,
        historyDelegate = historyDelegate,
        widgetDelegate = widgetDelegate,
        metadataDelegate = metadataDelegate,
        notificationDelegate = notificationDelegate,
        togetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
        crossfadeHooks = crossfadeHooks,
    )

    private val player: Player get() = playerDelegate.player
    private val dataStore: DataStore<Preferences> get() = queueDelegate.dataStore

    private val togetherEchoSnapshot: TogetherEchoSnapshot
        get() =
            TogetherEchoSnapshot(
                isApplyingRemote = queueDelegate.isTogetherApplyingRemote(),
                suppressEchoUntilElapsedMs = queueDelegate.togetherSuppressEchoUntilElapsedMs,
                lastRemoteAppliedIndex = queueDelegate.togetherLastRemoteAppliedIndex,
                lastRemoteAppliedPlayWhenReady = queueDelegate.togetherLastRemoteAppliedPlayWhenReady,
            )

    private var pagingJob: Job? = null
    private var pagingQueue: Queue? = null

    fun triggerPagination(isPlaybackEnded: Boolean = false) {
        val targetQueue = queueDelegate.currentQueue
        if (!targetQueue.hasNextPage() && !targetQueue.hasPendingContextItems) return
        if (queueDelegate.isInitializingQueue) return
        if (!targetQueue.isContextQueue && !dataStore.get(AutoLoadMoreKey, true)) return

        if (pagingJob?.isActive == true) {
            if (pagingQueue === targetQueue) {
                return
            } else {
                pagingJob?.cancel()
            }
        }

        val job =
            scope.launch(start = CoroutineStart.LAZY, context = SilentHandler) {
                try {
                    var loadedItems = emptyList<MediaItem>()
                    while (targetQueue.hasNextPage() && loadedItems.isEmpty()) {
                        val batch =
                            withContext(Dispatchers.IO) {
                                targetQueue
                                    .nextPage()
                                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                                    .filterVideo(dataStore.get(HideVideoKey, false))
                            }
                        if (!isActive || queueDelegate.currentQueue !== targetQueue) return@launch
                        loadedItems = batch
                    }

                    if (!isActive || queueDelegate.currentQueue !== targetQueue) return@launch

                    if (loadedItems.isNotEmpty()) {
                        if (player.playbackState != STATE_IDLE) {
                            val wasEndedImmediatelyBeforeMutation = player.playbackState == STATE_ENDED && player.playWhenReady
                            player.addMediaItems(loadedItems)
                            if (wasEndedImmediatelyBeforeMutation) {
                                player.seekToNext()
                                player.prepare()
                                player.play()
                            }
                        } else {
                            metadataDelegate.requestDiscordSync(
                                reason = "player_idle_after_queue_extension",
                                force = true,
                            )
                        }
                    } else if (isPlaybackEnded || player.playbackState == STATE_ENDED) {
                        if (dataStore.get(AutoLoadMoreKey, true) &&
                            !queueDelegate.suppressAutoPlayback &&
                            player.repeatMode == REPEAT_MODE_OFF
                        ) {
                            queueDelegate.onInfiniteQueueEnabled()
                        }
                    }
                } finally {
                    if (pagingJob === coroutineContext[Job]) {
                        pagingJob = null
                        pagingQueue = null
                    }
                }
            }
        pagingJob = job
        pagingQueue = targetQueue
        job.start()
    }

    fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        crossfadeHooks.secondaryPreparationFailedMediaId = null
        crossfadeHooks.hasPreparedSecondaryPlayer = false

        historyDelegate.beginHistorySession(mediaItem?.mediaId, forceNew = true)

        val currentIndex = player.currentMediaItemIndex
        val queue = player.mediaItems.mapNotNull { it.metadata }
        if (queue.isNotEmpty()) {
            metadataDelegate.onLyricsSongChanged(currentIndex, queue)
        }
        metadataDelegate.prefetchAround(currentIndex)
        metadataDelegate.kickOffUpcomingTrackAnalysis(currentIndex)
        metadataDelegate.kickOffTrackAnalysis(mediaItem)
        metadataDelegate.registerCacheListenersForMediaItem(mediaItem)
        val nextIdx = player.nextMediaItemIndex
        if (nextIdx != C.INDEX_UNSET && nextIdx in 0 until player.mediaItemCount) {
            metadataDelegate.registerCacheListenersForMediaItem(runCatching { player.getMediaItemAt(nextIdx) }.getOrNull())
        }

        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        ) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
                return
            }
            val now = SystemClock.elapsedRealtime()
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            val isEcho =
                togetherPlaybackEchoPolicy.isTransitionEcho(
                    snapshot = togetherEchoSnapshot,
                    currentIndex = index,
                    nowElapsedMs = now,
                )
            if (!isEcho) {
                val trackId = (mediaItem?.metadata ?: player.currentMetadata)?.id
                queueDelegate.requestTogetherControl(
                    togetherPlaybackEchoPolicy.buildSeekAction(
                        trackId = trackId,
                        index = index,
                        positionMs = player.currentPosition,
                    ),
                )
            }
        }

        val timelineEmpty = player.currentTimeline.isEmpty || player.mediaItemCount == 0 || player.currentMediaItem == null
        metadataDelegate.updateCurrentMediaMetadata(if (timelineEmpty) null else (mediaItem?.metadata ?: player.currentMetadata))
        notificationDelegate.updateNotification()

        widgetDelegate.update()

        metadataDelegate.onScrobbleSongStop()

        if (!timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF
        ) {
            // No redundant seeding update check.
        }

        val remainingTracks = player.mediaItemCount - player.currentMediaItemIndex
        val currentQ = queueDelegate.currentQueue

        val shouldPage = if (currentQ.isContextQueue) true else dataStore.get(AutoLoadMoreKey, true)

        if (!queueDelegate.suppressAutoPlayback &&
            !queueDelegate.isInitializingQueue &&
            !timelineEmpty &&
            shouldPage &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF
        ) {
            if (remainingTracks <= 5 && (currentQ.hasNextPage() || currentQ.hasPendingContextItems)) {
                triggerPagination(isPlaybackEnded = false)
            }
        }

        if (!queueDelegate.suppressAutoPlayback &&
            !queueDelegate.isInitializingQueue &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF &&
            player.mediaItemCount - player.currentMediaItemIndex <= 3 &&
            !currentQ.hasNextPage() &&
            !currentQ.isContextQueue
        ) {
            scope.launch(SilentHandler) {
                if (queueDelegate.suppressAutoPlayback || player.mediaItemCount == 0) return@launch
                if (queueDelegate.currentQueue !== currentQ) return@launch

                val currentMediaMetadata = player.currentMetadata ?: return@launch
                val currentMediaId = currentMediaMetadata.id.trim().ifBlank { return@launch }
                if (metadataDelegate.isCurrentPlaybackItemLocal(currentMediaMetadata)) return@launch

                try {
                    val radioQueue = YouTubeQueue(WatchEndpoint(videoId = currentMediaId), followAutomixPreview = true)
                    val status = withContext(Dispatchers.IO) { radioQueue.getInitialStatus() }
                    if (!isActive) return@launch
                    if (queueDelegate.currentQueue !== currentQ) return@launch

                    val queueIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                    val newItems = status.items.filter { it.mediaId !in queueIds }

                    if (newItems.isNotEmpty()) {
                        player.addMediaItems(newItems)
                        newItems.forEach { queueDelegate.autoAddedMediaIds.add(it.mediaId) }
                    }
                    queueDelegate.currentQueue = radioQueue
                } catch (e: Exception) {
                    Timber.e(e, "Failed to inject YouTube replacement queue")
                }
            }
        }

        if (!queueDelegate.suppressAutoPlayback &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF &&
            remainingTracks <= 3 &&
            !currentQ.hasNextPage() &&
            !currentQ.isContextQueue
        ) {
            queueDelegate.onInfiniteQueueEnabled()
        }

        if (player.playWhenReady && player.playbackState == STATE_READY) {
            metadataDelegate.onScrobbleSongStart(player.currentMetadata, durationMs = player.duration)
        }

        scope.launch {
            val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
            if (shouldSave) {
                queueDelegate.saveQueueToDisk()
            }
        }
        metadataDelegate.ensurePresenceManager()
        if (!crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        }
    }

    fun onMediaItemTransitionInternal() {
        if (player.playbackState == STATE_IDLE || player.playbackState == STATE_ENDED) {
            metadataDelegate.onScrobbleSongStop()
        }

        if (!queueDelegate.suppressAutoPlayback &&
            !(queueDelegate.isInitializingQueue && queueDelegate.currentQueue.isContextQueue) &&
            player.playbackState == STATE_ENDED &&
            player.repeatMode == REPEAT_MODE_OFF &&
            player.currentMediaItem != null
        ) {
            if (queueDelegate.currentQueue.hasNextPage() || queueDelegate.currentQueue.hasPendingContextItems) {
                triggerPagination(isPlaybackEnded = true)
            } else if (dataStore.get(AutoLoadMoreKey, true)) {
                queueDelegate.onInfiniteQueueEnabled()
            }
        }

        metadataDelegate.requestDiscordSync(
            reason = "media_item_transition",
            force = true,
        )
        scope.launch {
            metadataDelegate.submitPlayingNow(
                mediaId = player.currentMediaItem?.mediaId,
                mediaMetadata = player.currentMetadata,
                durationMs = player.duration,
                positionMs = player.currentPosition,
                reason = "media_item_transition",
            )
        }
    }
}
