package moe.rukamori.archivetune.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.constants.DiscordShowWhenPausedKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MAX
import moe.rukamori.archivetune.constants.HISTORY_DURATION_MIN
import moe.rukamori.archivetune.constants.HistoryDuration
import moe.rukamori.archivetune.constants.PauseListenHistoryKey
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.sql.SQLException
import java.time.LocalDateTime

// Discord 1429-1860
internal fun MusicService.startDiscordSyncWorker() {
    if (discordSyncWorkerJob?.isActive == true) return
    discordSyncWorkerJob =
        scope.launch(Dispatchers.IO) {
            for (request in discordSyncRequests) {
                try {
                    syncDiscordStateInternal(request)
                } catch (_: MusicService.StaleDiscordSyncException) {
                    Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                        "stale sync aborted epoch=%d reason=%s",
                        request.epoch,
                        request.reason,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.tag(MusicService.DISCORD_SYNC_TAG).e(
                        error,
                        "sync failed epoch=%d reason=%s",
                        request.epoch,
                        request.reason,
                    )
                }
            }
        }
}

internal fun MusicService.requestDiscordSync(
    reason: String,
    force: Boolean = false,
) {
    val request =
        MusicService.DiscordSyncRequest(
            epoch = discordSyncEpoch.incrementAndGet(),
            reason = reason,
            force = force,
        )
    if (discordSyncRequests.trySend(request).isFailure) {
        Timber.tag(MusicService.DISCORD_SYNC_TAG).w(
            "failed to enqueue sync epoch=%d reason=%s",
            request.epoch,
            request.reason,
        )
    }
}

internal fun MusicService.forceDiscordSync(reason: String) {
    requestDiscordSync(
        reason = reason,
        force = true,
    )
}

internal fun MusicService.ensureDiscordSyncFresh(epoch: Long) {
    if (epoch != discordSyncEpoch.get()) {
        throw MusicService.StaleDiscordSyncException()
    }
}

internal fun MusicService.updateActiveDiscordHoldState(nextHoldState: ActiveHoldState?) {
    val previousHoldState = activeDiscordHoldState
    activeDiscordHoldState = nextHoldState
    Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
        "hold state transition previous=%s next=%s",
        previousHoldState,
        nextHoldState,
    )
    reconcileDiscordHoldTimeoutJob(previousHoldState, nextHoldState)
}

internal fun MusicService.reconcileDiscordHoldTimeoutJob(
    previousHoldState: ActiveHoldState?,
    nextHoldState: ActiveHoldState?,
) {
    if (previousHoldState === nextHoldState) {
        Timber.tag(MusicService.DISCORD_SYNC_TAG).v("hold timeout job unchanged for holdState=%s", nextHoldState)
        return
    }

    activeDiscordHoldTimeoutJob?.cancel()
    activeDiscordHoldTimeoutJob = null

    if (nextHoldState == null) {
        Timber.tag(MusicService.DISCORD_SYNC_TAG).d("no active hold state, no timeout job scheduled")
        return
    }

    Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
        "scheduling hold timeout job state=%s timeoutMs=%d",
        nextHoldState,
        MusicService.DISCORD_HOLD_TIMEOUT_MS,
    )
    activeDiscordHoldTimeoutJob =
        scope.launch {
            delay(MusicService.DISCORD_HOLD_TIMEOUT_MS)
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                "hold timeout fired state=%s -> enqueue resync",
                nextHoldState,
            )
            requestDiscordSync(
                reason = "hold_timeout_check",
                force = true,
            )
        }
}

internal fun MusicService.clearDiscordHoldState() {
    if (activeDiscordHoldState != null) {
        Timber.tag(MusicService.DISCORD_SYNC_TAG).d("clearing active hold state=%s", activeDiscordHoldState)
    }
    updateActiveDiscordHoldState(null)
}

internal fun MusicService.markLastAppliedVisiblePresence(visibleDecision: DiscordPresenceDecision.Visible) {
    lastAppliedVisiblePresence =
        LastAppliedVisiblePresence(
            songId = visibleDecision.songId,
            mode = visibleDecision.mode,
            appliedAtMs = System.currentTimeMillis(),
        )
    Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
        "marked last applied visible presence songId=%s mode=%s",
        visibleDecision.songId,
        visibleDecision.mode,
    )
}

