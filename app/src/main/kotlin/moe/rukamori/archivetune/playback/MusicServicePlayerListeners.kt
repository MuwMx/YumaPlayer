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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_AUDIO_SESSION_ID
import androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED
import androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.listeners.PlayerEventFanout
import moe.rukamori.archivetune.playback.listeners.PlayerStateHandler
import moe.rukamori.archivetune.playback.listeners.PlayerTransitionHandler
import moe.rukamori.archivetune.playback.listeners.TogetherPlaybackEchoPolicy
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherRoomState
import moe.rukamori.archivetune.together.TogetherSessionState
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

    var historyThresholdJob: Job?
        get() = historyDelegate.historyThresholdJob
        set(value) {
            historyDelegate.historyThresholdJob = value
        }

    private val playerEventFanout =
        PlayerEventFanout(
            scope = scope,
            queueDelegate = queueDelegate,
            historyDelegate = historyDelegate,
            widgetDelegate = widgetDelegate,
            metadataDelegate = metadataDelegate,
            notificationDelegate = notificationDelegate,
            togetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
            crossfadeHooks = crossfadeHooks,
        )

    private val playerTransitionHandler =
        PlayerTransitionHandler(
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

    private val playerStateHandler =
        PlayerStateHandler(
            scope = scope,
            playerDelegate = playerDelegate,
            queueDelegate = queueDelegate,
            historyDelegate = historyDelegate,
            widgetDelegate = widgetDelegate,
            metadataDelegate = metadataDelegate,
            notificationDelegate = notificationDelegate,
            togetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
            crossfadeHooks = crossfadeHooks,
            transitionHandler = playerTransitionHandler,
        )

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
        playerTransitionHandler.onMediaItemTransition(mediaItem, reason)
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        super.onPlaybackStateChanged(playbackState)
        playerStateHandler.onPlaybackStateChanged(playbackState)
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        playerStateHandler.onPlayWhenReadyChanged(playWhenReady, reason)
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        playerStateHandler.onPlaybackParametersChanged(playbackParameters)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        playerStateHandler.onIsPlayingChanged(isPlaying)
    }

    internal fun onMediaItemTransitionInternal() {
        playerTransitionHandler.onMediaItemTransitionInternal()
    }

    internal fun triggerPagination(isPlaybackEnded: Boolean) {
        playerTransitionHandler.triggerPagination(isPlaybackEnded)
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        playerEventFanout.onEvents(player, events)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        playerStateHandler.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        playerStateHandler.onShuffleModeEnabledChanged(shuffleModeEnabled)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        playerStateHandler.onRepeatModeChanged(repeatMode)
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        super.onTimelineChanged(timeline, reason)
        playerStateHandler.onTimelineChanged(timeline, reason)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        playerStateHandler.onPlaybackStatsReady(eventTime, playbackStats)
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        playerStateHandler.onPlayerError(error)
    }

    companion object {
        private const val TAG = "MusicServicePlayerListeners"
    }
}
