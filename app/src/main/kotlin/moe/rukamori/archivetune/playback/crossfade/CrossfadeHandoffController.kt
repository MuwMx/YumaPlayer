/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.crossfade

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.TransitionTier
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.playback.ExoDeckController
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.applyEffectiveVolumeImmediately
import moe.rukamori.archivetune.playback.currentEffectivePlayerVolumeForMediaId
import moe.rukamori.archivetune.playback.updateAudiblePlaybackRecovery

internal suspend fun MusicService.finishCrossfade(
    target: CrossfadeTarget,
    incomingPlayer: ExoPlayer,
    plan: AutomixPlan? = activeAutomixPlan,
) {
    val targetIndex = resolveCrossfadeTargetIndex(target)
    if (targetIndex == C.INDEX_UNSET) {
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        return
    }

    var handoffCompleted = false
    try {
        crossfadeHandoffInProgress = true
        incomingPlayer.volume = currentEffectivePlayerVolumeForMediaId(target.mediaId).coerceIn(0f, maxSafeGainFactor)
        val userPlaybackSpeed = localPlayer.playbackParameters.takeIf { it != PlaybackParameters.DEFAULT }

        controller.completeHandoff()

        if (plan?.tier == TransitionTier.SMART_BEATMATCH) {
            localPlayer.playbackParameters = userPlaybackSpeed ?: PlaybackParameters.DEFAULT
        }

        val targetMetadata = incomingPlayer.currentMediaItem?.metadata
            ?: runCatching { player.getMediaItemAt(targetIndex).metadata }.getOrNull()
        if (targetMetadata != null) {
            currentMediaMetadata.value = targetMetadata
        }

        refreshPlaybackNotification()
        handoffCompleted = true
    } finally {
        if (!handoffCompleted) {
            crossfadeHandoffInProgress = false
            isCrossfading = false
            crossfadeProgress = 0f
            crossfadePlaybackRequested = false
            if (isControllerInitialized) {
                (controller as? ExoDeckController)?.cancel(resetVolume = true, resetPauseAtEnd = true)
            }
        }
    }

    isCrossfading = false
    crossfadeHandoffInProgress = false
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
    activeAutomixPlan = null
    applyEffectiveVolumeImmediately()
    updateAudiblePlaybackRecovery()
    scheduleCrossfade()
}

internal suspend fun MusicService.awaitPrimaryCrossfadeHandoffReady(incomingPlayer: ExoPlayer): Boolean {
    val deadlineMs = android.os.SystemClock.elapsedRealtime() + MusicService.CROSSFADE_HANDOFF_READY_TIMEOUT_MS
    while (currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
        if (player.playbackState == Player.STATE_READY && canHandoffWithoutRebuffer(incomingPlayer)) {
            return true
        }
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            return false
        }
        delay(25L)
    }
    return player.playbackState == Player.STATE_READY && canHandoffWithoutRebuffer(incomingPlayer)
}
