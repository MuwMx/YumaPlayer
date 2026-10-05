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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.playback.ExoDeckController
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.SafeTrackSelectionFactory
import moe.rukamori.archivetune.playback.applyEffectiveVolumeImmediately
import moe.rukamori.archivetune.playback.bumpCrossfadePlanGeneration
import moe.rukamori.archivetune.playback.createMediaSourceFactory
import moe.rukamori.archivetune.playback.isPlayerInitialized
import moe.rukamori.archivetune.playback.playbackAudioAttributes
import moe.rukamori.archivetune.playback.prepareNext
import moe.rukamori.archivetune.playback.shouldBypassPlayerCache
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer

internal fun MusicService.prepareSecondaryCrossfadePlayer(
    target: CrossfadeTarget,
    startPositionMs: Long = 0L,
): ExoPlayer? {
    if (secondaryPreparationFailedMediaId == target.mediaId) return null
    val incomingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(target.mediaId) else null
    val cueInMs = resolveIncomingCueInMs(activeAutomixPlan, incomingAnalysis, startPositionMs)
    val player = prepareNext(target)
    if (player != null && cueInMs > 0L && player.currentPosition != cueInMs) {
        player.seekTo(target.index, cueInMs)
    }
    return player
}

internal fun MusicService.createSecondaryCrossfadePlayer(): ExoPlayer {
    val djFilter = DjFilterAudioProcessor()
    return ExoPlayer
        .Builder(this)
        .setMediaSourceFactory(createMediaSourceFactory())
        .setRenderersFactory(createRenderersFactory(djFilter))
        .setLoadControl(createCrossfadeLoadControl())
        .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
        .setHandleAudioBecomingNoisy(false)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setAudioAttributes(playbackAudioAttributes(), false)
        .setSeekBackIncrementMs(5000)
        .setSeekForwardIncrementMs(5000)
        .build()
        .apply {
            djFilterByPlayer[this] = djFilter
            addListener(secondaryCrossfadeListener)
            setOffloadEnabled(false)
            skipSilenceEnabled = localPlayer.skipSilenceEnabled
        }
}

internal suspend fun MusicService.awaitCrossfadePlayerReady(
    crossfadePlayer: ExoPlayer,
    timeoutMs: Long,
    minimumBufferedMs: Long,
): Boolean {
    val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
    while (currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
        when (crossfadePlayer.playbackState) {
            Player.STATE_READY -> {
                if (hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs)) {
                    return true
                }
            }

            Player.STATE_IDLE -> {
                crossfadePlayer.prepare()
            }

            Player.STATE_ENDED -> {
                return false
            }
        }
        delay(50L)
    }
    return crossfadePlayer.playbackState == Player.STATE_READY &&
        hasBufferedForSmoothStart(crossfadePlayer, minimumBufferedMs)
}

internal fun MusicService.canHandoffWithoutRebuffer(incomingPlayer: ExoPlayer): Boolean {
    if (player.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.shouldBypassPlayerCache() == true
    ) {
        return true
    }
    if (hasBufferedForSmoothStart(localPlayer, MusicService.CROSSFADE_HANDOFF_BUFFER_MS)) {
        val bufferedPosition = localPlayer.bufferedPosition
        val incomingPosition = incomingPlayer.currentPosition.coerceAtLeast(0L)
        return bufferedPosition == C.TIME_UNSET ||
            incomingPosition + MusicService.CROSSFADE_HANDOFF_SEEK_GUARD_MS <= bufferedPosition
    }
    return false
}

internal fun MusicService.hasBufferedForSmoothStart(
    targetPlayer: ExoPlayer,
    minimumBufferedMs: Long,
): Boolean {
    if (minimumBufferedMs <= 0L) return true
    if (targetPlayer.currentMediaItem
            ?.localConfiguration
            ?.uri
            ?.shouldBypassPlayerCache() == true
    ) {
        return true
    }

    val duration = targetPlayer.duration
    val currentPosition = targetPlayer.currentPosition.coerceAtLeast(0L)
    val remainingDuration =
        if (duration != C.TIME_UNSET && duration > currentPosition) {
            duration - currentPosition
        } else {
            Long.MAX_VALUE
        }
    val requiredBufferedMs = minimumBufferedMs.coerceAtMost(remainingDuration)
    if (requiredBufferedMs <= 0L) return true

    val bufferedDuration = targetPlayer.totalBufferedDuration.coerceAtLeast(0L)
    if (bufferedDuration >= requiredBufferedMs) return true

    return duration != C.TIME_UNSET &&
        targetPlayer.bufferedPosition >= duration - MusicService.CROSSFADE_END_GUARD_MS
}

internal fun MusicService.cancelSecondaryCrossfadePreparation() {
    bumpCrossfadePlanGeneration()
    secondaryPreparationFailedMediaId = secondaryCrossfadeTarget?.mediaId
    hasPreparedSecondaryPlayer = false
    if (isCrossfading) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfading = false
        crossfadeHandoffInProgress = false
        activeAutomixPlan = null
        crossfadeProgress = 0f
        crossfadeIncomingBaseVolume = 1f
        crossfadePlaybackRequested = false
        if (isPlayerInitialized()) {
            applyEffectiveVolumeImmediately()
        }
    }
    if (isControllerInitialized) {
        (controller as? ExoDeckController)?.cancel(resetVolume = false, resetPauseAtEnd = true)
    }
}

internal fun MusicService.releaseSecondaryCrossfadePlayer() {
    if (isControllerInitialized) {
        (controller as? ExoDeckController)?.releaseTransitionDeck()
    }
}
