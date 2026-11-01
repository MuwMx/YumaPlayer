/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.history

import moe.rukamori.archivetune.playback.host.*

import android.os.SystemClock
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MAX
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MIN
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.listeners.MusicServicePlayerListeners
import moe.rukamori.archivetune.playback.host.beginHistorySession
import moe.rukamori.archivetune.playback.host.enqueueCurrentHistorySessionForFinalization
import moe.rukamori.archivetune.playback.host.updateHistoryTrackingPlaybackState
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

internal class PlaybackHistoryTracker(
    private val service: MusicService,
    store: PlaybackHistoryStore? = null,
) : PlaybackHistoryStore.SessionDelegate {

    constructor(service: MusicService) : this(service, null)

    private val store: PlaybackHistoryStore = store ?: PlaybackHistoryStore(service, this)

    val currentSessionToken: Long
        get() = service.historyCoordinator.currentHistorySessionToken

    val currentMediaId: String?
        get() = service.historyCoordinator.currentHistoryMediaId

    val currentAccumulatedPlayMs: Long
        get() = service.historyCoordinator.currentHistoryAccumulatedPlayMs

    val isSessionQueued: Boolean
        get() = service.historyCoordinator.currentHistorySessionQueued

    override fun historyThresholdMs(): Long =
        (runCatching { service.dataStore[HistoryDuration] }.getOrNull() ?: HISTORY_DURATION_DEFAULT)
            .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
            .toLong() * 1000L

    override fun currentHistoryPlayedMs(
        nowElapsedMs: Long,
    ): Long {
        val runningPlayMs =
            service.historyCoordinator.currentHistoryStartedAtElapsedMs
                ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
                ?: 0L
        return service.historyCoordinator.currentHistoryAccumulatedPlayMs + runningPlayMs
    }

    fun currentHistoryPlayedMs(): Long =
        currentHistoryPlayedMs(SystemClock.elapsedRealtime())

    fun flushCurrentHistoryPlayedTime(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        service.historyCoordinator.currentHistoryAccumulatedPlayMs = currentHistoryPlayedMs(nowElapsedMs)
        service.historyCoordinator.currentHistoryStartedAtElapsedMs = null
    }

    fun beginHistorySession(
        mediaId: String?,
        forceNew: Boolean = false,
    ) {
        val normalizedMediaId = mediaId?.trim()?.takeIf { it.isNotEmpty() }
        if (!forceNew &&
            service.historyCoordinator.currentHistoryMediaId == normalizedMediaId &&
            service.historyCoordinator.currentHistorySessionToken != 0L
        ) {
            updateHistoryTrackingPlaybackState()
            return
        }

        service.historyCoordinator.historyThresholdJob?.cancel()
        service.historyCoordinator.historyThresholdJob = null
        flushCurrentHistoryPlayedTime()
        store.enqueueCurrentHistorySessionForFinalization()

        service.historyCoordinator.currentHistorySessionToken = ++service.historyCoordinator.nextHistorySessionToken
        service.historyCoordinator.currentHistoryMediaId = normalizedMediaId
        service.historyCoordinator.currentHistoryAccumulatedPlayMs = 0L
        service.historyCoordinator.currentHistoryStartedAtElapsedMs = null
        service.historyCoordinator.currentHistoryEventId = null
        service.historyCoordinator.currentHistoryRemoteRegistered = false
        service.historyCoordinator.currentHistoryImmediateAttempted = false
        service.historyCoordinator.currentHistorySessionQueued = false

        updateHistoryTrackingPlaybackState()
    }

    fun updateHistoryTrackingPlaybackState() {
        val mediaId = service.historyCoordinator.currentHistoryMediaId
        if (mediaId == null || service.historyCoordinator.currentHistorySessionQueued) {
            service.historyCoordinator.historyThresholdJob?.cancel()
            service.historyCoordinator.historyThresholdJob = null
            service.historyCoordinator.currentHistoryStartedAtElapsedMs = null
            return
        }

        if (service.player.isPlaying) {
            if (service.historyCoordinator.currentHistoryStartedAtElapsedMs == null) {
                service.historyCoordinator.currentHistoryStartedAtElapsedMs = SystemClock.elapsedRealtime()
            }
        } else {
            flushCurrentHistoryPlayedTime()
        }

        syncHistoryThresholdJob()
    }

    override fun syncHistoryThresholdJob() {
        service.historyCoordinator.historyThresholdJob?.cancel()
        service.historyCoordinator.historyThresholdJob = null

        val mediaId = service.historyCoordinator.currentHistoryMediaId ?: return
        if (service.historyCoordinator.currentHistorySessionQueued) return
        if (service.dataStore.get(PauseListenHistoryKey, false)) return
        if (service.historyCoordinator.currentHistoryEventId != null && service.historyCoordinator.currentHistoryRemoteRegistered) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs >= thresholdMs) {
            if (!service.historyCoordinator.currentHistoryImmediateAttempted) {
                store.maybeRecordCurrentPlaybackHistory()
            }
            return
        }
        if (!service.player.isPlaying) return

        service.historyCoordinator.historyThresholdJob =
            service.scope.launch {
                delay((thresholdMs - playedMs).coerceAtLeast(0L))
                store.maybeRecordCurrentPlaybackHistory()
            }
    }

    companion object {
        fun createHistoryDelegate(service: MusicService): MusicServicePlayerListeners.HistoryDelegate =
            object : MusicServicePlayerListeners.HistoryDelegate {
                override var historyThresholdJob: Job?
                    get() = service.historyCoordinator.historyThresholdJob
                    set(value) {
                        service.historyCoordinator.historyThresholdJob = value
                    }
                override val currentHistoryMediaId: String? get() = service.historyCoordinator.currentHistoryMediaId
                override fun beginHistorySession(mediaId: String?, forceNew: Boolean) {
                    service.beginHistorySession(mediaId, forceNew)
                }
                override fun updateHistoryTrackingPlaybackState() {
                    service.updateHistoryTrackingPlaybackState()
                }
                override fun enqueueCurrentHistorySessionForFinalization() {
                    service.enqueueCurrentHistorySessionForFinalization()
                }
                override fun onPlaybackStatsReady(
                    eventTime: AnalyticsListener.EventTime,
                    playbackStats: PlaybackStats,
                ) {
                    service.handlePlaybackStatsReady(eventTime, playbackStats)
                }
            }
    }
}
