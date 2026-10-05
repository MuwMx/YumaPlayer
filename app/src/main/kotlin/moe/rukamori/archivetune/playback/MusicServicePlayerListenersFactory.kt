/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.crossfade.kickOffTrackAnalysis
import moe.rukamori.archivetune.playback.crossfade.kickOffUpcomingTrackAnalysis
import moe.rukamori.archivetune.playback.crossfade.recheckCacheReadinessForCurrentAndNext
import moe.rukamori.archivetune.playback.crossfade.registerCacheListenersForMediaItem
import moe.rukamori.archivetune.playback.queues.Queue

internal fun MusicService.createMusicServicePlayerListeners(): MusicServicePlayerListeners {
    val service = this
    val playerDelegate =
        object : MusicServicePlayerListeners.PlayerDelegate {
            override val player: Player get() = service.player
            override val localPlayer: ExoPlayer get() = service.localPlayer
        }
    val queueDelegate =
        object : MusicServicePlayerListeners.QueueDelegate {
            override var currentQueue: Queue
                get() = service.currentQueue
                set(value) {
                    service.currentQueue = value
                }
            override val suppressAutoPlayback: Boolean get() = service.suppressAutoPlayback
            override val isInitializingQueue: Boolean get() = service.isInitializingQueue
            override val autoAddedMediaIds: MutableSet<String> get() = service.autoAddedMediaIds
            override val dataStore: DataStore<Preferences> get() = service.dataStore
            override fun onInfiniteQueueEnabled() = service.onInfiniteQueueEnabled()
            override suspend fun saveQueueToDisk() = service.saveQueueToDisk()
            override fun applyCurrentFirstShuffleOrder() = service.applyCurrentFirstShuffleOrder()

            override val togetherSessionState: StateFlow<moe.rukamori.archivetune.together.TogetherSessionState>
                get() = service.togetherSessionState
            override val togetherSuppressEchoUntilElapsedMs: Long get() = service.togetherSuppressEchoUntilElapsedMs
            override val togetherLastRemoteAppliedIndex: Int get() = service.togetherLastRemoteAppliedIndex
            override val togetherLastRemoteAppliedPlayWhenReady: Boolean? get() = service.togetherLastRemoteAppliedPlayWhenReady
            override fun isTogetherApplyingRemote(): Boolean = service.isTogetherApplyingRemote()
            override suspend fun applyRemoteRoomState(
                roomState: moe.rukamori.archivetune.together.TogetherRoomState,
                force: Boolean,
            ) {
                service.applyRemoteRoomState(roomState, force)
            }
            override fun requestTogetherControl(action: moe.rukamori.archivetune.together.ControlAction) {
                service.requestTogetherControl(action)
            }
        }
    val historyDelegate =
        moe.rukamori.archivetune.playback.history.PlaybackHistoryTracker.createHistoryDelegate(service)
    val widgetDelegate =
        object : MusicServicePlayerListeners.WidgetDelegate {
            override fun update() = service.widgetUpdater.update()
            override fun updateProgressTracking() = service.widgetUpdater.updateProgressTracking()
        }
    val metadataDelegate =
        object : MusicServicePlayerListeners.MetadataDelegate {
            override fun updateCurrentMediaMetadata(metadata: MediaMetadata?) {
                service.currentMediaMetadata.value = metadata
            }
            override fun onLyricsSongChanged(currentIndex: Int, queue: List<MediaMetadata>) {
                service.lyricsPreloadManager?.onSongChanged(currentIndex, queue)
            }
            override fun prefetchAround(index: Int) = service.prefetchAround(index)
            override fun cancelPrefetch() = service.cancelPrefetch()
            override fun kickOffUpcomingTrackAnalysis(index: Int) = service.kickOffUpcomingTrackAnalysis(index)
            override fun kickOffTrackAnalysis(mediaItem: MediaItem?) = service.kickOffTrackAnalysis(mediaItem)
            override fun registerCacheListenersForMediaItem(mediaItem: MediaItem?) =
                service.registerCacheListenersForMediaItem(mediaItem)
            override fun recheckCacheReadinessForCurrentAndNext() =
                service.recheckCacheReadinessForCurrentAndNext()
            override fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean =
                service.isCurrentPlaybackItemLocal(metadata)
            override fun onScrobbleSongStart(metadata: MediaMetadata?, durationMs: Long) {
                service.scrobbleManager?.onSongStart(metadata, duration = durationMs)
            }
            override fun onScrobbleSongStop() {
                service.scrobbleManager?.onSongStop()
            }
            override fun onScrobblePlayerStateChanged(
                isPlaying: Boolean,
                metadata: MediaMetadata?,
                durationMs: Long,
            ) {
                service.scrobbleManager?.onPlayerStateChanged(isPlaying, metadata, duration = durationMs)
            }
            override fun ensurePresenceManager() = service.ensurePresenceManager()
            override fun requestDiscordSync(reason: String, force: Boolean) = service.requestDiscordSync(reason, force)
            override suspend fun submitPlayingNow(
                mediaId: String?,
                mediaMetadata: MediaMetadata?,
                durationMs: Long,
                positionMs: Long,
                reason: String,
            ) {
                service.handlePresenceAndListenBrainz(mediaId, mediaMetadata, durationMs, positionMs, reason)
            }
        }
    val notificationDelegate =
        object : MusicServicePlayerListeners.NotificationDelegate {
            override fun updateNotification() = service.updateNotification()
            override fun shouldKeepAudioEffectSessionOpen(): Boolean = service.shouldKeepAudioEffectSessionOpen()
            override fun ensureAudioFocusForActivePlayback(): Boolean = service.ensureAudioFocusForActivePlayback()
            override fun updateWakeLock() = service.updateWakeLock()
            override fun hasResumablePlaybackNotification(): Boolean = service.hasResumablePlaybackNotification()
            override fun cancelIdleStop() = service.cancelIdleStop()
            override fun promoteToStartedService() = service.promoteToStartedService()
            override fun ensureStartedAsForeground() = service.ensureStartedAsForeground()
            override fun scheduleStopIfIdle() = service.scheduleStopIfIdle()
            override fun handleDeviceMuteStateChanged(playbackRequestedWhileMuted: Boolean) {
                service.handleDeviceMuteStateChanged(playbackRequestedWhileMuted)
            }
            override fun isDeviceMutedNow(): Boolean = service.isDeviceMutedNow()
            override fun unregisterMuteRecoveryObserver() = service.unregisterMuteRecoveryObserver()
            override var wasAutoPausedByDeviceMute: Boolean
                get() = service.wasAutoPausedByDeviceMute
                set(value) {
                    service.wasAutoPausedByDeviceMute = value
                }
            override fun updateAudiblePlaybackRecovery() = service.updateAudiblePlaybackRecovery()
            override fun ensureAudiblePlaybackVolume(reason: String) = service.ensureAudiblePlaybackVolume(reason)
            override fun onPlaybackStreamMediaItemChanged(mediaId: String?) {
                service.playbackStreamRecoveryTracker.onMediaItemChanged(mediaId)
            }
            override fun onPlaybackStreamRecovered(mediaId: String?) {
                service.playbackStreamRecoveryTracker.onPlaybackRecovered(mediaId)
            }
            override fun reconcileAudioEffectSession() = service.reconcileAudioEffectSession()
            override fun onPlayerError(error: PlaybackException) {
                service.onPlayerError(error)
            }
        }
    val crossfadeHooks =
        object : MusicServicePlayerListeners.CrossfadeHooks {
            override val secondaryCrossfadePlayer: ExoPlayer? get() = service.secondaryCrossfadePlayer
            override var secondaryPreparationFailedMediaId: String?
                get() = service.secondaryPreparationFailedMediaId
                set(value) {
                    service.secondaryPreparationFailedMediaId = value
                }
            override var hasPreparedSecondaryPlayer: Boolean
                get() = service.hasPreparedSecondaryPlayer
                set(value) {
                    service.hasPreparedSecondaryPlayer = value
                }
            override var isCrossfading: Boolean
                get() = service.isCrossfading
                set(value) {
                    service.isCrossfading = value
                }
            override val crossfadeHandoffInProgress: Boolean get() = service.crossfadeHandoffInProgress
            override var crossfadePlaybackRequested: Boolean
                get() = service.crossfadePlaybackRequested
                set(value) {
                    service.crossfadePlaybackRequested = value
                }
            override var crossfadeTriggerJob: Job?
                get() = service.crossfadeTriggerJob
                set(value) {
                    service.crossfadeTriggerJob = value
                }
            override var activeCrossfadeScheduledKey: Pair<String, CrossfadeTarget>?
                get() = service.activeCrossfadeScheduledKey
                set(value) {
                    service.activeCrossfadeScheduledKey = value
                }
            override fun scheduleCrossfade() = service.scheduleCrossfade()
            override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) {
                service.cancelCrossfade(resetVolume, resetPauseAtEnd)
            }
            override fun cancelSecondaryCrossfadePreparation() = service.cancelSecondaryCrossfadePreparation()
            override fun releaseSecondaryCrossfadePlayer() = service.releaseSecondaryCrossfadePlayer()
        }
    return MusicServicePlayerListeners(
        scope = service.scope,
        playerDelegate = playerDelegate,
        queueDelegate = queueDelegate,
        historyDelegate = historyDelegate,
        widgetDelegate = widgetDelegate,
        metadataDelegate = metadataDelegate,
        notificationDelegate = notificationDelegate,
        crossfadeHooks = crossfadeHooks,
    )
}
