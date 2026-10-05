/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.history

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
import moe.rukamori.archivetune.playback.MusicServicePlayerListeners
import moe.rukamori.archivetune.playback.beginHistorySession
import moe.rukamori.archivetune.playback.enqueueCurrentHistorySessionForFinalization
import moe.rukamori.archivetune.playback.updateHistoryTrackingPlaybackState
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

internal class PlaybackHistoryTracker(
    private val service: MusicService,
    store: PlaybackHistoryStore? = null,
) : PlaybackHistoryStore.SessionDelegate {

    constructor(service: MusicService) : this(service, null)

    private val store: PlaybackHistoryStore = store ?: PlaybackHistoryStore(service, this)

    val currentSessionToken: Long
        get() = service.currentHistorySessionToken

    val currentMediaId: String?
        get() = service.currentHistoryMediaId

    val currentAccumulatedPlayMs: Long
        get() = service.currentHistoryAccumulatedPlayMs

    val isSessionQueued: Boolean
        get() = service.currentHistorySessionQueued

    override fun historyThresholdMs(): Long =
        (runCatching { service.dataStore[HistoryDuration] }.getOrNull() ?: HISTORY_DURATION_DEFAULT)
            .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
            .toLong() * 1000L

    override fun currentHistoryPlayedMs(
        nowElapsedMs: Long,
    ): Long {
        val runningPlayMs =
            service.currentHistoryStartedAtElapsedMs
                ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
                ?: 0L
        return service.currentHistoryAccumulatedPlayMs + runningPlayMs
    }

    fun currentHistoryPlayedMs(): Long =
        currentHistoryPlayedMs(SystemClock.elapsedRealtime())

    fun flushCurrentHistoryPlayedTime(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        service.currentHistoryAccumulatedPlayMs = currentHistoryPlayedMs(nowElapsedMs)
        service.currentHistoryStartedAtElapsedMs = null
    }

    fun beginHistorySession(
        mediaId: String?,
        forceNew: Boolean = false,
    ) {
        val normalizedMediaId = mediaId?.trim()?.takeIf { it.isNotEmpty() }
        if (!forceNew &&
            service.currentHistoryMediaId == normalizedMediaId &&
            service.currentHistorySessionToken != 0L
        ) {
            updateHistoryTrackingPlaybackState()
            return
        }

        service.historyThresholdJob?.cancel()
        service.historyThresholdJob = null
        flushCurrentHistoryPlayedTime()
        store.enqueueCurrentHistorySessionForFinalization()

        service.currentHistorySessionToken = ++service.nextHistorySessionToken
        service.currentHistoryMediaId = normalizedMediaId
        service.currentHistoryAccumulatedPlayMs = 0L
        service.currentHistoryStartedAtElapsedMs = null
        service.currentHistoryEventId = null
        service.currentHistoryRemoteRegistered = false
        service.currentHistoryImmediateAttempted = false
        service.currentHistorySessionQueued = false

        updateHistoryTrackingPlaybackState()
    }

    fun updateHistoryTrackingPlaybackState() {
        val mediaId = service.currentHistoryMediaId
        if (mediaId == null || service.currentHistorySessionQueued) {
            service.historyThresholdJob?.cancel()
            service.historyThresholdJob = null
            service.currentHistoryStartedAtElapsedMs = null
            return
        }

        if (service.player.isPlaying) {
            if (service.currentHistoryStartedAtElapsedMs == null) {
                service.currentHistoryStartedAtElapsedMs = SystemClock.elapsedRealtime()
            }
        } else {
            flushCurrentHistoryPlayedTime()
        }

        syncHistoryThresholdJob()
    }

    override fun syncHistoryThresholdJob() {
        service.historyThresholdJob?.cancel()
        service.historyThresholdJob = null

        val mediaId = service.currentHistoryMediaId ?: return
        if (service.currentHistorySessionQueued) return
        if (service.dataStore.get(PauseListenHistoryKey, false)) return
        if (service.currentHistoryEventId != null && service.currentHistoryRemoteRegistered) return

        val thresholdMs = historyThresholdMs()
        val playedMs = currentHistoryPlayedMs()
        if (playedMs >= thresholdMs) {
            if (!service.currentHistoryImmediateAttempted) {
                store.maybeRecordCurrentPlaybackHistory()
            }
            return
        }
        if (!service.player.isPlaying) return

        service.historyThresholdJob =
            service.scope.launch {
                delay((thresholdMs - playedMs).coerceAtLeast(0L))
                store.maybeRecordCurrentPlaybackHistory()
            }
    }

    companion object {
        fun createHistoryDelegate(service: MusicService): MusicServicePlayerListeners.HistoryDelegate =
            object : MusicServicePlayerListeners.HistoryDelegate {
                override var historyThresholdJob: Job?
                    get() = service.historyThresholdJob
                    set(value) {
                        service.historyThresholdJob = value
                    }
                override val currentHistoryMediaId: String? get() = service.currentHistoryMediaId
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
