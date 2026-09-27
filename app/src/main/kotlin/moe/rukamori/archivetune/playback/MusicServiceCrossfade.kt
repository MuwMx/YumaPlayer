package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.playback.automix.TransitionPlanner
import timber.log.Timber

internal fun MusicService.scheduleCrossfade() {
    if (!isPlayerInitialized()) return
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null

    if (isCrossfading) return
    if (!player.playWhenReady) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val target = resolveCrossfadeTarget()
    val duration = player.duration
    val effectiveDuration = effectiveCrossfadeDuration(duration)
    if (target == null || effectiveDuration == null) {
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        activeAutomixPlan = null
        return
    }

    val automixPlan = if (automixEnabled) {
        TransitionPlanner.planTransition(
            currentDurationMs = duration,
            preferredDurationMs = effectiveDuration,
        ).also { activeAutomixPlan = it }
    } else {
        activeAutomixPlan = null
        null
    }

    val plannedDuration = automixPlan?.durationMs ?: effectiveDuration
    val triggerOffset = automixPlan?.triggerOffsetMs ?: effectiveDuration

    val currentMediaId = player.currentMediaItem?.mediaId ?: return
    val currentIndex = player.currentMediaItemIndex
    val triggerAt = duration - triggerOffset - MusicService.CROSSFADE_END_GUARD_MS

    crossfadeTriggerJob =
        scope.launch {
            var hasPreparedSecondaryPlayer = false
            while (isActive) {
                if (!crossfadeEnabled || isCrossfading) return@launch
                if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                    return@launch
                }
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    return@launch
                }

                val remainingToTrigger = triggerAt - player.currentPosition
                if (!hasPreparedSecondaryPlayer && remainingToTrigger <= MusicService.CROSSFADE_PREPARE_AHEAD_MS) {
                    prepareSecondaryCrossfadePlayer(target)
                    hasPreparedSecondaryPlayer = true
                }
                if (remainingToTrigger <= 0L) {
                    val adjustedDuration =
                        (duration - player.currentPosition - MusicService.CROSSFADE_END_GUARD_MS)
                            .coerceAtMost(plannedDuration)
                    if (adjustedDuration >= MusicService.MIN_CROSSFADE_DURATION_MS) {
                        startCrossfade(target, adjustedDuration, automixPlan?.incomingStartMs ?: 0L)
                    }
                    return@launch
                }

                val sleepMs =
                    when {
                        remainingToTrigger > 5_000L -> 1_000L
                        remainingToTrigger > 1_000L -> 250L
                        else -> 50L
                    }.coerceAtMost(remainingToTrigger).coerceAtLeast(1L)
                delay(sleepMs)
            }
        }
}

internal fun MusicService.resolveCrossfadeTarget(): MusicService.CrossfadeTarget? {
    if (!crossfadeEnabled || crossfadeDurationMs <= 0L) return null
    if (player.mediaItemCount == 0 || player.currentTimeline.isEmpty) return null
    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return null

    val currentIndex = player.currentMediaItemIndex
    if (currentIndex !in 0 until player.mediaItemCount) return null

    val repeatCurrent = player.repeatMode == Player.REPEAT_MODE_ONE
    val targetIndex = if (repeatCurrent) currentIndex else player.nextMediaItemIndex
    if (targetIndex == C.INDEX_UNSET || targetIndex !in 0 until player.mediaItemCount) return null
    if (!repeatCurrent && targetIndex == currentIndex) return null

    val currentItem = player.getMediaItemAt(currentIndex)
    val targetItem = player.getMediaItemAt(targetIndex)
    if (!repeatCurrent && crossfadeGapless && isGaplessAlbumTransition(currentItem, targetItem)) return null

    return MusicService.CrossfadeTarget(
        index = targetIndex,
        mediaId = targetItem.mediaId,
    )
}

internal fun MusicService.prepareSecondaryCrossfadePlayer(target: MusicService.CrossfadeTarget): ExoPlayer? =
    prepareNext(target)

internal fun MusicService.createSecondaryCrossfadePlayer(): ExoPlayer =
    ExoPlayer
        .Builder(this)
        .setMediaSourceFactory(createMediaSourceFactory())
        .setRenderersFactory(createRenderersFactory())
        .setLoadControl(createCrossfadeLoadControl())
        .setTrackSelector(DefaultTrackSelector(this, SafeTrackSelectionFactory()))
        .setHandleAudioBecomingNoisy(false)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setAudioAttributes(playbackAudioAttributes(), false)
        .setSeekBackIncrementMs(5000)
        .setSeekForwardIncrementMs(5000)
        .build()
        .apply {
            addListener(secondaryCrossfadeListener)
            setOffloadEnabled(false)
            skipSilenceEnabled = localPlayer.skipSilenceEnabled
        }

