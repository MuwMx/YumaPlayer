package moe.rukamori.archivetune.playback

import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.ListenBrainzTokenKey
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.discord.DiscordHoldController
import moe.rukamori.archivetune.playback.discord.DiscordPresenceApplier
import moe.rukamori.archivetune.playback.discord.DiscordSyncOrchestrator
import moe.rukamori.archivetune.playback.discord.DiscordSyncRequest
import moe.rukamori.archivetune.playback.history.PlaybackHistoryStore
import moe.rukamori.archivetune.playback.history.PlaybackHistoryTracker
import moe.rukamori.archivetune.playback.history.remotePlaybackTrackingUrl as storeRemotePlaybackTrackingUrl
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import moe.rukamori.archivetune.ui.screens.settings.ListenBrainzManager
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

internal fun MusicService.startDiscordSyncWorker() = discordSyncOrchestrator.startDiscordSyncWorker()
internal fun MusicService.requestDiscordSync(reason: String, force: Boolean = false) =
    discordSyncOrchestrator.requestDiscordSync(reason, force)
internal fun MusicService.forceDiscordSync(reason: String) = discordSyncOrchestrator.forceDiscordSync(reason)
internal fun MusicService.ensureDiscordSyncFresh(epoch: Long) = discordSyncOrchestrator.ensureDiscordSyncFresh(epoch)

internal fun MusicService.updateActiveDiscordHoldState(nextHoldState: ActiveHoldState?) =
    discordHoldController.updateActiveDiscordHoldState(nextHoldState)
internal fun MusicService.reconcileDiscordHoldTimeoutJob(previousHoldState: ActiveHoldState?, nextHoldState: ActiveHoldState?) =
    discordHoldController.reconcileDiscordHoldTimeoutJob(previousHoldState, nextHoldState)
internal fun MusicService.clearDiscordHoldState() = discordHoldController.clearDiscordHoldState()
internal fun MusicService.markLastAppliedVisiblePresence(visibleDecision: DiscordPresenceDecision.Visible) =
    discordHoldController.markLastAppliedVisiblePresence(visibleDecision)

internal suspend fun MusicService.addPendingDiscordRefreshWaiter(waiter: CompletableDeferred<Boolean>) =
    discordSyncOrchestrator.addPendingDiscordRefreshWaiter(waiter)
internal suspend fun MusicService.takePendingDiscordRefreshWaiters(): List<CompletableDeferred<Boolean>> =
    discordSyncOrchestrator.takePendingDiscordRefreshWaiters()
internal suspend fun MusicService.requeueDiscordRefreshWaiters(waiters: List<CompletableDeferred<Boolean>>) =
    discordSyncOrchestrator.requeueDiscordRefreshWaiters(waiters)
internal fun MusicService.completeDiscordRefreshWaiters(waiters: List<CompletableDeferred<Boolean>>, result: Boolean) =
    discordSyncOrchestrator.completeDiscordRefreshWaiters(waiters, result)
internal suspend fun MusicService.refreshDiscordNow(): Boolean = discordSyncOrchestrator.refreshDiscordNow()
internal suspend fun MusicService.syncDiscordStateInternal(request: DiscordSyncRequest) =
    discordSyncOrchestrator.syncDiscordStateInternal(request)

internal suspend fun MusicService.applyDiscordPresenceDecision(
    request: DiscordSyncRequest,
    resolution: DiscordPresenceResolution,
    token: String,
    song: moe.rukamori.archivetune.db.entities.Song?,
): Boolean = DiscordPresenceApplier(this).applyDiscordPresenceDecision(request, resolution, token, song)

internal suspend fun MusicService.buildDiscordPresenceSnapshot(
    song: moe.rukamori.archivetune.db.entities.Song?,
    isPaused: Boolean,
): DiscordPresenceSnapshot? = DiscordPresenceApplier(this).buildDiscordPresenceSnapshot(song, isPaused)

internal fun MusicService.historyThresholdMs(): Long = PlaybackHistoryTracker(this).historyThresholdMs()
internal fun MusicService.currentHistoryPlayedMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long =
    PlaybackHistoryTracker(this).currentHistoryPlayedMs(nowElapsedMs)
internal fun MusicService.flushCurrentHistoryPlayedTime(nowElapsedMs: Long = SystemClock.elapsedRealtime()) =
    PlaybackHistoryTracker(this).flushCurrentHistoryPlayedTime(nowElapsedMs)

internal fun MusicService.updatePendingHistoryFinalization(
    mediaId: String,
    sessionToken: Long,
    result: ImmediateHistoryResult,
) = PlaybackHistoryStore(this).updatePendingHistoryFinalization(mediaId, sessionToken, result)
internal fun MusicService.enqueueCurrentHistorySessionForFinalization() =
    PlaybackHistoryStore(this).enqueueCurrentHistorySessionForFinalization()
