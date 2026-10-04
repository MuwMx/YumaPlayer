/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_AUDIO_SESSION_ID
import androidx.media3.common.Player.EVENT_DEVICE_VOLUME_CHANGED
import androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED
import androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED
import androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.RepeatModeKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.playback.queues.filterExplicit
import moe.rukamori.archivetune.playback.queues.filterVideo
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherRoomState
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

@OptIn(UnstableApi::class)
internal class MusicServicePlayerListeners(
    private val scope: CoroutineScope,
    private val playerDelegate: PlayerDelegate,
    private val queueDelegate: QueueDelegate,
    private val historyDelegate: HistoryDelegate,
    private val widgetDelegate: WidgetDelegate,
    private val metadataDelegate: MetadataDelegate,
    private val notificationDelegate: NotificationDelegate,
    private val crossfadeHooks: CrossfadeHooks,
) : Player.Listener, PlaybackStatsListener.Callback {

    interface PlayerDelegate {
        val player: Player
        val localPlayer: ExoPlayer
    }

    interface QueueDelegate {
        var currentQueue: Queue
        val suppressAutoPlayback: Boolean
        val isInitializingQueue: Boolean
        val autoAddedMediaIds: MutableSet<String>
        val dataStore: DataStore<Preferences>
        fun onInfiniteQueueEnabled()
        suspend fun saveQueueToDisk()
        fun applyCurrentFirstShuffleOrder()

        val togetherSessionState: StateFlow<TogetherSessionState>
        val togetherSuppressEchoUntilElapsedMs: Long
        val togetherLastRemoteAppliedIndex: Int
        val togetherLastRemoteAppliedPlayWhenReady: Boolean?
        fun isTogetherApplyingRemote(): Boolean
        suspend fun applyRemoteRoomState(roomState: TogetherRoomState, force: Boolean = false)
        fun requestTogetherControl(action: ControlAction)
    }

    interface HistoryDelegate {
        var historyThresholdJob: Job?
        val currentHistoryMediaId: String?
        fun beginHistorySession(mediaId: String?, forceNew: Boolean = false)
        fun updateHistoryTrackingPlaybackState()
        fun enqueueCurrentHistorySessionForFinalization()
        fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats)
    }

    interface WidgetDelegate {
        fun update()
        fun updateProgressTracking()
    }

    interface MetadataDelegate {
        fun updateCurrentMediaMetadata(metadata: MediaMetadata?)
        fun onLyricsSongChanged(currentIndex: Int, queue: List<MediaMetadata>)
        fun prefetchAround(index: Int)
        fun cancelPrefetch()
        fun kickOffUpcomingTrackAnalysis(index: Int)
        fun kickOffTrackAnalysis(mediaItem: MediaItem?)
        fun registerCacheListenersForMediaItem(mediaItem: MediaItem?)
        fun recheckCacheReadinessForCurrentAndNext()
        fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean
        fun onScrobbleSongStart(metadata: MediaMetadata?, durationMs: Long)
        fun onScrobbleSongStop()
        fun onScrobblePlayerStateChanged(isPlaying: Boolean, metadata: MediaMetadata?, durationMs: Long)
        fun ensurePresenceManager()
        fun requestDiscordSync(reason: String, force: Boolean = false)
        suspend fun submitPlayingNow(
            mediaId: String?,
            mediaMetadata: MediaMetadata?,
            durationMs: Long,
            positionMs: Long,
            reason: String,
        )
    }

    interface NotificationDelegate {
        fun updateNotification()
        fun shouldKeepAudioEffectSessionOpen(): Boolean
        fun ensureAudioFocusForActivePlayback(): Boolean
        fun updateWakeLock()
        fun hasResumablePlaybackNotification(): Boolean
        fun cancelIdleStop()
        fun promoteToStartedService()
        fun ensureStartedAsForeground()
        fun scheduleStopIfIdle()
        fun handleDeviceMuteStateChanged(playbackRequestedWhileMuted: Boolean = false)
        fun isDeviceMutedNow(): Boolean
        fun unregisterMuteRecoveryObserver()
        var wasAutoPausedByDeviceMute: Boolean
        fun updateAudiblePlaybackRecovery()
        fun ensureAudiblePlaybackVolume(reason: String)
        fun onPlaybackStreamMediaItemChanged(mediaId: String?)
        fun onPlaybackStreamRecovered(mediaId: String?)
        fun reconcileAudioEffectSession()
        fun onPlayerError(error: PlaybackException) {}
    }

    interface CrossfadeHooks {
        val secondaryCrossfadePlayer: ExoPlayer?
        var secondaryPreparationFailedMediaId: String?
        var hasPreparedSecondaryPlayer: Boolean
        var isCrossfading: Boolean
        val crossfadeHandoffInProgress: Boolean
        var crossfadePlaybackRequested: Boolean
        var crossfadeTriggerJob: Job?
        var activeCrossfadeScheduledKey: Pair<String, CrossfadeTarget>?
        fun scheduleCrossfade()
        fun cancelCrossfade(resetVolume: Boolean = true, resetPauseAtEnd: Boolean = true)
        fun cancelSecondaryCrossfadePreparation()
        fun releaseSecondaryCrossfadePlayer()
    }

    private val player: Player get() = playerDelegate.player
    private val localPlayer: ExoPlayer get() = playerDelegate.localPlayer
    private val dataStore: DataStore<Preferences> get() = queueDelegate.dataStore

    var historyThresholdJob: Job?
        get() = historyDelegate.historyThresholdJob
        set(value) {
            historyDelegate.historyThresholdJob = value
        }

    val audioEffectPlayerListener: Player.Listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        EVENT_AUDIO_SESSION_ID,
                        EVENT_PLAYBACK_STATE_CHANGED,
                        EVENT_IS_PLAYING_CHANGED,
                    )
                ) {
                    notificationDelegate.reconcileAudioEffectSession()
                }
            }
        }

    val secondaryCrossfadeListener: Player.Listener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Timber.tag(TAG).w(error, "Secondary crossfade player failed")
                val isEofOrSource = isSourceOrEofError(error)
                scope.launch {
                    if (isEofOrSource) {
                        crossfadeHooks.cancelSecondaryCrossfadePreparation()
                    } else {
                        crossfadeHooks.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        crossfadeHooks.scheduleCrossfade()
                    }
                }
            }
        }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        super.onMediaItemTransition(mediaItem, reason)

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
            val now = android.os.SystemClock.elapsedRealtime()
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            val isEcho =
                queueDelegate.isTogetherApplyingRemote() ||
                    (now < queueDelegate.togetherSuppressEchoUntilElapsedMs && queueDelegate.togetherLastRemoteAppliedIndex == index)
            if (!isEcho) {
                val trackId = (mediaItem?.metadata ?: player.currentMetadata)?.id?.trim().orEmpty()
                queueDelegate.requestTogetherControl(
                    if (trackId.isBlank()) {
                        ControlAction.SeekToIndex(
                            index = index,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    } else {
                        ControlAction.SeekToTrack(
                            trackId = trackId,
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    },
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
        if (!queueDelegate.suppressAutoPlayback &&
            !queueDelegate.isInitializingQueue &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF
        ) {
            if (remainingTracks <= 5 && queueDelegate.currentQueue.hasNextPage()) {
                scope.launch(SilentHandler) {
                    val nextBatch =
                        queueDelegate.currentQueue
                            .nextPage()
                            .filterExplicit(
                                dataStore.get(HideExplicitKey, false),
                            ).filterVideo(dataStore.get(HideVideoKey, false))
                    if (player.playbackState != STATE_IDLE) {
                        player.addMediaItems(nextBatch)
                    } else {
                        metadataDelegate.requestDiscordSync(
                            reason = "player_idle_after_queue_extension",
                            force = true,
                        )
                    }
                }
            }
        }

        if (!queueDelegate.suppressAutoPlayback &&
            !queueDelegate.isInitializingQueue &&
            !timelineEmpty &&
            dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.repeatMode == REPEAT_MODE_OFF &&
            player.mediaItemCount - player.currentMediaItemIndex <= 3 &&
            !queueDelegate.currentQueue.hasNextPage()
        ) {
            scope.launch(SilentHandler) {
                if (queueDelegate.suppressAutoPlayback || player.mediaItemCount == 0) return@launch
                if (queueDelegate.currentQueue is SpotifyTracksQueue) return@launch

                val currentMediaMetadata = player.currentMetadata ?: return@launch
                val currentMediaId = currentMediaMetadata.id.trim().ifBlank { return@launch }
                if (metadataDelegate.isCurrentPlaybackItemLocal(currentMediaMetadata)) return@launch

                try {
                    val radioQueue = YouTubeQueue(WatchEndpoint(videoId = currentMediaId), followAutomixPreview = true)
                    val status = withContext(Dispatchers.IO) { radioQueue.getInitialStatus() }

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
            !queueDelegate.currentQueue.hasNextPage()
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

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        super.onPlaybackStateChanged(playbackState)

        historyDelegate.updateHistoryTrackingPlaybackState()
        if (playbackState == STATE_ENDED || playbackState == STATE_IDLE) {
            historyDelegate.enqueueCurrentHistorySessionForFinalization()
            if ((!crossfadeHooks.isCrossfading || playbackState == STATE_IDLE) && !crossfadeHooks.crossfadeHandoffInProgress) {
                crossfadeHooks.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            }
            if (playbackState == STATE_ENDED &&
                !queueDelegate.suppressAutoPlayback &&
                dataStore.get(AutoLoadMoreKey, true) &&
                player.repeatMode == REPEAT_MODE_OFF
            ) {
                queueDelegate.onInfiniteQueueEnabled()
            }
        } else if (playbackState == STATE_READY) {
            crossfadeHooks.scheduleCrossfade()
            metadataDelegate.recheckCacheReadinessForCurrentAndNext()
        }

        widgetDelegate.update()
        widgetDelegate.updateProgressTracking()

        scope.launch {
            val shouldSave = withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }
            if (shouldSave) {
                queueDelegate.saveQueueToDisk()
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        crossfadeHooks.secondaryCrossfadePlayer?.let { secondaryPlayer ->
            if (crossfadeHooks.isCrossfading && !crossfadeHooks.crossfadeHandoffInProgress) {
                val isEndOfOutgoingItemPause =
                    !playWhenReady &&
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                        localPlayer.pauseAtEndOfMediaItems
                if (!isEndOfOutgoingItemPause) {
                    crossfadeHooks.crossfadePlaybackRequested = playWhenReady
                }
                secondaryPlayer.playWhenReady = crossfadeHooks.crossfadePlaybackRequested
                if (crossfadeHooks.crossfadePlaybackRequested) {
                    secondaryPlayer.play()
                } else if (!isEndOfOutgoingItemPause) {
                    secondaryPlayer.pause()
                }
            }
        }
        if (playWhenReady && !crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        } else if (!playWhenReady && !crossfadeHooks.isCrossfading) {
            crossfadeHooks.crossfadeTriggerJob?.cancel()
            crossfadeHooks.crossfadeTriggerJob = null
            crossfadeHooks.activeCrossfadeScheduledKey = null
            crossfadeHooks.isCrossfading = false
            localPlayer.pauseAtEndOfMediaItems = false
            crossfadeHooks.releaseSecondaryCrossfadePlayer()
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        crossfadeHooks.secondaryCrossfadePlayer?.playbackParameters = playbackParameters
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        crossfadeHooks.secondaryCrossfadePlayer?.let { secondaryPlayer ->
            if (crossfadeHooks.isCrossfading && !crossfadeHooks.crossfadeHandoffInProgress) {
                if (isPlaying) {
                    secondaryPlayer.play()
                } else {
                    secondaryPlayer.pause()
                }
            }
        }
        if (isPlaying && !crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        }
        if (isPlaying) {
            metadataDelegate.prefetchAround(player.currentMediaItemIndex)
        }
        notificationDelegate.updateAudiblePlaybackRecovery()

        widgetDelegate.update()
        widgetDelegate.updateProgressTracking()
    }

    internal fun onMediaItemTransitionInternal() {
        if (player.playbackState == STATE_IDLE || player.playbackState == STATE_ENDED) {
            metadataDelegate.onScrobbleSongStop()
        }

        if (!queueDelegate.suppressAutoPlayback &&
            player.playbackState == STATE_ENDED &&
            dataStore.get(AutoLoadMoreKey, true) &&
            player.repeatMode == REPEAT_MODE_OFF &&
            player.currentMediaItem != null
        ) {
            queueDelegate.onInfiniteQueueEnabled()
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

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        val currentMediaId = player.currentMediaItem?.mediaId
        if (currentMediaId == null && historyDelegate.currentHistoryMediaId != null) {
            historyDelegate.beginHistorySession(null, forceNew = true)
        } else if (historyDelegate.currentHistoryMediaId == null && currentMediaId != null) {
            historyDelegate.beginHistorySession(currentMediaId)
        }
        if (events.contains(EVENT_MEDIA_ITEM_TRANSITION)) {
            notificationDelegate.onPlaybackStreamMediaItemChanged(currentMediaId)
        }
        if (
            (events.contains(EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == STATE_READY) ||
            (events.contains(EVENT_IS_PLAYING_CHANGED) && player.isPlaying)
        ) {
            notificationDelegate.onPlaybackStreamRecovered(currentMediaId)
            notificationDelegate.ensureAudiblePlaybackVolume("player_event")
        }
        if (events.containsAny(
                EVENT_PLAYBACK_STATE_CHANGED,
                EVENT_PLAY_WHEN_READY_CHANGED,
                EVENT_IS_PLAYING_CHANGED,
            )
        ) {
            notificationDelegate.updateAudiblePlaybackRecovery()
        }
        if (events.contains(EVENT_MEDIA_METADATA_CHANGED)) {
            metadataDelegate.updateCurrentMediaMetadata(player.currentMetadata)
        }
        if (events.containsAny(
                EVENT_PLAYBACK_STATE_CHANGED,
                EVENT_PLAY_WHEN_READY_CHANGED,
                EVENT_IS_PLAYING_CHANGED,
            )
        ) {
            historyDelegate.updateHistoryTrackingPlaybackState()
        }
        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest &&
            events.contains(EVENT_PLAY_WHEN_READY_CHANGED)
        ) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
            } else {
                val now = android.os.SystemClock.elapsedRealtime()
                val playWhenReady = this.player.playWhenReady
                val isEcho =
                    queueDelegate.isTogetherApplyingRemote() ||
                        (
                            now < queueDelegate.togetherSuppressEchoUntilElapsedMs &&
                                queueDelegate.togetherLastRemoteAppliedPlayWhenReady != null &&
                                queueDelegate.togetherLastRemoteAppliedPlayWhenReady == playWhenReady
                        )
                if (!isEcho) {
                    val action =
                        if (playWhenReady) {
                            ControlAction.Play
                        } else {
                            ControlAction.Pause
                        }
                    queueDelegate.requestTogetherControl(action)
                }
            }
        }
        if (events.contains(EVENT_DEVICE_VOLUME_CHANGED)) {
            notificationDelegate.handleDeviceMuteStateChanged()
        }
        if (events.contains(EVENT_PLAY_WHEN_READY_CHANGED) && notificationDelegate.isDeviceMutedNow() && this.player.playWhenReady) {
            notificationDelegate.handleDeviceMuteStateChanged(playbackRequestedWhileMuted = true)
        }
        if (events.contains(EVENT_PLAYBACK_STATE_CHANGED) &&
            (this.player.playbackState == STATE_IDLE || this.player.playbackState == STATE_ENDED)
        ) {
            notificationDelegate.wasAutoPausedByDeviceMute = false
            notificationDelegate.unregisterMuteRecoveryObserver()
            notificationDelegate.updateAudiblePlaybackRecovery()
        }
        if (events.contains(EVENT_PLAYBACK_STATE_CHANGED) &&
            notificationDelegate.isDeviceMutedNow() &&
            this.player.playWhenReady
        ) {
            notificationDelegate.handleDeviceMuteStateChanged(playbackRequestedWhileMuted = true)
        }
        if (events.containsAny(
                EVENT_PLAYBACK_STATE_CHANGED,
                EVENT_PLAY_WHEN_READY_CHANGED,
            )
        ) {
            if (player.playWhenReady && notificationDelegate.shouldKeepAudioEffectSessionOpen()) {
                notificationDelegate.ensureAudioFocusForActivePlayback()
            }
            notificationDelegate.updateWakeLock()
            if (notificationDelegate.hasResumablePlaybackNotification()) {
                notificationDelegate.cancelIdleStop()
                notificationDelegate.promoteToStartedService()
                notificationDelegate.ensureStartedAsForeground()
            } else {
                notificationDelegate.scheduleStopIfIdle()
            }
        }

        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            metadataDelegate.updateCurrentMediaMetadata(player.currentMetadata)
            metadataDelegate.requestDiscordSync(
                reason = "timeline_or_position_discontinuity",
                force = true,
            )
            scope.launch {
                metadataDelegate.submitPlayingNow(
                    mediaId = player.currentMediaItem?.mediaId,
                    mediaMetadata = player.currentMetadata,
                    durationMs = player.duration,
                    positionMs = player.currentPosition,
                    reason = "timeline_or_position_discontinuity",
                )
            }
        }
        if (events.contains(EVENT_TIMELINE_CHANGED) && !crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        }

        if (events.containsAny(
                EVENT_PLAYBACK_STATE_CHANGED,
                EVENT_PLAY_WHEN_READY_CHANGED,
                EVENT_IS_PLAYING_CHANGED,
                EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            if (events.contains(EVENT_MEDIA_ITEM_TRANSITION)) {
                metadataDelegate.updateCurrentMediaMetadata(player.currentMetadata)
            }
            metadataDelegate.requestDiscordSync(
                reason = "is_playing_or_media_item_transition",
                force = true,
            )
            val currentMediaId = player.currentMediaItem?.mediaId
            val currentMetadata = player.currentMetadata
            val currentPosition = player.currentPosition
            val currentDuration = player.duration

            scope.launch {
                metadataDelegate.submitPlayingNow(
                    mediaId = currentMediaId,
                    mediaMetadata = currentMetadata,
                    durationMs = currentDuration,
                    positionMs = currentPosition,
                    reason = "isPlaying/mediaTransition",
                )
            }
        }

        if (events.containsAny(EVENT_IS_PLAYING_CHANGED)) {
            metadataDelegate.onScrobblePlayerStateChanged(player.isPlaying, player.currentMetadata, durationMs = player.duration)
        }

        if (events.contains(EVENT_PLAY_WHEN_READY_CHANGED) && player.mediaItemCount > 0) {
            scope.launch(SilentHandler) {
                if (withContext(Dispatchers.IO) { dataStore.get(PersistentQueueKey, true) }) {
                    queueDelegate.saveQueueToDisk()
                }
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        val isSeekDiscontinuity =
            reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        if (isSeekDiscontinuity) {
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                metadataDelegate.cancelPrefetch()
                if (player.isPlaying) {
                    metadataDelegate.prefetchAround(newPosition.mediaItemIndex)
                }
            }
            if (!crossfadeHooks.crossfadeHandoffInProgress) {
                crossfadeHooks.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            }
        }
        if (!crossfadeHooks.isCrossfading && !crossfadeHooks.crossfadeHandoffInProgress) {
            crossfadeHooks.scheduleCrossfade()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        metadataDelegate.cancelPrefetch()
        if (player.isPlaying) {
            metadataDelegate.prefetchAround(player.currentMediaItemIndex)
        }
        notificationDelegate.updateNotification()
        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!queueDelegate.isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                queueDelegate.requestTogetherControl(
                    ControlAction.SetShuffleEnabled(
                        shuffleEnabled = shuffleModeEnabled,
                    ),
                )
            }
            return
        }
        if (shuffleModeEnabled) {
            queueDelegate.applyCurrentFirstShuffleOrder()
        }

        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                queueDelegate.saveQueueToDisk()
            }
        }
        if (!crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        metadataDelegate.cancelPrefetch()
        if (player.isPlaying) {
            metadataDelegate.prefetchAround(player.currentMediaItemIndex)
        }
        notificationDelegate.updateNotification()
        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!queueDelegate.isTogetherApplyingRemote()) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                queueDelegate.requestTogetherControl(
                    ControlAction.SetRepeatMode(
                        repeatMode = repeatMode,
                    ),
                )
            }
            return
        }
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        scope.launch {
            if (dataStore.get(PersistentQueueKey, true)) {
                queueDelegate.saveQueueToDisk()
            }
        }
        if (!crossfadeHooks.isCrossfading) {
            crossfadeHooks.scheduleCrossfade()
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        super.onTimelineChanged(timeline, reason)
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            metadataDelegate.cancelPrefetch()
            if (player.isPlaying) {
                metadataDelegate.prefetchAround(player.currentMediaItemIndex)
            }
        }
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        historyDelegate.onPlaybackStatsReady(eventTime, playbackStats)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        notificationDelegate.onPlayerError(error)
    }

    companion object {
        private const val TAG = "MusicServicePlayerListeners"
    }
}
