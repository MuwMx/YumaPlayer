/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.history

import moe.rukamori.archivetune.playback.host.*

import android.os.SystemClock
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.PlayerClient
import moe.rukamori.archivetune.constants.ListenBrainzEnabledKey
import moe.rukamori.archivetune.constants.ListenBrainzTokenKey
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.host.currentHistoryPlayedMs
import moe.rukamori.archivetune.playback.host.historyThresholdMs
import moe.rukamori.archivetune.playback.host.syncHistoryThresholdJob
import moe.rukamori.archivetune.ui.screens.settings.ListenBrainzManager
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.sql.SQLException
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

internal class PlaybackHistoryStore(
    private val service: MusicService,
    private val sessionDelegate: SessionDelegate? = null,
) {
    constructor(service: MusicService) : this(service, null)

    interface SessionDelegate {
        fun historyThresholdMs(): Long
        fun currentHistoryPlayedMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long
        fun syncHistoryThresholdJob()
    }

    private fun historyThresholdMs(): Long = sessionDelegate?.historyThresholdMs() ?: service.historyThresholdMs()
    private fun currentHistoryPlayedMs(): Long = sessionDelegate?.currentHistoryPlayedMs() ?: service.currentHistoryPlayedMs()
    private fun syncHistoryThresholdJob() = sessionDelegate?.syncHistoryThresholdJob() ?: service.syncHistoryThresholdJob()

    fun updatePendingHistoryFinalization(
        mediaId: String,
        sessionToken: Long,
        result: ImmediateHistoryResult,
    ) {
        val pendingSessions = service.historyCoordinator.pendingHistoryFinalizations[mediaId] ?: return
        val index = pendingSessions.indexOfFirst { it.sessionToken == sessionToken }
        if (index == -1) return

        val existing = pendingSessions[index]
        pendingSessions[index] = existing.copy(
            eventId = result.eventId ?: existing.eventId,
            remoteRegistered = existing.remoteRegistered || result.remoteRegistered,
        )
    }

    fun enqueueCurrentHistorySessionForFinalization() {
        val mediaId = service.historyCoordinator.currentHistoryMediaId ?: return
        if (service.historyCoordinator.currentHistorySessionQueued) return

        service.historyCoordinator.remoteWatchtimeJob?.cancel()
        service.historyCoordinator.remoteWatchtimeJob = null

        service.historyCoordinator.pendingHistoryFinalizations.getOrPut(mediaId) { mutableListOf() }.add(
            PendingHistoryFinalization(
                sessionToken = service.historyCoordinator.currentHistorySessionToken,
                eventId = service.historyCoordinator.currentHistoryEventId,
                remoteRegistered = service.historyCoordinator.currentHistoryRemoteRegistered,
                remoteCpn = service.historyCoordinator.currentHistoryCpn,
                remoteTracking = service.historyCoordinator.currentHistoryTracking,
            ),
        )
        service.historyCoordinator.currentHistoryCpn = null
        service.historyCoordinator.currentHistoryTracking = null
        service.historyCoordinator.currentHistorySessionQueued = true
    }

    fun popPendingHistoryFinalization(mediaId: String): PendingHistoryFinalization? {
        val pendingSessions = service.historyCoordinator.pendingHistoryFinalizations[mediaId] ?: return null
        val pending = pendingSessions.firstOrNull() ?: return null
        pendingSessions.removeAt(0)
        if (pendingSessions.isEmpty()) service.historyCoordinator.pendingHistoryFinalizations.remove(mediaId)
        return pending
    }

    fun maybeRecordCurrentPlaybackHistory() {
        val mediaId = service.historyCoordinator.currentHistoryMediaId ?: return
        if (service.historyCoordinator.currentHistorySessionQueued) return
        if (service.dataStore.get(PauseListenHistoryKey, false)) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs < thresholdMs) {
            syncHistoryThresholdJob()
            return
        }

        val sessionToken = service.historyCoordinator.currentHistorySessionToken
        if (service.historyCoordinator.historyRecordingJobs.containsKey(sessionToken)) return
        service.historyCoordinator.currentHistoryImmediateAttempted = true

        val eventIdSnapshot = service.historyCoordinator.currentHistoryEventId
        val remoteRegisteredSnapshot = service.historyCoordinator.currentHistoryRemoteRegistered
        val mediaMetadataSnapshot = service.player.currentMetadata?.takeIf { it.id == mediaId }

        val deferred = service.scope.async {
            withContext(Dispatchers.IO) {
                val resolvedEventId = eventIdSnapshot ?: insertPlaybackHistoryEvent(mediaId, playedMs, mediaMetadataSnapshot)
                val remoteRegistered = remoteRegisteredSnapshot || registerRemotePlaybackHistory(mediaId)
                ImmediateHistoryResult(resolvedEventId, remoteRegistered)
            }
        }

        service.historyCoordinator.historyRecordingJobs[sessionToken] = deferred
        service.scope.launch {
            val result = runCatching { deferred.await() }.onFailure(::reportException).getOrNull()
            service.historyCoordinator.historyRecordingJobs.remove(sessionToken)

            if (result != null) {
                if (service.historyCoordinator.currentHistorySessionToken == sessionToken &&
                    !service.historyCoordinator.currentHistorySessionQueued &&
                    service.historyCoordinator.currentHistoryMediaId == mediaId
                ) {
                    service.historyCoordinator.currentHistoryEventId = result.eventId ?: service.historyCoordinator.currentHistoryEventId
                    service.historyCoordinator.currentHistoryRemoteRegistered = service.historyCoordinator.currentHistoryRemoteRegistered || result.remoteRegistered
                } else {
                    updatePendingHistoryFinalization(mediaId, sessionToken, result)
                }
            }

            syncHistoryThresholdJob()
        }
    }

    suspend fun insertPlaybackHistoryEvent(
        mediaId: String,
        playTimeMs: Long,
        mediaMetadata: MediaMetadata?,
    ): Long? = try {
        service.database.withTransaction {
            if (song(mediaId).first() == null && mediaMetadata != null) {
                insert(mediaMetadata)
            }
            insert(Event(songId = mediaId, timestamp = LocalDateTime.now(), playTime = playTimeMs)).takeIf { it > 0L }
        }
    } catch (_: SQLException) {
        null
    } catch (throwable: Throwable) {
        reportException(throwable)
        null
    }

    suspend fun registerRemotePlaybackHistory(mediaId: String): Boolean {
        if (service.database.song(mediaId).first()?.song?.isLocal == true) return false

        val tracking =
            service.historyCoordinator.remoteTrackingCache[mediaId]
                ?: service.retryWithoutPlaybackLoginContext {
                    YTPlayerUtils.playerResponseForMetadata(mediaId)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    when (throwable) {
                        is YTPlayerUtils.InvalidPlaybackLoginContextException -> service.promptLoginRecovery(mediaId, throwable.targetUrl)
                        is YTPlayerUtils.LoginRequiredForPlaybackException ->
                            Timber.tag("MusicService").w(throwable, "Playback confirmation is required before refreshing remote playback tracking for %s", mediaId)
                        else ->
                            Timber.tag("MusicService").w(throwable, "Failed to refresh remote playback tracking for %s", mediaId)
                    }
                }.getOrNull()?.playbackTracking?.also {
                    service.historyCoordinator.remoteTrackingCache[mediaId] = it
                }

        val playbackUrl = tracking?.remotePlaybackTrackingUrl() ?: return false
        service.remotePlaybackTrackingUrlCache[mediaId] = playbackUrl
        val cpn = PlayerClient.newCpn()

        val registered =
            YouTube.registerPlayback(playlistId = null, playbackTracking = playbackUrl, cpn = cpn)
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    service.historyCoordinator.remoteTrackingCache.remove(mediaId, tracking)
                    service.remotePlaybackTrackingUrlCache.remove(mediaId, playbackUrl)
                    Timber.tag("MusicService").w(throwable, "Failed to register remote playback history for %s", mediaId)
                }
                .onSuccess { YouTube.notifyHistorySynced() }
                .isSuccess

        if (!registered) return false

        val sessionToken = service.historyCoordinator.currentHistorySessionToken
        if (service.historyCoordinator.currentHistoryMediaId == mediaId && !service.historyCoordinator.currentHistorySessionQueued) {
            service.historyCoordinator.currentHistoryCpn = cpn
            service.historyCoordinator.currentHistoryTracking = tracking
            startRemoteWatchtimeLoop(mediaId, sessionToken, cpn, tracking)
        }
        return true
    }

    private fun startRemoteWatchtimeLoop(
        mediaId: String,
        sessionToken: Long,
        cpn: String,
        tracking: PlayerResponse.PlaybackTracking,
    ) {
        service.historyCoordinator.remoteWatchtimeJob?.cancel()
        val atrUrl = tracking.atrUrl?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }
        val watchtimeUrl = tracking.videostatsWatchtimeUrl?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (atrUrl == null && watchtimeUrl == null) return

        service.historyCoordinator.remoteWatchtimeJob =
            service.ioScope.launch {
                if (atrUrl != null) {
                    delay(5_000L.milliseconds)
                    if (!isActive || service.historyCoordinator.currentHistorySessionToken != sessionToken) return@launch
                    YouTube.pingAtr(atrUrl = atrUrl, cpn = cpn)
                }
                if (watchtimeUrl == null) return@launch
                while (isActive &&
                    service.historyCoordinator.currentHistorySessionToken == sessionToken &&
                    !service.historyCoordinator.currentHistorySessionQueued
                ) {
                    delay(30_000L.milliseconds)
                    if (service.historyCoordinator.currentHistorySessionToken != sessionToken ||
                        service.historyCoordinator.currentHistorySessionQueued
                    ) break
                    val playedSec = (currentHistoryPlayedMs() / 1000L).coerceAtLeast(1L)
                    YouTube.pingWatchtime(watchtimeUrl = watchtimeUrl, cpn = cpn, seconds = playedSec, final = false)
                }
            }
    }

    fun handlePlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val mediaId = mediaItem.mediaId
        val thresholdMs = historyThresholdMs()
        val pendingSession = popPendingHistoryFinalization(mediaId)
        val alreadyPersistedForSession = pendingSession?.eventId != null || pendingSession?.remoteRegistered == true
        val reachedHistoryThreshold =
            playbackStats.totalPlayTimeMs >= thresholdMs &&
                !service.dataStore.get(PauseListenHistoryKey, false)
        val shouldPersistHistory = alreadyPersistedForSession || reachedHistoryThreshold

        if (shouldPersistHistory) {
            service.ioScope.launch {
                val pendingResult =
                    pendingSession?.let { session ->
                        service.historyCoordinator.historyRecordingJobs[session.sessionToken]
                            ?.let { deferred ->
                                runCatching { deferred.await() }
                                    .onFailure(::reportException)
                                    .getOrNull()
                            }?.let { result ->
                                session.copy(
                                    eventId = result.eventId ?: session.eventId,
                                    remoteRegistered = session.remoteRegistered || result.remoteRegistered,
                                )
                            }
                            ?: session
                    }

                val fallbackMetadata = mediaItem.metadata
                val eventId =
                    pendingResult?.eventId ?: insertPlaybackHistoryEvent(
                        mediaId = mediaId,
                        playTimeMs = playbackStats.totalPlayTimeMs,
                        mediaMetadata = fallbackMetadata,
                    )

                if (eventId != null) {
                    runCatching {
                        service.database.updateEventPlayTime(eventId, playbackStats.totalPlayTimeMs)
                    }.onFailure(::reportException)
                }

                try {
                    service.database.withTransaction {
                        incrementTotalPlayTime(mediaId, playbackStats.totalPlayTimeMs)
                    }
                } catch (_: SQLException) {
                } catch (throwable: Throwable) {
                    reportException(throwable)
                }

                if (pendingResult?.remoteRegistered != true) {
                    registerRemotePlaybackHistory(mediaId)
                }

                val cpn = pendingResult?.remoteCpn ?: service.historyCoordinator.currentHistoryCpn
                val tracking = pendingResult?.remoteTracking ?: service.historyCoordinator.currentHistoryTracking
                val watchtimeUrl = tracking?.videostatsWatchtimeUrl?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }
                if (cpn != null && watchtimeUrl != null) {
                    val totalSec = (playbackStats.totalPlayTimeMs / 1000L).coerceAtLeast(1L)
                    withContext(NonCancellable) {
                        YouTube.pingWatchtime(watchtimeUrl = watchtimeUrl, cpn = cpn, seconds = totalSec, final = true)
                    }
                }
            }

            service.ioScope.launch {
                try {
                    val song =
                        service.database.song(mediaId).first()
                            ?: return@launch

                    val lbEnabled = service.dataStore.get(ListenBrainzEnabledKey, false)
                    val lbToken = service.dataStore.get(ListenBrainzTokenKey, "")
                    if (lbEnabled && lbToken.isNotBlank()) {
                        val endMs = System.currentTimeMillis()
                        val startMs = endMs - playbackStats.totalPlayTimeMs
                        try {
                            ListenBrainzManager.submitFinished(service, lbToken, song, startMs, endMs)
                        } catch (ie: Exception) {
                            Timber.tag("MusicService").v(ie, "ListenBrainz finished submit failed")
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}
internal fun PlayerResponse.PlaybackTracking.remotePlaybackTrackingUrl(): String? =
    videostatsPlaybackUrl?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }

internal data class PendingHistoryFinalization(
    val sessionToken: Long,
    val eventId: Long?,
    val remoteRegistered: Boolean,
    val remoteCpn: String? = null,
    val remoteTracking: PlayerResponse.PlaybackTracking? = null,
)

internal data class ImmediateHistoryResult(
    val eventId: Long?,
    val remoteRegistered: Boolean,
)