internal suspend fun MusicService.addPendingDiscordRefreshWaiter(waiter: CompletableDeferred<Boolean>) {
    discordRefreshWaitersMutex.withLock {
        pendingDiscordRefreshWaiters += waiter
    }
}

internal suspend fun MusicService.takePendingDiscordRefreshWaiters(): List<CompletableDeferred<Boolean>> =
    discordRefreshWaitersMutex.withLock {
        val snapshot = pendingDiscordRefreshWaiters.toList()
        pendingDiscordRefreshWaiters.removeAll(snapshot)
        snapshot
    }

internal suspend fun MusicService.requeueDiscordRefreshWaiters(waiters: List<CompletableDeferred<Boolean>>) {
    if (waiters.isEmpty()) return
    discordRefreshWaitersMutex.withLock {
        waiters.forEach { waiter ->
            if (!waiter.isCompleted && !waiter.isCancelled) {
                pendingDiscordRefreshWaiters += waiter
            }
        }
    }
}

internal fun MusicService.completeDiscordRefreshWaiters(
    waiters: List<CompletableDeferred<Boolean>>,
    result: Boolean,
) {
    waiters.forEach { waiter ->
        if (!waiter.isCompleted && !waiter.isCancelled) {
            waiter.complete(result)
        }
    }
}

internal suspend fun MusicService.refreshDiscordNow(): Boolean {
    val waiter = CompletableDeferred<Boolean>()
    addPendingDiscordRefreshWaiter(waiter)
    requestDiscordSync(
        reason = "manual_refresh",
        force = true,
    )
    return try {
        withTimeout(15_000L) { waiter.await() }
    } catch (error: CancellationException) {
        false
    } catch (_: Exception) {
        false
    }
}

internal suspend fun MusicService.syncDiscordStateInternal(request: MusicService.DiscordSyncRequest) {
    val refreshWaiters = takePendingDiscordRefreshWaiters()
    try {
        ensureDiscordSyncFresh(request.epoch)

        val enabled = dataStore.get(EnableDiscordRPCKey, true)
        val token = dataStore.get(DiscordTokenKey, "")
        val hasToken = token.isNotBlank()
        val showWhenPaused = dataStore.get(DiscordShowWhenPausedKey, false)
        val (song, isPlaying, playWhenReady, playbackState) =
            withContext(Dispatchers.Main.immediate) {
                MusicService.Quadruple(
                    currentPresenceSong(),
                    player.isPlaying,
                    player.playWhenReady,
                    player.playbackState,
                )
            }

        if (playWhenReady && pausedPresenceGate != PausedPresenceGate.FollowPreference) {
            pausedPresenceGate = PausedPresenceGate.FollowPreference
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                "sync epoch=%d reason=%s reset paused gate because playback intent resumed",
                request.epoch,
                request.reason,
            )
        }

        val inputs =
            DiscordPresenceInputs(
                enabled = enabled,
                hasToken = hasToken,
                song = song,
                isPlaying = isPlaying,
                showWhenPaused = showWhenPaused,
                pausedPresenceGate = pausedPresenceGate,
                serviceStopping = discordServiceStopping,
                playWhenReady = playWhenReady,
                playbackState = playbackState,
            )
        val holdContext =
            DiscordHoldContext(
                nowMs = System.currentTimeMillis(),
                activeHoldState = activeDiscordHoldState,
                lastAppliedVisiblePresence = lastAppliedVisiblePresence,
                holdTimeoutMs = MusicService.DISCORD_HOLD_TIMEOUT_MS,
            )
        val semanticState = derivePlaybackSemanticState(inputs)
        val rawDecision = deriveRawDiscordPresenceDecision(inputs, semanticState)
        val resolution = resolveDiscordPresenceDecision(rawDecision, holdContext)

        val decision = resolution.decision
        ensureDiscordSyncFresh(request.epoch)

        val effectiveForce = request.force || refreshWaiters.isNotEmpty()
        if (!effectiveForce && decision == lastDiscordPresenceDecision) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).v(
                "sync epoch=%d reason=%s unchanged decision=%s",
                request.epoch,
                request.reason,
                decision,
            )
            completeDiscordRefreshWaiters(refreshWaiters, true)
            return
        }

        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
            "sync epoch=%d reason=%s force=%s effectiveForce=%s songId=%s playWhenReady=%s playbackState=%d isPlaying=%s semantic=%s raw=%s decision=%s holdState=%s lastAppliedVisible=%s refreshWaiters=%d",
            request.epoch,
            request.reason,
            request.force,
            effectiveForce,
            song?.song?.id,
            playWhenReady,
            playbackState,
            isPlaying,
            semanticState,
            rawDecision,
            decision,
            resolution.nextHoldState,
            lastAppliedVisiblePresence,
            refreshWaiters.size,
        )

        val applied =
            applyDiscordPresenceDecision(
                request = request,
                resolution = resolution,
                token = token,
                song = song,
            )

        if (applied) {
            lastDiscordPresenceDecision = decision
        }
        if (decision is DiscordPresenceDecision.Hold) {
            requeueDiscordRefreshWaiters(refreshWaiters)
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                "refresh waiters requeued because decision is Hold count=%d",
                refreshWaiters.size,
            )
        } else {
            completeDiscordRefreshWaiters(refreshWaiters, applied)
        }
    } catch (_: MusicService.StaleDiscordSyncException) {
        requeueDiscordRefreshWaiters(refreshWaiters)
        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
            "stale sync aborted epoch=%d reason=%s and refresh waiters requeued=%d",
            request.epoch,
            request.reason,
            refreshWaiters.size,
        )
    } catch (error: CancellationException) {
        completeDiscordRefreshWaiters(refreshWaiters, false)
        throw error
    } catch (error: Exception) {
        Timber.tag(MusicService.DISCORD_SYNC_TAG).e(error, "syncDiscordStateInternal failed epoch=%d reason=%s", request.epoch, request.reason)
        completeDiscordRefreshWaiters(refreshWaiters, false)
        throw error
    }
}

