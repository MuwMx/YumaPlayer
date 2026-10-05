/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.DiscordPresenceDecision
import moe.rukamori.archivetune.playback.DiscordPresenceResolution
import moe.rukamori.archivetune.playback.DiscordPresenceSnapshot
import moe.rukamori.archivetune.playback.HiddenReason
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.ensureDiscordSyncFresh
import moe.rukamori.archivetune.ui.screens.settings.DiscordPresenceManager
import timber.log.Timber

internal class DiscordPresenceApplier(
    private val service: MusicService,
    private val holdController: DiscordHoldController = DiscordHoldController(service),
) {
    suspend fun applyDiscordPresenceDecision(
        request: DiscordSyncRequest,
        resolution: DiscordPresenceResolution,
        token: String,
        song: Song?,
    ): Boolean {
        service.ensureDiscordSyncFresh(request.epoch)

        val decision = resolution.decision
        Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
            "apply decision epoch=%d decision=%s tokenPresent=%s songId=%s",
            request.epoch,
            decision,
            token.isNotBlank() || !service.lastPresenceToken.isNullOrBlank(),
            song?.song?.id,
        )
        return when (decision) {
            is DiscordPresenceDecision.Hidden -> {
                holdController.clearDiscordHoldState()
                when (decision.reason) {
                    HiddenReason.NoSong,
                    HiddenReason.PausedByPreference,
                    HiddenReason.PausedByNotificationDismiss,
                    HiddenReason.NoStablePlaybackYet,
                    HiddenReason.PlaybackStalled,
                    -> {
                        service.ensureDiscordSyncFresh(request.epoch)
                        val cleared =
                            DiscordPresenceManager.clearNow(
                                context = service,
                                token = token.takeIf { it.isNotBlank() } ?: service.lastPresenceToken,
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
                        val clearToken = token.takeIf { it.isNotBlank() } ?: service.lastPresenceToken
                        service.ensureDiscordSyncFresh(request.epoch)
                        val cleared = DiscordPresenceManager.clearNow(context = service, token = clearToken)
                        if (!cleared) {
                            Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                                "terminal clear skipped or failed for hidden reason=%s",
                                decision.reason,
                            )
                        }
                        service.ensureDiscordSyncFresh(request.epoch)
                        DiscordPresenceManager.stop()
                        service.lastPresenceToken = null
                        true
                    }

                    HiddenReason.NoToken -> {
                        val clearToken = token.takeIf { it.isNotBlank() } ?: service.lastPresenceToken
                        service.ensureDiscordSyncFresh(request.epoch)
                        if (clearToken.isNullOrBlank()) {
                            Timber.tag(MusicService.DISCORD_SYNC_TAG).v(
                                "no token available for terminal clear; stopping manager only",
                            )
                        } else {
                            val cleared = DiscordPresenceManager.clearNow(context = service, token = clearToken)
                            if (!cleared) {
                                Timber.tag(MusicService.DISCORD_SYNC_TAG).d(
                                    "terminal clear skipped or failed for hidden reason=%s",
                                    decision.reason,
                                )
                            }
                        }
                        service.ensureDiscordSyncFresh(request.epoch)
                        DiscordPresenceManager.stop()
                        service.lastPresenceToken = null
                        true
                    }
                }
            }

            is DiscordPresenceDecision.Visible -> {
                holdController.clearDiscordHoldState()
                service.ensureDiscordSyncFresh(request.epoch)
                val snapshot = buildDiscordPresenceSnapshot(song, decision.isPaused) ?: return false
                service.ensureDiscordSyncFresh(request.epoch)
                val updated =
                    DiscordPresenceManager.updateNow(
                        context = service,
                        token = token,
                        song = snapshot.song,
                        positionMs = snapshot.positionMs,
                        isPaused = snapshot.isPaused,
                        isMusicVideo = service.currentMediaMetadata.value?.isMusicVideo ?: false,
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
                        service.lastPresenceToken = token
                    }
                    holdController.markLastAppliedVisiblePresence(decision)
                    true
                }
            }

            is DiscordPresenceDecision.Hold -> {
                holdController.updateActiveDiscordHoldState(resolution.nextHoldState)
                true
            }
        }
    }

    suspend fun buildDiscordPresenceSnapshot(
        song: Song?,
        isPaused: Boolean,
    ): DiscordPresenceSnapshot? {
        val resolvedSong = song ?: return null
        val positionMs = withContext(Dispatchers.Main.immediate) { service.player.currentPosition }
        return DiscordPresenceSnapshot(
            song = resolvedSong,
            positionMs = positionMs,
            isPaused = isPaused,
        )
    }
}
