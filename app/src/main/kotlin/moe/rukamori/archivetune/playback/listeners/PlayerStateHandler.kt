/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.listeners

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.constants.RepeatModeKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.playback.MusicServicePlayerListeners
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.get

@OptIn(UnstableApi::class)
internal class PlayerStateHandler(
    private val scope: CoroutineScope,
    private val playerDelegate: MusicServicePlayerListeners.PlayerDelegate,
    private val queueDelegate: MusicServicePlayerListeners.QueueDelegate,
    private val historyDelegate: MusicServicePlayerListeners.HistoryDelegate,
    private val widgetDelegate: MusicServicePlayerListeners.WidgetDelegate,
    private val metadataDelegate: MusicServicePlayerListeners.MetadataDelegate,
    private val notificationDelegate: MusicServicePlayerListeners.NotificationDelegate,
    private val togetherPlaybackEchoPolicy: TogetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
    private val crossfadeHooks: MusicServicePlayerListeners.CrossfadeHooks,
    private val transitionHandler: PlayerTransitionHandler? = null,
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
        transitionHandler = null,
    )

    private val player: Player get() = playerDelegate.player
    private val localPlayer: ExoPlayer get() = playerDelegate.localPlayer
    private val dataStore: DataStore<Preferences> get() = queueDelegate.dataStore

    private val togetherEchoSnapshot: TogetherEchoSnapshot
        get() =
            TogetherEchoSnapshot(
                isApplyingRemote = queueDelegate.isTogetherApplyingRemote(),
                suppressEchoUntilElapsedMs = queueDelegate.togetherSuppressEchoUntilElapsedMs,
                lastRemoteAppliedIndex = queueDelegate.togetherLastRemoteAppliedIndex,
                lastRemoteAppliedPlayWhenReady = queueDelegate.togetherLastRemoteAppliedPlayWhenReady,
            )

    fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        historyDelegate.updateHistoryTrackingPlaybackState()
        if (playbackState == STATE_ENDED || playbackState == STATE_IDLE) {
            historyDelegate.enqueueCurrentHistorySessionForFinalization()
            if ((!crossfadeHooks.isCrossfading || playbackState == STATE_IDLE) && !crossfadeHooks.crossfadeHandoffInProgress) {
                crossfadeHooks.cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            }
            if (playbackState == STATE_ENDED &&
                !queueDelegate.suppressAutoPlayback &&
                !(queueDelegate.isInitializingQueue && queueDelegate.currentQueue.isContextQueue) &&
                player.repeatMode == REPEAT_MODE_OFF
            ) {
                if (queueDelegate.currentQueue.hasNextPage() || queueDelegate.currentQueue.hasPendingContextItems) {
                    transitionHandler?.triggerPagination(isPlaybackEnded = true)
                } else if (dataStore.get(AutoLoadMoreKey, true)) {
                    queueDelegate.onInfiniteQueueEnabled()
                }
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

    fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
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

    fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        crossfadeHooks.secondaryCrossfadePlayer?.playbackParameters = playbackParameters
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
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

    fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
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

    fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        metadataDelegate.cancelPrefetch()
        if (player.isPlaying) {
            metadataDelegate.prefetchAround(player.currentMediaItemIndex)
        }
        notificationDelegate.updateNotification()
        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!togetherPlaybackEchoPolicy.isRemoteApplyingEcho(togetherEchoSnapshot)) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                queueDelegate.requestTogetherControl(
                    togetherPlaybackEchoPolicy.buildShuffleAction(shuffleModeEnabled),
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

    fun onRepeatModeChanged(repeatMode: Int) {
        metadataDelegate.cancelPrefetch()
        if (player.isPlaying) {
            metadataDelegate.prefetchAround(player.currentMediaItemIndex)
        }
        notificationDelegate.updateNotification()
        val joined = queueDelegate.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!togetherPlaybackEchoPolicy.isRemoteApplyingEcho(togetherEchoSnapshot)) {
                if (!joined.roomState.settings.allowGuestsToControlPlayback) {
                    scope.launch(SilentHandler) { queueDelegate.applyRemoteRoomState(joined.roomState, force = true) }
                    return
                }
                queueDelegate.requestTogetherControl(
                    togetherPlaybackEchoPolicy.buildRepeatAction(repeatMode),
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

    fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            metadataDelegate.cancelPrefetch()
            if (player.isPlaying) {
                metadataDelegate.prefetchAround(player.currentMediaItemIndex)
            }
        }
    }

    fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        historyDelegate.onPlaybackStatsReady(eventTime, playbackStats)
    }

    fun onPlayerError(error: PlaybackException) {
        notificationDelegate.onPlayerError(error)
    }
}
