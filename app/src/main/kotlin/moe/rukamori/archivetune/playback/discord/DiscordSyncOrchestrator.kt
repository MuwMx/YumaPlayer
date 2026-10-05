/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.discord

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.constants.DiscordShowWhenPausedKey
import moe.rukamori.archivetune.constants.DiscordTokenKey
import moe.rukamori.archivetune.constants.EnableDiscordRPCKey
import moe.rukamori.archivetune.playback.DiscordPresenceDecision
import moe.rukamori.archivetune.playback.DiscordPresenceInputs
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PausedPresenceGate
import moe.rukamori.archivetune.playback.derivePlaybackSemanticState
import moe.rukamori.archivetune.playback.deriveRawDiscordPresenceDecision
import moe.rukamori.archivetune.playback.resolveDiscordPresenceDecision
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

internal class DiscordSyncOrchestrator(
    private val service: MusicService,
    private val holdController: DiscordHoldController = service.discordHoldController,
    private val presenceApplier: DiscordPresenceApplier = DiscordPresenceApplier(service, holdController),
) {
    @Volatile
    var discordServiceStopping: Boolean = false

    @Volatile
    var lastPresenceToken: String? = null

    val discordSyncEpoch = AtomicLong(0L)
    val discordSyncRequests = Channel<DiscordSyncRequest>(Channel.CONFLATED)
    var discordSyncWorkerJob: Job? = null
    val pendingDiscordRefreshWaiters = mutableListOf<CompletableDeferred<Boolean>>()
    val discordRefreshWaitersMutex = Mutex()

    fun startDiscordSyncWorker() {
        if (discordSyncWorkerJob?.isActive == true) return
        discordSyncWorkerJob =
            service.scope.launch(Dispatchers.IO) {
                for (request in discordSyncRequests) {
                    try {
                        syncDiscordStateInternal(request)
                    } catch (_: StaleDiscordSyncException) {
                        Timber.tag(MusicService.DISCORD_SYNC_TAG).d("stale sync aborted epoch=%d reason=%s", request.epoch, request.reason)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Timber.tag(MusicService.DISCORD_SYNC_TAG).e(error, "sync failed epoch=%d reason=%s", request.epoch, request.reason)
                    }
                }
            }
    }

    fun requestDiscordSync(
        reason: String,
        force: Boolean = false,
    ) {
        val request = DiscordSyncRequest(
            epoch = discordSyncEpoch.incrementAndGet(),
            reason = reason,
            force = force,
        )
        if (discordSyncRequests.trySend(request).isFailure) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).w("failed to enqueue sync epoch=%d reason=%s", request.epoch, request.reason)
        }
    }

    fun forceDiscordSync(reason: String) = requestDiscordSync(reason = reason, force = true)

    fun ensureDiscordSyncFresh(epoch: Long) {
        if (epoch != discordSyncEpoch.get()) {
            throw StaleDiscordSyncException()
        }
    }

    suspend fun addPendingDiscordRefreshWaiter(waiter: CompletableDeferred<Boolean>) {
        discordRefreshWaitersMutex.withLock {
            pendingDiscordRefreshWaiters += waiter
        }
    }

    suspend fun takePendingDiscordRefreshWaiters(): List<CompletableDeferred<Boolean>> =
        discordRefreshWaitersMutex.withLock {
            val snapshot = pendingDiscordRefreshWaiters.toList()
            pendingDiscordRefreshWaiters.removeAll(snapshot)
            snapshot
        }

    suspend fun requeueDiscordRefreshWaiters(waiters: List<CompletableDeferred<Boolean>>) {
        if (waiters.isEmpty()) return
        discordRefreshWaitersMutex.withLock {
            waiters.forEach { waiter ->
                if (!waiter.isCompleted && !waiter.isCancelled) {
                    pendingDiscordRefreshWaiters += waiter
                }
            }
        }
    }

    fun completeDiscordRefreshWaiters(
        waiters: List<CompletableDeferred<Boolean>>,
        result: Boolean,
    ) {
        waiters.forEach { waiter ->
            if (!waiter.isCompleted && !waiter.isCancelled) {
                waiter.complete(result)
            }
        }
    }

    suspend fun refreshDiscordNow(): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        addPendingDiscordRefreshWaiter(waiter)
        requestDiscordSync(reason = "manual_refresh", force = true)
        return try {
            withTimeout(15_000L) { waiter.await() }
        } catch (error: CancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncDiscordStateInternal(request: DiscordSyncRequest) {
        val refreshWaiters = takePendingDiscordRefreshWaiters()
        try {
            ensureDiscordSyncFresh(request.epoch)

            val enabled = service.dataStore.get(EnableDiscordRPCKey, true)
            val token = service.dataStore.get(DiscordTokenKey, "")
            val hasToken = token.isNotBlank()
            val showWhenPaused = service.dataStore.get(DiscordShowWhenPausedKey, false)
            val (song, isPlaying, playWhenReady, playbackState) =
                withContext(Dispatchers.Main.immediate) {
                    Quadruple(
                        service.currentPresenceSong(),
                        service.player.isPlaying,
                        service.player.playWhenReady,
                        service.player.playbackState,
                    )
                }

            if (playWhenReady && holdController.pausedPresenceGate != PausedPresenceGate.FollowPreference) {
                holdController.pausedPresenceGate = PausedPresenceGate.FollowPreference
                Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                    "sync epoch=%d reason=%s reset paused gate because playback intent resumed",
                    request.epoch,
                    request.reason,
                )
            }

            val inputs = DiscordPresenceInputs(
                enabled = enabled,
                hasToken = hasToken,
                song = song,
                isPlaying = isPlaying,
                showWhenPaused = showWhenPaused,
                pausedPresenceGate = holdController.pausedPresenceGate,
                serviceStopping = discordServiceStopping,
                playWhenReady = playWhenReady,
                playbackState = playbackState,
            )
            val holdContext = holdController.buildHoldContext(nowMs = System.currentTimeMillis())
            val semanticState = derivePlaybackSemanticState(inputs)
            val rawDecision = deriveRawDiscordPresenceDecision(inputs, semanticState)
            val resolution = resolveDiscordPresenceDecision(rawDecision, holdContext)

            val decision = resolution.decision
            ensureDiscordSyncFresh(request.epoch)

            val effectiveForce = request.force || refreshWaiters.isNotEmpty()
            if (!effectiveForce && decision == holdController.lastDecision) {
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
                request.epoch, request.reason, request.force, effectiveForce, song?.song?.id,
                playWhenReady, playbackState, isPlaying, semanticState, rawDecision, decision,
                resolution.nextHoldState, holdController.lastAppliedVisiblePresence, refreshWaiters.size,
            )

            val applied = presenceApplier.applyDiscordPresenceDecision(
                request = request,
                resolution = resolution,
                token = token,
                song = song,
            )

            if (applied) {
                holdController.lastDecision = decision
            }
            if (decision is DiscordPresenceDecision.Hold) {
                requeueDiscordRefreshWaiters(refreshWaiters)
                Timber.tag(MusicService.DISCORD_SYNC_TAG).d("refresh waiters requeued because decision is Hold count=%d", refreshWaiters.size)
            } else {
                completeDiscordRefreshWaiters(refreshWaiters, applied)
            }
        } catch (_: StaleDiscordSyncException) {
            requeueDiscordRefreshWaiters(refreshWaiters)
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d("stale sync aborted epoch=%d reason=%s and refresh waiters requeued=%d", request.epoch, request.reason, refreshWaiters.size)
        } catch (error: CancellationException) {
            completeDiscordRefreshWaiters(refreshWaiters, false)
            throw error
        } catch (error: Exception) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).e(error, "syncDiscordStateInternal failed epoch=%d reason=%s", request.epoch, request.reason)
            completeDiscordRefreshWaiters(refreshWaiters, false)
            throw error
        }
    }
}

internal data class DiscordSyncRequest(
    val epoch: Long,
    val reason: String,
    val force: Boolean,
)

internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

internal class StaleDiscordSyncException : CancellationException("Stale Discord sync request")