internal fun MusicService.startCrossfade(
    target: MusicService.CrossfadeTarget,
    durationMs: Long,
    incomingStartMs: Long = 0L,
) {
    if (isCrossfading || !crossfadeEnabled) return

    val incomingPlayer = prepareSecondaryCrossfadePlayer(target) ?: return
    val outgoingMediaId = player.currentMediaItem?.mediaId ?: return

    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    crossfadeJob?.cancel()
    crossfadeJob =
        scope.launch {
            isCrossfading = true
            crossfadeProgress = 0f
            crossfadeBaseVolume = currentEffectivePlayerVolume()
            crossfadeIncomingBaseVolume = currentEffectivePlayerVolumeForMediaId(target.mediaId)
            crossfadePlaybackRequested = player.playWhenReady

            try {
                val requiredBufferedMs = requiredCrossfadeStartBufferMs(durationMs)
                if (!awaitCrossfadePlayerReady(incomingPlayer, MusicService.CROSSFADE_READY_TIMEOUT_MS, requiredBufferedMs)) {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                    scheduleCrossfade()
                    return@launch
                }

                localPlayer.pauseAtEndOfMediaItems = true

                val standbyPlayer = incomingPlayer
                val outgoingPlayer = localPlayer

                outgoingPlayer.volume = (crossfadeBaseVolume * FALL(0f)).coerceIn(0f, maxSafeGainFactor)
                incomingPlayer.volume = (crossfadeIncomingBaseVolume * RISE(0f)).coerceIn(0f, maxSafeGainFactor)
                standbyPlayer.playbackParameters = player.playbackParameters
                standbyPlayer.playWhenReady = crossfadePlaybackRequested
                if (incomingStartMs > 0L) {
                    standbyPlayer.seekTo(target.index, incomingStartMs)
                } else if (standbyPlayer.currentPosition > 0L) {
                    standbyPlayer.seekTo(target.index, 0L)
                }
                if (crossfadePlaybackRequested) {
                    standbyPlayer.play()
                }

                var elapsedMs = 0L
                var lastTickMs = android.os.SystemClock.elapsedRealtime()
                var bufferingStartMs: Long? = null
                while (isActive && elapsedMs < durationMs) {
                    if (player.currentMediaItem?.mediaId != outgoingMediaId) {
                        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        return@launch
                    }

                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (crossfadePlaybackRequested) {
                        standbyPlayer.playWhenReady = true
                        val isIncomingProducingSound =
                            standbyPlayer.playbackState == Player.STATE_READY && standbyPlayer.isPlaying

                        if (isIncomingProducingSound) {
                            bufferingStartMs = null
                            elapsedMs = (elapsedMs + (nowMs - lastTickMs)).coerceAtMost(durationMs)
                            crossfadeProgress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            outgoingPlayer.volume = (crossfadeBaseVolume * FALL(crossfadeProgress)).coerceIn(0f, maxSafeGainFactor)
                            incomingPlayer.volume = (crossfadeIncomingBaseVolume * RISE(crossfadeProgress)).coerceIn(0f, maxSafeGainFactor)
                        } else {
                            if (standbyPlayer.playbackState == Player.STATE_ENDED || standbyPlayer.playerError != null) {
                                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                                return@launch
                            }
                            if (standbyPlayer.playbackState == Player.STATE_IDLE) {
                                standbyPlayer.prepare()
                            }
                            val stallStart = bufferingStartMs ?: nowMs.also { bufferingStartMs = it }
                            if (nowMs - stallStart >= PlaybackConstants.CROSSFADE_BUFFERING_TIMEOUT_MS) {
                                Timber.tag(MusicService.TAG).w("Crossfade incoming player buffering timed out; bailing out")
                                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                                return@launch
                            }
                        }
                    } else {
                        standbyPlayer.pause()
                        bufferingStartMs = null
                    }
                    lastTickMs = nowMs
                    delay(MusicService.CROSSFADE_FRAME_MS)
                }

                finishCrossfade(target, incomingPlayer)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(MusicService.TAG).w(error, "Crossfade failed")
                cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
            } finally {
                if (isCrossfading) {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                }
            }
        }
}