internal suspend fun MusicService.applyDiscordPresenceDecision(
    request: MusicService.DiscordSyncRequest,
    resolution: DiscordPresenceResolution,
    token: String,
    song: moe.rukamori.archivetune.db.entities.Song?,
): Boolean {
    ensureDiscordSyncFresh(request.epoch)

    val decision = resolution.decision
    Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
        "apply decision epoch=%d decision=%s tokenPresent=%s songId=%s",
        request.epoch,
        decision,
        token.isNotBlank() || !lastPresenceToken.isNullOrBlank(),
        song?.song?.id,
    )
    return when (decision) {
        is DiscordPresenceDecision.Hidden -> {
            clearDiscordHoldState()
            when (decision.reason) {
                HiddenReason.NoSong,
                HiddenReason.PausedByPreference,
                HiddenReason.PausedByNotificationDismiss,
                HiddenReason.NoStablePlaybackYet,
                HiddenReason.PlaybackStalled,
                -> {
                    ensureDiscordSyncFresh(request.epoch)
                    val cleared =
                        DiscordPresenceManager.clearNow(
                            context = this,
                            token = token.takeIf { it.isNotBlank() } ?: lastPresenceToken,
                        )
                    if (!cleared) {
                        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                            "clear skipped or failed for hidden reason=%s",
                            decision.reason,
                        )
                    }
                    cleared
                }

                HiddenReason.Disabled,
                HiddenReason.ServiceStopping,
                -> {
                    val clearToken = token.takeIf { it.isNotBlank() } ?: lastPresenceToken
                    ensureDiscordSyncFresh(request.epoch)
                    val cleared =
                        DiscordPresenceManager.clearNow(
                            context = this,
                            token = clearToken,
                        )
                    if (!cleared) {
                        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                            "terminal clear skipped or failed for hidden reason=%s",
                            decision.reason,
                        )
                    }
                    ensureDiscordSyncFresh(request.epoch)
                    DiscordPresenceManager.stop()
                    lastPresenceToken = null
                    true
                }

                HiddenReason.NoToken -> {
                    val clearToken = token.takeIf { it.isNotBlank() } ?: lastPresenceToken
                    ensureDiscordSyncFresh(request.epoch)
                    if (clearToken.isNullOrBlank()) {
                        Timber.tag(MusicService.DISCORD_SYNC_TAG).v(
                            "no token available for terminal clear; stopping manager only",
                        )
                    } else {
                        val cleared =
                            DiscordPresenceManager.clearNow(
                                context = this,
                                token = clearToken,
                            )
                        if (!cleared) {
                            Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                                "terminal clear skipped or failed for hidden reason=%s",
                                decision.reason,
                            )
                        }
                    }
                    ensureDiscordSyncFresh(request.epoch)
                    DiscordPresenceManager.stop()
                    lastPresenceToken = null
                    true
                }
            }
        }

        is DiscordPresenceDecision.Visible -> {
            clearDiscordHoldState()
            ensureDiscordSyncFresh(request.epoch)
            val snapshot = buildDiscordPresenceSnapshot(song, decision.isPaused) ?: return false
            ensureDiscordSyncFresh(request.epoch)
            val updated =
                DiscordPresenceManager.updateNow(
                    context = this,
                    token = token,
                    song = snapshot.song,
                    positionMs = snapshot.positionMs,
                    isPaused = snapshot.isPaused,
                    isMusicVideo = currentMediaMetadata.value?.isMusicVideo ?: false,
                )
            if (!updated) {
                Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                    "visible update failed songId=%s paused=%s",
                    decision.songId,
                    decision.isPaused,
                )
                false
            } else {
                if (token.isNotBlank()) {
                    lastPresenceToken = token
                }
                markLastAppliedVisiblePresence(decision)
                true
            }
        }

        is DiscordPresenceDecision.Hold -> {
            updateActiveDiscordHoldState(resolution.nextHoldState)
            true
        }
    }
}