internal fun MusicService.popPendingHistoryFinalization(mediaId: String): PendingHistoryFinalization? =
    PlaybackHistoryStore(this).popPendingHistoryFinalization(mediaId)

internal fun MusicService.beginHistorySession(mediaId: String?, forceNew: Boolean = false) =
    PlaybackHistoryTracker(this).beginHistorySession(mediaId, forceNew)
internal fun MusicService.updateHistoryTrackingPlaybackState() = PlaybackHistoryTracker(this).updateHistoryTrackingPlaybackState()
internal fun MusicService.syncHistoryThresholdJob() = PlaybackHistoryTracker(this).syncHistoryThresholdJob()
internal fun MusicService.maybeRecordCurrentPlaybackHistory() =
    PlaybackHistoryStore(this, PlaybackHistoryTracker(this)).maybeRecordCurrentPlaybackHistory()

internal suspend fun MusicService.insertPlaybackHistoryEvent(
    mediaId: String,
    playTimeMs: Long,
    mediaMetadata: MediaMetadata?,
): Long? = PlaybackHistoryStore(this).insertPlaybackHistoryEvent(mediaId, playTimeMs, mediaMetadata)

internal suspend fun MusicService.registerRemotePlaybackHistory(mediaId: String): Boolean =
    PlaybackHistoryStore(this).registerRemotePlaybackHistory(mediaId)

internal fun moe.rukamori.archivetune.innertube.models.response.PlayerResponse.PlaybackTracking.remotePlaybackTrackingUrl(): String? =
    storeRemotePlaybackTrackingUrl()

internal fun MusicService.notifyScrobbleManagerOnStart() =
    scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
internal fun MusicService.notifyScrobbleManagerOnStop() = scrobbleManager?.onSongStop()
internal fun MusicService.updateScrobbleManagerState(isPlaying: Boolean) =
    scrobbleManager?.onPlayerStateChanged(isPlaying, player.currentMetadata, duration = player.duration)

internal fun MusicService.ensurePresenceManagerInternal() {
    if (DiscordPresenceManager.isRunning() && lastPresenceToken != null) return

    scope.launch {
        if (!dataStore.get(EnableDiscordRPCKey, true)) {
            if (DiscordPresenceManager.isRunning()) {
                Timber.tag("MusicService").d("Discord RPC disabled → stopping presence manager")
                try {
                    DiscordPresenceManager.stop()
                } catch (_: Exception) {
                }
                lastPresenceToken = null
            }
            return@launch
        }

        val key: String = dataStore.get(DiscordTokenKey, "")
        if (key.isNullOrBlank()) {
            if (DiscordPresenceManager.isRunning()) {
                Timber.tag("MusicService").d("No Discord OAuth session -> stopping presence manager")
                try {
                    DiscordPresenceManager.stop()
                } catch (_: Exception) {
                }
                lastPresenceToken = null
            }
            return@launch
        }

        if (DiscordPresenceManager.isRunning() && lastPresenceToken == key) {
            return@launch
        }

        try {
            DiscordPresenceManager.stop()
            DiscordPresenceManager.start(
                context = this@ensurePresenceManagerInternal,
                token = key,
            )
            DiscordPresenceManager.setOnTransportInvalidated { reason ->
                Timber.tag(MusicService.DISCORD_SYNC_TAG).w(
                    "transport invalidated reason=%s; requesting forced sync",
                    reason,
                )
                requestDiscordSync(
                    reason = "transport_invalidated:$reason",
                    force = true,
                )
            }
            Timber.tag("MusicService").d("Presence manager started")
            lastPresenceToken = key
            requestDiscordSync(
                reason = "presence_manager_started",
                force = true,
            )
        } catch (ex: Exception) {
            Timber.tag("MusicService").e(ex, "Failed to start presence manager")
        }
    }
}

internal suspend fun MusicService.handlePresenceAndListenBrainzInternal(
    mediaId: String?,
    mediaMetadata: MediaMetadata?,
    durationMs: Long,
    positionMs: Long,
    reason: String,
) {
    try {
        val song = if (mediaId != null) withContext(Dispatchers.IO) { database.song(mediaId).first() } else null
        val finalSong =
            resolvePresenceSong(
                dbSong = song,
                mediaMetadata = mediaMetadata,
                durationMs = durationMs,
            ) ?: return

        try {
            val lbEnabled = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzEnabledKey, false) }
            val lbToken = withContext(Dispatchers.IO) { dataStore.get(ListenBrainzTokenKey, "") }
            if (lbEnabled && !lbToken.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        ListenBrainzManager.submitPlayingNow(this@handlePresenceAndListenBrainzInternal, lbToken, finalSong, positionMs)
                    } catch (ie: Exception) {
                        Timber.tag("MusicService").v(ie, "ListenBrainz playing_now submit failed for $reason")
                    }
                }
            }
        } catch (_: Exception) {
        }
    } catch (e: Exception) {
        Timber.tag("MusicService").v(e, "presence and listenbrainz follow-up work failed for $reason")
    }
}
