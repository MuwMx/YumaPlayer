package moe.rukamori.archivetune.playback.history

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import java.util.concurrent.ConcurrentHashMap

internal class ServicePlaybackHistoryCoordinator {
    var nextHistorySessionToken = 0L
    var currentHistorySessionToken = 0L
    var currentHistoryMediaId: String? = null
    var currentHistoryAccumulatedPlayMs = 0L
    var currentHistoryStartedAtElapsedMs: Long? = null
    var currentHistoryEventId: Long? = null
    var currentHistoryRemoteRegistered = false
    var currentHistoryImmediateAttempted = false
    var currentHistorySessionQueued = false
    var currentHistoryCpn: String? = null
    var currentHistoryTracking: PlayerResponse.PlaybackTracking? = null
    var historyThresholdJob: Job? = null
    var remoteWatchtimeJob: Job? = null
    val remoteTrackingCache = ConcurrentHashMap<String, PlayerResponse.PlaybackTracking>()
    val pendingHistoryFinalizations = mutableMapOf<String, MutableList<PendingHistoryFinalization>>()
    val historyRecordingJobs = ConcurrentHashMap<Long, Deferred<ImmediateHistoryResult>>()
}