internal suspend fun MusicService.buildDiscordPresenceSnapshot(
    song: moe.rukamori.archivetune.db.entities.Song?,
    isPaused: Boolean,
): DiscordPresenceSnapshot? {
    val resolvedSong = song ?: return null
    val positionMs = withContext(Dispatchers.Main.immediate) { player.currentPosition }
    return DiscordPresenceSnapshot(
        song = resolvedSong,
        positionMs = positionMs,
        isPaused = isPaused,
    )
}

// History 5704-5866 + Scrobbling

internal fun MusicService.historyThresholdMs(): Long =
    (runCatching { dataStore[HistoryDuration] }.getOrNull() ?: HISTORY_DURATION_DEFAULT)
        .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
        .toLong() * 1000L

internal fun MusicService.currentHistoryPlayedMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
    val runningPlayMs =
        currentHistoryStartedAtElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
    return currentHistoryAccumulatedPlayMs + runningPlayMs
}

internal fun MusicService.flushCurrentHistoryPlayedTime(nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
    currentHistoryAccumulatedPlayMs = currentHistoryPlayedMs(nowElapsedMs)
    currentHistoryStartedAtElapsedMs = null
}

internal fun MusicService.updatePendingHistoryFinalization(
    mediaId: String,
    sessionToken: Long,
    result: MusicService.ImmediateHistoryResult,
) {
    val pendingSessions = pendingHistoryFinalizations[mediaId] ?: return
    val index = pendingSessions.indexOfFirst { it.sessionToken == sessionToken }
    if (index == -1) return

    val existing = pendingSessions[index]
    pendingSessions[index] =
        existing.copy(
            eventId = result.eventId ?: existing.eventId,
            remoteRegistered = existing.remoteRegistered || result.remoteRegistered,
        )
}

internal fun MusicService.enqueueCurrentHistorySessionForFinalization() {
    val mediaId = currentHistoryMediaId ?: return
    if (currentHistorySessionQueued) return

    pendingHistoryFinalizations
        .getOrPut(mediaId) { mutableListOf() }
        .add(
            MusicService.PendingHistoryFinalization(
                sessionToken = currentHistorySessionToken,
                eventId = currentHistoryEventId,
                remoteRegistered = currentHistoryRemoteRegistered,
            ),
        )
    currentHistorySessionQueued = true
}

internal fun MusicService.popPendingHistoryFinalization(mediaId: String): MusicService.PendingHistoryFinalization? {
    val pendingSessions = pendingHistoryFinalizations[mediaId] ?: return null
    val pending = pendingSessions.firstOrNull() ?: return null
    pendingSessions.removeAt(0)
    if (pendingSessions.isEmpty()) {
        pendingHistoryFinalizations.remove(mediaId)
    }
    return pending
}

