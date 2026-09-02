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
        return
    }

    val currentMediaId = player.currentMediaItem?.mediaId ?: return
    val currentIndex = player.currentMediaItemIndex
    val triggerAt = duration - effectiveDuration - MusicService.CROSSFADE_END_GUARD_MS

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
                            .coerceAtMost(effectiveDuration)
                    if (adjustedDuration >= MusicService.MIN_CROSSFADE_DURATION_MS) {
                        startCrossfade(target, adjustedDuration)
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

internal fun MusicService.effectiveCrossfadeDuration(duration: Long): Long? {
    if (duration == C.TIME_UNSET || duration <= 0L) return null
    val maxDuration = duration - MusicService.CROSSFADE_END_GUARD_MS
    if (maxDuration < MusicService.MIN_CROSSFADE_DURATION_MS) return null
    return crossfadeDurationMs
        .coerceAtLeast(MusicService.MIN_CROSSFADE_DURATION_MS)
        .coerceAtMost(maxDuration)
}

internal fun MusicService.isGaplessAlbumTransition(
    currentItem: MediaItem,
    targetItem: MediaItem,
): Boolean {
    val currentAlbum =
        currentItem.metadata
            ?.album
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: currentItem.metadata
                ?.album
                ?.title
                ?.takeIf { it.isNotBlank() }
            ?: currentItem.mediaMetadata.albumTitle
                ?.toString()
                ?.takeIf { it.isNotBlank() }
    val targetAlbum =
        targetItem.metadata
            ?.album
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: targetItem.metadata
                ?.album
                ?.title
                ?.takeIf { it.isNotBlank() }
            ?: targetItem.mediaMetadata.albumTitle
                ?.toString()
                ?.takeIf { it.isNotBlank() }
    return currentAlbum != null && currentAlbum == targetAlbum
}

internal fun MusicService.prepareSecondaryCrossfadePlayer(target: MusicService.CrossfadeTarget): ExoPlayer? {
    val existingPlayer = secondaryCrossfadePlayer
    if (existingPlayer != null && secondaryCrossfadeTarget == target) {
        return existingPlayer
    }

    releaseSecondaryCrossfadePlayer()

    val targetItem =
        runCatching { player.getMediaItemAt(target.index) }
            .getOrNull()
            ?.takeIf { it.mediaId == target.mediaId }
            ?: return null

    return runCatching {
        createSecondaryCrossfadePlayer().also { secondaryPlayer ->
            secondaryCrossfadePlayer = secondaryPlayer
            secondaryCrossfadeTarget = target
            secondaryPlayer.setMediaItem(targetItem)
            secondaryPlayer.playbackParameters = player.playbackParameters
            secondaryPlayer.volume = 0f
            secondaryPlayer.prepare()
        }
    }.onFailure { error ->
        Timber.tag(MusicService.TAG).w(error, "Failed to prepare crossfade player")
        releaseSecondaryCrossfadePlayer()
    }.getOrNull()
}

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
            localPlayer.pauseAtEndOfMediaItems = true

            try {
                val requiredBufferedMs = requiredCrossfadeStartBufferMs(durationMs)
                if (!awaitCrossfadePlayerReady(incomingPlayer, MusicService.CROSSFADE_READY_TIMEOUT_MS, requiredBufferedMs)) {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                    scheduleCrossfade()
                    return@launch
                }

                incomingPlayer.playbackParameters = player.playbackParameters
                incomingPlayer.playWhenReady = crossfadePlaybackRequested
                if (crossfadePlaybackRequested) {
                    incomingPlayer.play()
                }

                var elapsedMs = 0L
                var lastTickMs = android.os.SystemClock.elapsedRealtime()
                while (isActive && elapsedMs < durationMs) {
                    if (player.currentMediaItem?.mediaId != outgoingMediaId) {
                        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        return@launch
                    }

                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (crossfadePlaybackRequested) {
                        incomingPlayer.playWhenReady = true
                        elapsedMs = (elapsedMs + (nowMs - lastTickMs)).coerceAtMost(durationMs)
                        crossfadeProgress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        applyCrossfadeVolumes(
                            crossfadeProgress,
                            crossfadeBaseVolume,
                            crossfadeIncomingBaseVolume,
                            localPlayer,
                            incomingPlayer,
                        )
                    } else {
                        incomingPlayer.pause()
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

    val incomingPosition = incomingPlayer.currentPosition.coerceAtLeast(0L)
    val shouldContinuePlayback = crossfadePlaybackRequested

    var handoffCompleted = false
    try {
        localPlayer.pauseAtEndOfMediaItems = false
        player.volume = 0f
        crossfadeHandoffInProgress = true
        player.seekTo(targetIndex, incomingPosition)
        player.playWhenReady = shouldContinuePlayback
        if (shouldContinuePlayback) {
            if (awaitPrimaryCrossfadeHandoffReady(incomingPlayer)) {
                val syncedIncomingPosition = incomingPlayer.currentPosition.coerceAtLeast(0L)
                player.seekTo(targetIndex, syncedIncomingPosition)
            }
        }
        currentMediaMetadata.value = player.getMediaItemAt(targetIndex).metadata
        handoffCompleted = true
    } finally {
        if (!handoffCompleted) {
            crossfadeHandoffInProgress = false
            isCrossfading = false
            crossfadeProgress = 0f
            crossfadePlaybackRequested = false
            releaseSecondaryCrossfadePlayer()
            applyEffectiveVolumeImmediately()
        }
    }

    isCrossfading = false
    crossfadeHandoffInProgress = false
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
    releaseSecondaryCrossfadePlayer()
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

internal fun MusicService.requiredCrossfadeStartBufferMs(durationMs: Long): Long =
    (durationMs + MusicService.CROSSFADE_HANDOFF_BUFFER_MS)
        .coerceAtLeast(MusicService.CROSSFADE_MIN_BUFFER_BEFORE_START_MS)
        .coerceAtMost(MusicService.CROSSFADE_MAX_BUFFER_BEFORE_START_MS)

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
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
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
    runCatching { playerToRelease.stop() }
    runCatching { playerToRelease.clearMediaItems() }
    runCatching { playerToRelease.release() }
}