internal suspend fun MusicService.awaitCrossfadePlayerReady(
    crossfadePlayer: ExoPlayer,
    timeoutMs: Long,
    minimumBufferedMs: Long,
): Boolean {
    val deadlineMs = android.os.SystemClock.elapsedRealtime() + timeoutMs
    while (kotlinx.coroutines.currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
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

internal suspend fun MusicService.finishCrossfade(
    target: MusicService.CrossfadeTarget,
    incomingPlayer: ExoPlayer,
) {
    val targetIndex = resolveCrossfadeTargetIndex(target)
    if (targetIndex == C.INDEX_UNSET) {
        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
        return
    }

    var handoffCompleted = false
    try {
        localPlayer.pauseAtEndOfMediaItems = false
        crossfadeHandoffInProgress = true

        incomingPlayer.volume = currentEffectivePlayerVolumeForMediaId(target.mediaId).coerceIn(0f, maxSafeGainFactor)
        dualForwardingPlayer.attachPlayer(incomingPlayer)

        val playerA = localPlayer
        transferAudioEffects(incomingPlayer)
        incomingPlayer.setShuffleOrder(playerA.shuffleOrder)
        playerA.playWhenReady = false
        playerA.volume = 0f
        playerA.stop()
        playerA.clearMediaItems()
        reserveCrossfadePlayer = playerA
        localPlayer = incomingPlayer

        val targetMetadata = incomingPlayer.currentMediaItem?.metadata
            ?: runCatching { player.getMediaItemAt(targetIndex).metadata }.getOrNull()
        if (targetMetadata != null) {
            currentMediaMetadata.value = targetMetadata
        }
        handoffCompleted = true
    } finally {
        if (!handoffCompleted) {
            crossfadeHandoffInProgress = false
            isCrossfading = false
            crossfadeProgress = 0f
            crossfadePlaybackRequested = false
            secondaryCrossfadePlayer?.apply {
                playWhenReady = false
                volume = 0f
                stop()
                clearMediaItems()
            }
            dualForwardingPlayer.attachPlayer(localPlayer)
            dualPlayerRoleHolder.reset()
            releaseSecondaryCrossfadePlayer()
            applyEffectiveVolumeImmediately()
        }
    }

    isCrossfading = false
    crossfadeHandoffInProgress = false
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
    dualPlayerRoleHolder.reset()
    secondaryCrossfadePlayer = null
    secondaryCrossfadeTarget = null
    activeAutomixPlan = null
    applyEffectiveVolumeImmediately()
    updateAudiblePlaybackRecovery()
    scheduleCrossfade()
}

internal suspend fun MusicService.awaitPrimaryCrossfadeHandoffReady(incomingPlayer: ExoPlayer): Boolean {
    val deadlineMs = android.os.SystemClock.elapsedRealtime() + MusicService.CROSSFADE_HANDOFF_READY_TIMEOUT_MS
    while (kotlinx.coroutines.currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
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

internal fun MusicService.resolveCrossfadeTargetIndex(target: MusicService.CrossfadeTarget): Int {
    if (target.index in 0 until player.mediaItemCount &&
        player.getMediaItemAt(target.index).mediaId == target.mediaId
    ) {
        return target.index
    }

    for (index in 0 until player.mediaItemCount) {
        if (player.getMediaItemAt(index).mediaId == target.mediaId) {
            return index
        }
    }
    return C.INDEX_UNSET
}

internal fun MusicService.cancelCrossfade(
    resetVolume: Boolean,
    resetPauseAtEnd: Boolean,
) {
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    crossfadeJob?.cancel()
    crossfadeJob = null
    isCrossfading = false
    crossfadeHandoffInProgress = false
    activeAutomixPlan = null
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
    secondaryCrossfadePlayer?.apply {
        playWhenReady = false
        volume = 0f
        stop()
        clearMediaItems()
    }
    if (isPlayerInitialized()) {
        dualForwardingPlayer.attachPlayer(localPlayer)
    }
    dualPlayerRoleHolder.reset()
    if (isPlayerInitialized() && resetPauseAtEnd) {
        localPlayer.pauseAtEndOfMediaItems = false
    }
    releaseSecondaryCrossfadePlayer()
    if (resetVolume && isPlayerInitialized()) {
        applyEffectiveVolumeImmediately()
    }
}

internal fun MusicService.releaseSecondaryCrossfadePlayer() {
    val playerToRelease = secondaryCrossfadePlayer ?: return
    secondaryCrossfadePlayer = null
    secondaryCrossfadeTarget = null
    runCatching { playerToRelease.removeListener(secondaryCrossfadeListener) }
    runCatching { playerToRelease.playWhenReady = false }
    runCatching { playerToRelease.volume = 0f }
    runCatching { playerToRelease.stop() }
    runCatching { playerToRelease.clearMediaItems() }
    runCatching { playerToRelease.release() }
}