internal fun MusicService.beginHistorySession(
    mediaId: String?,
    forceNew: Boolean = false,
) {
    val normalizedMediaId = mediaId?.trim()?.takeIf { it.isNotEmpty() }
    if (!forceNew && currentHistoryMediaId == normalizedMediaId && currentHistorySessionToken != 0L) {
        updateHistoryTrackingPlaybackState()
        return
    }

    historyThresholdJob?.cancel()
    historyThresholdJob = null
    flushCurrentHistoryPlayedTime()
    enqueueCurrentHistorySessionForFinalization()

    currentHistorySessionToken = ++nextHistorySessionToken
    currentHistoryMediaId = normalizedMediaId
    currentHistoryAccumulatedPlayMs = 0L
    currentHistoryStartedAtElapsedMs = null
    currentHistoryEventId = null
    currentHistoryRemoteRegistered = false
    currentHistoryImmediateAttempted = false
    currentHistorySessionQueued = false

    updateHistoryTrackingPlaybackState()
}

internal fun MusicService.updateHistoryTrackingPlaybackState() {
    val mediaId = currentHistoryMediaId
    if (mediaId == null || currentHistorySessionQueued) {
        historyThresholdJob?.cancel()
        historyThresholdJob = null
        currentHistoryStartedAtElapsedMs = null
        return
    }

    if (player.isPlaying) {
        if (currentHistoryStartedAtElapsedMs == null) {
            currentHistoryStartedAtElapsedMs = SystemClock.elapsedRealtime()
        }
    } else {
        flushCurrentHistoryPlayedTime()
    }

    syncHistoryThresholdJob()
}

internal fun MusicService.syncHistoryThresholdJob() {
    historyThresholdJob?.cancel()
    historyThresholdJob = null

    val mediaId = currentHistoryMediaId ?: return
    if (currentHistorySessionQueued) return
    if (dataStore.get(PauseListenHistoryKey, false)) return
    if (currentHistoryEventId != null && currentHistoryRemoteRegistered) return

    val thresholdMs = historyThresholdMs()
    val playedMs = currentHistoryPlayedMs()
    if (playedMs >= thresholdMs) {
        if (!currentHistoryImmediateAttempted) {
            maybeRecordCurrentPlaybackHistory()
        }
        return
    }
    if (!player.isPlaying) return

    historyThresholdJob =
        scope.launch {
            delay((thresholdMs - playedMs).coerceAtLeast(0L))
            maybeRecordCurrentPlaybackHistory()
        }
}

internal fun MusicService.maybeRecordCurrentPlaybackHistory() {
    val mediaId = currentHistoryMediaId ?: return
    if (currentHistorySessionQueued) return
    if (dataStore.get(PauseListenHistoryKey, false)) return

    val thresholdMs = historyThresholdMs()
    val playedMs = currentHistoryPlayedMs()
    if (playedMs < thresholdMs) {
        syncHistoryThresholdJob()
        return
    }

    val sessionToken = currentHistorySessionToken
    if (historyRecordingJobs.containsKey(sessionToken)) return
    currentHistoryImmediateAttempted = true

    val eventIdSnapshot = currentHistoryEventId
    val remoteRegisteredSnapshot = currentHistoryRemoteRegistered
    val mediaMetadataSnapshot = player.currentMetadata?.takeIf { it.id == mediaId }

    val deferred =
        scope.async {
            withContext(Dispatchers.IO) {
                val resolvedEventId =
                    eventIdSnapshot
                        ?: insertPlaybackHistoryEvent(
                            mediaId = mediaId,
                            playTimeMs = playedMs,
                            mediaMetadata = mediaMetadataSnapshot,
                        )
                val remoteRegistered = remoteRegisteredSnapshot || registerRemotePlaybackHistory(mediaId)
                MusicService.ImmediateHistoryResult(
                    eventId = resolvedEventId,
                    remoteRegistered = remoteRegistered,
                )
            }
        }

    historyRecordingJobs[sessionToken] = deferred
    scope.launch {
        val result =
            runCatching { deferred.await() }
                .onFailure(::reportException)
                .getOrNull()

        historyRecordingJobs.remove(sessionToken)

        if (result != null) {
            if (currentHistorySessionToken == sessionToken &&
                !currentHistorySessionQueued &&
                currentHistoryMediaId == mediaId
            ) {
                currentHistoryEventId = result.eventId ?: currentHistoryEventId
                currentHistoryRemoteRegistered = currentHistoryRemoteRegistered || result.remoteRegistered
            } else {
                updatePendingHistoryFinalization(mediaId, sessionToken, result)
            }
        }

        syncHistoryThresholdJob()
    }
}

