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
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_DEVICE_VOLUME_CHANGED
import androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED
import androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED
import androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.playback.MusicServicePlayerListeners
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.get

@OptIn(UnstableApi::class)
internal class PlayerEventFanout(
    private val scope: CoroutineScope,
    private val queueDelegate: MusicServicePlayerListeners.QueueDelegate,
    private val historyDelegate: MusicServicePlayerListeners.HistoryDelegate,
    @Suppress("unused")
    private val widgetDelegate: MusicServicePlayerListeners.WidgetDelegate,
    private val metadataDelegate: MusicServicePlayerListeners.MetadataDelegate,
    private val notificationDelegate: MusicServicePlayerListeners.NotificationDelegate,
    private val togetherPlaybackEchoPolicy: TogetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
    private val crossfadeHooks: MusicServicePlayerListeners.CrossfadeHooks,
) {
    constructor(
        scope: CoroutineScope,
        queueDelegate: MusicServicePlayerListeners.QueueDelegate,
        historyDelegate: MusicServicePlayerListeners.HistoryDelegate,
        widgetDelegate: MusicServicePlayerListeners.WidgetDelegate,
        metadataDelegate: MusicServicePlayerListeners.MetadataDelegate,
        notificationDelegate: MusicServicePlayerListeners.NotificationDelegate,
        crossfadeHooks: MusicServicePlayerListeners.CrossfadeHooks,
    ) : this(
        scope = scope,
        queueDelegate = queueDelegate,
        historyDelegate = historyDelegate,
        widgetDelegate = widgetDelegate,
        metadataDelegate = metadataDelegate,
        notificationDelegate = notificationDelegate,
        togetherPlaybackEchoPolicy = TogetherPlaybackEchoPolicy,
        crossfadeHooks = crossfadeHooks,
    )

    private val dataStore: DataStore<Preferences> get() = queueDelegate.dataStore

    private val togetherEchoSnapshot: TogetherEchoSnapshot
        get() =
            TogetherEchoSnapshot(
                isApplyingRemote = queueDelegate.isTogetherApplyingRemote(),
                suppressEchoUntilElapsedMs = queueDelegate.togetherSuppressEchoUntilElapsedMs,
                lastRemoteAppliedIndex = queueDelegate.togetherLastRemoteAppliedIndex,
                lastRemoteAppliedPlayWhenReady = queueDelegate.togetherLastRemoteAppliedPlayWhenReady,
            )

    fun onEvents(
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
                val now = SystemClock.elapsedRealtime()
                val playWhenReady = player.playWhenReady
                val isEcho =
                    togetherPlaybackEchoPolicy.isPlayWhenReadyEcho(
                        snapshot = togetherEchoSnapshot,
                        playWhenReady = playWhenReady,
                        nowElapsedMs = now,
                    )
                if (!isEcho) {
                    val action = togetherPlaybackEchoPolicy.buildPlayPauseAction(playWhenReady)
                    queueDelegate.requestTogetherControl(action)
                }
            }
        }
        if (events.contains(EVENT_DEVICE_VOLUME_CHANGED)) {
            notificationDelegate.handleDeviceMuteStateChanged()
        }
        if (events.contains(EVENT_PLAY_WHEN_READY_CHANGED) && notificationDelegate.isDeviceMutedNow() && player.playWhenReady) {
            notificationDelegate.handleDeviceMuteStateChanged(playbackRequestedWhileMuted = true)
        }
        if (events.contains(EVENT_PLAYBACK_STATE_CHANGED) &&
            (player.playbackState == STATE_IDLE || player.playbackState == STATE_ENDED)
        ) {
            notificationDelegate.wasAutoPausedByDeviceMute = false
            notificationDelegate.unregisterMuteRecoveryObserver()
            notificationDelegate.updateAudiblePlaybackRecovery()
        }
        if (events.contains(EVENT_PLAYBACK_STATE_CHANGED) &&
            notificationDelegate.isDeviceMutedNow() &&
            player.playWhenReady
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
}
