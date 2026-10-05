/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.history

import android.os.SystemClock
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import moe.rukamori.archivetune.playback.currentHistoryPlayedMs
import moe.rukamori.archivetune.playback.historyThresholdMs
import moe.rukamori.archivetune.playback.syncHistoryThresholdJob
import moe.rukamori.archivetune.ui.screens.settings.ListenBrainzManager
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.sql.SQLException
import java.time.LocalDateTime

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
        val pendingSessions = service.pendingHistoryFinalizations[mediaId] ?: return
        val index = pendingSessions.indexOfFirst { it.sessionToken == sessionToken }
        if (index == -1) return

        val existing = pendingSessions[index]
        pendingSessions[index] = existing.copy(
            eventId = result.eventId ?: existing.eventId,
            remoteRegistered = existing.remoteRegistered || result.remoteRegistered,
        )
    }

    fun enqueueCurrentHistorySessionForFinalization() {
        val mediaId = service.currentHistoryMediaId ?: return
        if (service.currentHistorySessionQueued) return

        service.pendingHistoryFinalizations.getOrPut(mediaId) { mutableListOf() }.add(
            PendingHistoryFinalization(
                sessionToken = service.currentHistorySessionToken,
                eventId = service.currentHistoryEventId,
                remoteRegistered = service.currentHistoryRemoteRegistered,
            ),
        )
        service.currentHistorySessionQueued = true
    }

    fun popPendingHistoryFinalization(mediaId: String): PendingHistoryFinalization? {
        val pendingSessions = service.pendingHistoryFinalizations[mediaId] ?: return null
        val pending = pendingSessions.firstOrNull() ?: return null
        pendingSessions.removeAt(0)
        if (pendingSessions.isEmpty()) service.pendingHistoryFinalizations.remove(mediaId)
        return pending
    }

    fun maybeRecordCurrentPlaybackHistory() {
        val mediaId = service.currentHistoryMediaId ?: return
        if (service.currentHistorySessionQueued) return
        if (service.dataStore.get(PauseListenHistoryKey, false)) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs < thresholdMs) {
            syncHistoryThresholdJob()
            return
        }

        val sessionToken = service.currentHistorySessionToken
        if (service.historyRecordingJobs.containsKey(sessionToken)) return
        service.currentHistoryImmediateAttempted = true

        val eventIdSnapshot = service.currentHistoryEventId
        val remoteRegisteredSnapshot = service.currentHistoryRemoteRegistered
        val mediaMetadataSnapshot = service.player.currentMetadata?.takeIf { it.id == mediaId }

        val deferred = service.scope.async {
            withContext(Dispatchers.IO) {
                val resolvedEventId = eventIdSnapshot ?: insertPlaybackHistoryEvent(mediaId, playedMs, mediaMetadataSnapshot)
                val remoteRegistered = remoteRegisteredSnapshot || registerRemotePlaybackHistory(mediaId)
                ImmediateHistoryResult(resolvedEventId, remoteRegistered)
            }
        }

        service.historyRecordingJobs[sessionToken] = deferred
        service.scope.launch {
            val result = runCatching { deferred.await() }.onFailure(::reportException).getOrNull()
            service.historyRecordingJobs.remove(sessionToken)

            if (result != null) {
                if (service.currentHistorySessionToken == sessionToken &&
                    !service.currentHistorySessionQueued &&
                    service.currentHistoryMediaId == mediaId
                ) {
                    service.currentHistoryEventId = result.eventId ?: service.currentHistoryEventId
                    service.currentHistoryRemoteRegistered = service.currentHistoryRemoteRegistered || result.remoteRegistered
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

        suspend fun registerTracking(playbackTrackingUrl: String): Boolean =
            YouTube.registerPlayback(playlistId = null, playbackTracking = playbackTrackingUrl)
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag("MusicService").w(throwable, "Failed to register remote playback history for %s", mediaId)
                }
                .onSuccess { YouTube.notifyHistorySynced() }
                .isSuccess

        service.remotePlaybackTrackingUrlCache[mediaId]?.let { cachedPlaybackTrackingUrl ->
            if (registerTracking(cachedPlaybackTrackingUrl)) return true
            service.remotePlaybackTrackingUrlCache.remove(mediaId, cachedPlaybackTrackingUrl)
        }

        val remotePlaybackTracking = service.retryWithoutPlaybackLoginContext {
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
        }.getOrNull()?.playbackTracking

        val refreshedPlaybackTrackingUrl = remotePlaybackTracking?.remotePlaybackTrackingUrl() ?: return false
        service.remotePlaybackTrackingUrlCache[mediaId] = refreshedPlaybackTrackingUrl
        return registerTracking(refreshedPlaybackTrackingUrl)
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
                        service.historyRecordingJobs[session.sessionToken]
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
            }

            service.ioScope.launch {
                try {
                    val song =
                        service.database.song(mediaId).first()
                            ?: return@launch

                    val lbEnabled = service.dataStore.get(ListenBrainzEnabledKey, false)
                    val lbToken = service.dataStore.get(ListenBrainzTokenKey, "")
                    if (lbEnabled && !lbToken.isNullOrBlank()) {
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
)

internal data class ImmediateHistoryResult(
    val eventId: Long?,
    val remoteRegistered: Boolean,
)