internal suspend fun MusicService.insertPlaybackHistoryEvent(
    mediaId: String,
    playTimeMs: Long,
    mediaMetadata: moe.rukamori.archivetune.models.MediaMetadata?,
): Long? =
    try {
        database.withTransaction {
            if (song(mediaId).first() == null && mediaMetadata != null) {
                insert(mediaMetadata)
            }

            insert(
                Event(
                    songId = mediaId,
                    timestamp = LocalDateTime.now(),
                    playTime = playTimeMs,
                ),
            ).takeIf { it > 0L }
        }
    } catch (_: SQLException) {
        null
    } catch (throwable: Throwable) {
        reportException(throwable)
        null
    }

internal suspend fun MusicService.registerRemotePlaybackHistory(mediaId: String): Boolean {
    if (database
            .song(mediaId)
            .first()
            ?.song
            ?.isLocal == true
    ) {
        return false
    }

    suspend fun registerTracking(playbackTrackingUrl: String): Boolean =
        YouTube
            .registerPlayback(
                playlistId = null,
                playbackTracking = playbackTrackingUrl,
            ).onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Timber.tag("MusicService").w(
                    throwable,
                    "Failed to register remote playback history for %s",
                    mediaId,
                )
            }.onSuccess {
                YouTube.notifyHistorySynced()
            }.isSuccess

    remotePlaybackTrackingUrlCache[mediaId]?.let { cachedPlaybackTrackingUrl ->
        if (registerTracking(cachedPlaybackTrackingUrl)) {
            return true
        }
        remotePlaybackTrackingUrlCache.remove(mediaId, cachedPlaybackTrackingUrl)
    }

    val remotePlaybackTracking =
        retryWithoutPlaybackLoginContext {
            YTPlayerUtils.playerResponseForMetadata(mediaId)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            when (throwable) {
                is YTPlayerUtils.InvalidPlaybackLoginContextException -> {
                    promptLoginRecovery(mediaId, throwable.targetUrl)
                }

                is YTPlayerUtils.LoginRequiredForPlaybackException -> {
                    Timber.tag("MusicService").w(
                        throwable,
                        "Playback confirmation is required before refreshing remote playback tracking for %s",
                        mediaId,
                    )
                }

                else -> {
                    Timber.tag("MusicService").w(
                        throwable,
                        "Failed to refresh remote playback tracking for %s",
                        mediaId,
                    )
                }
            }
        }.getOrNull()
            ?.playbackTracking

    val refreshedPlaybackTrackingUrl = remotePlaybackTracking?.remotePlaybackTrackingUrl()
    if (refreshedPlaybackTrackingUrl != null) {
        remotePlaybackTrackingUrlCache[mediaId] = refreshedPlaybackTrackingUrl
        return registerTracking(refreshedPlaybackTrackingUrl)
    }

    return false
}

internal fun moe.rukamori.archivetune.innertube.models.response.PlayerResponse.PlaybackTracking.remotePlaybackTrackingUrl(): String? =
    videostatsPlaybackUrl?.baseUrl?.trim()?.takeIf { it.isNotEmpty() }

// Scrobbling extension helpers — keep @Inject in core, expose manager via internal access
internal fun MusicService.notifyScrobbleManagerOnStart() {
    scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
}

internal fun MusicService.notifyScrobbleManagerOnStop() {
    scrobbleManager?.onSongStop()
}

internal fun MusicService.updateScrobbleManagerState(isPlaying: Boolean) {
    scrobbleManager?.onPlayerStateChanged(isPlaying, player.currentMetadata, duration = player.duration)
}
