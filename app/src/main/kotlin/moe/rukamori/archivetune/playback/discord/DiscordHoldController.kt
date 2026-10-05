/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.discord

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.playback.ActiveHoldState
import moe.rukamori.archivetune.playback.DiscordHoldContext
import moe.rukamori.archivetune.playback.DiscordPresenceDecision
import moe.rukamori.archivetune.playback.LastAppliedVisiblePresence
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PausedPresenceGate
import moe.rukamori.archivetune.playback.requestDiscordSync
import timber.log.Timber

internal class DiscordHoldController(
    private val service: MusicService,
) {
    @Volatile
    var activeHoldState: ActiveHoldState? = null

    var activeHoldTimeoutJob: Job? = null

    @Volatile
    var lastAppliedVisiblePresence: LastAppliedVisiblePresence? = null

    @Volatile
    var lastDecision: DiscordPresenceDecision? = null

    @Volatile
    var pausedPresenceGate: PausedPresenceGate = PausedPresenceGate.FollowPreference

    fun updateActiveDiscordHoldState(nextHoldState: ActiveHoldState?) {
        val previousHoldState = activeHoldState
        activeHoldState = nextHoldState
        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
            "hold state transition previous=%s next=%s",
            previousHoldState,
            nextHoldState,
        )
        reconcileDiscordHoldTimeoutJob(previousHoldState, nextHoldState)
    }

    fun reconcileDiscordHoldTimeoutJob(
        previousHoldState: ActiveHoldState?,
        nextHoldState: ActiveHoldState?,
    ) {
        if (previousHoldState === nextHoldState) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).v("hold timeout job unchanged for holdState=%s", nextHoldState)
            return
        }

        activeHoldTimeoutJob?.cancel()
        activeHoldTimeoutJob = null

        if (nextHoldState == null) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d("no active hold state, no timeout job scheduled")
            return
        }

        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
            "scheduling hold timeout job state=%s timeoutMs=%d",
            nextHoldState,
            MusicService.DISCORD_HOLD_TIMEOUT_MS,
        )
        activeHoldTimeoutJob =
            service.scope.launch {
                delay(MusicService.DISCORD_HOLD_TIMEOUT_MS)
                Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                    "hold timeout fired state=%s -> enqueue resync",
                    nextHoldState,
                )
                service.requestDiscordSync(
                    reason = "hold_timeout_check",
                    force = true,
                )
            }
    }

    fun clearDiscordHoldState() {
        if (activeHoldState != null) {
            Timber.tag(MusicService.DISCORD_SYNC_TAG).d("clearing active hold state=%s", activeHoldState)
        }
        updateActiveDiscordHoldState(null)
    }

    fun markLastAppliedVisiblePresence(visibleDecision: DiscordPresenceDecision.Visible) {
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

    fun buildHoldContext(nowMs: Long = System.currentTimeMillis()): DiscordHoldContext =
        DiscordHoldContext(
            nowMs = nowMs,
            activeHoldState = activeHoldState,
            lastAppliedVisiblePresence = lastAppliedVisiblePresence,
            holdTimeoutMs = MusicService.DISCORD_HOLD_TIMEOUT_MS,
        )
}
