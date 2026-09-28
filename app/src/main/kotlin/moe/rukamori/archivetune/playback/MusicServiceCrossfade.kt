package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.EOFException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToLong
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.playback.automix.AutomixPlan
import moe.rukamori.archivetune.playback.automix.TransitionPlanner
import moe.rukamori.archivetune.playback.automix.TransitionTier
import moe.rukamori.archivetune.playback.smart.TrackAnalysisResult
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber

private const val FAST_ANALYSIS_TIMEOUT_MS = 500L

internal data class PrerollDriftAnchor(
    val primaryStartMs: Long,
    val standbyStartMs: Long,
)

private fun MusicService.computeCrossfadeTriggerAt(
    outgoingAnalysis: TrackAnalysisResult?,
    duration: Long,
    triggerOffset: Long,
    automixPlanTriggerAtMs: Long?,
): Long {
    val mixOutSec = outgoingAnalysis?.mixOutTime ?: 0.0
    return if (automixEnabled && mixOutSec > 0.0) {
        val mixOutTimeMs = (mixOutSec * 1000.0).roundToLong()
        mixOutTimeMs - (crossfadeDurationMs / 2L)
    } else {
        automixPlanTriggerAtMs ?: (duration - triggerOffset - MusicService.CROSSFADE_END_GUARD_MS)
    }
}

private fun MusicService.feedTriggerMixOutToPlayerState(
    mediaId: String,
    outgoingAnalysis: TrackAnalysisResult?,
    triggerAt: Long,
): TrackAnalysisResult? {
    if (!automixEnabled || outgoingAnalysis == null || outgoingAnalysis.mixOutTime <= 0.0) {
        return outgoingAnalysis
    }
    val triggerSec = (triggerAt.coerceAtLeast(0L)).toDouble() / 1000.0
    val updated = outgoingAnalysis.copy(mixOutTime = triggerSec)
    TrackAnalyzer.putCached(mediaId, updated)
    return updated
}

private fun resolveIncomingCueInMs(
    automixPlan: moe.rukamori.archivetune.playback.automix.AutomixPlan?,
    incomingAnalysis: TrackAnalysisResult?,
    explicitStartMs: Long = 0L,
): Long {
    if (explicitStartMs > 0L) return explicitStartMs
    val planStart = automixPlan?.incomingStartMs ?: 0L
    if (planStart > 0L) return planStart
    val mixInSec = incomingAnalysis?.mixInTime ?: 0.0
    return if (mixInSec > 0.0) (mixInSec * 1000.0).roundToLong() else 0L
}

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

    val currentMediaId = player.currentMediaItem?.mediaId ?: return
    val currentIndex = player.currentMediaItemIndex

    if (automixEnabled) {
        kickOffTrackAnalysis(player.currentMediaItem)
        kickOffTrackAnalysis(runCatching { player.getMediaItemAt(target.index) }.getOrNull())
    }

    var outgoingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(currentMediaId) else null
    var incomingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(target.mediaId) else null
    val automixAggr = automixAggressiveness
    val resolvedPreferred = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration)

    var automixPlan = if (automixEnabled && outgoingAnalysis != null) {
        TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoingAnalysis,
            incomingAnalysis = incomingAnalysis,
            currentDurationMs = duration,
            preferredDurationMs = resolvedPreferred,
            aggressiveness = automixAggr,
        ).also { activeAutomixPlan = it }
    } else if (automixEnabled) {
        TransitionPlanner.planTransition(
            currentDurationMs = duration,
            preferredDurationMs = resolvedPreferred,
            aggressiveness = automixAggr,
        ).also { activeAutomixPlan = it }
    } else {
        activeAutomixPlan = null
        null
    }

    var plannedDuration = automixPlan?.durationMs ?: effectiveDuration
    var triggerOffset = automixPlan?.triggerOffsetMs ?: effectiveDuration
    var triggerAt = computeCrossfadeTriggerAt(
        outgoingAnalysis = outgoingAnalysis,
        duration = duration,
        triggerOffset = triggerOffset,
        automixPlanTriggerAtMs = automixPlan?.triggerAtMs,
    )
    outgoingAnalysis = feedTriggerMixOutToPlayerState(currentMediaId, outgoingAnalysis, triggerAt)
    var prepareAhead = (automixPlan?.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS)
        .coerceAtLeast(PlaybackConstants.MIN_CROSSFADE_PREPARE_AHEAD_MS)

    crossfadeTriggerJob =
        scope.launch {
            hasPreparedSecondaryPlayer = false
            var hasPrerolledSecondaryPlayer = false
            var prerollPrimaryPosition = C.TIME_UNSET
            var prerollStandbyPosition = C.TIME_UNSET
            try {
                while (isActive) {
                    if (!crossfadeEnabled || isCrossfading) return@launch
                    if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                        return@launch
                    }
                    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                        return@launch
                    }
                    if (!player.playWhenReady) {
                        return@launch
                    }

                    if (automixEnabled && (outgoingAnalysis == null || (incomingAnalysis == null && (automixPlan?.incomingStartMs ?: 0L) == 0L))) {
                        var latestOutgoing = TrackAnalyzer.getCached(currentMediaId)
                        if (latestOutgoing == null && isTrackFullyCached(currentMediaId)) {
                            kickOffTrackAnalysis(currentMediaId)
                            latestOutgoing = TrackAnalyzer.getCached(currentMediaId)
                        }
                        val latestIncoming = TrackAnalyzer.getCached(target.mediaId)
                        if (latestOutgoing != null && latestOutgoing != outgoingAnalysis) {
                            outgoingAnalysis = latestOutgoing
                            incomingAnalysis = latestIncoming
                            val newPlan = TransitionPlanner.planSmartTransition(
                                outgoingAnalysis = latestOutgoing,
                                incomingAnalysis = latestIncoming,
                                currentDurationMs = duration,
                                preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                aggressiveness = automixAggressiveness,
                            )
                            automixPlan = newPlan
                            activeAutomixPlan = newPlan
                            plannedDuration = newPlan.durationMs
                            triggerOffset = newPlan.triggerOffsetMs
                            triggerAt = computeCrossfadeTriggerAt(
                                outgoingAnalysis = latestOutgoing,
                                duration = duration,
                                triggerOffset = triggerOffset,
                                automixPlanTriggerAtMs = newPlan.triggerAtMs,
                            )
                            outgoingAnalysis = feedTriggerMixOutToPlayerState(currentMediaId, latestOutgoing, triggerAt)
                            prepareAhead = (newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS)
                                .coerceAtLeast(PlaybackConstants.MIN_CROSSFADE_PREPARE_AHEAD_MS)
                        } else {
                            val currentOutgoing = outgoingAnalysis
                            if (latestIncoming != null && latestIncoming != incomingAnalysis && currentOutgoing != null) {
                                incomingAnalysis = latestIncoming
                                val newPlan = TransitionPlanner.planSmartTransition(
                                    outgoingAnalysis = currentOutgoing,
                                    incomingAnalysis = latestIncoming,
                                    currentDurationMs = duration,
                                    preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                    aggressiveness = automixAggressiveness,
                                )
                                automixPlan = newPlan
                                activeAutomixPlan = newPlan
                                plannedDuration = newPlan.durationMs
                                triggerOffset = newPlan.triggerOffsetMs
                                triggerAt = computeCrossfadeTriggerAt(
                                    outgoingAnalysis = currentOutgoing,
                                    duration = duration,
                                    triggerOffset = triggerOffset,
                                    automixPlanTriggerAtMs = newPlan.triggerAtMs,
                                )
                                outgoingAnalysis = feedTriggerMixOutToPlayerState(currentMediaId, currentOutgoing, triggerAt)
                                prepareAhead = (newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS)
                                    .coerceAtLeast(PlaybackConstants.MIN_CROSSFADE_PREPARE_AHEAD_MS)
                            }
                        }
                    }

                    val remainingToTrigger = triggerAt - player.currentPosition
                    val incomingStartMs = resolveIncomingCueInMs(automixPlan, incomingAnalysis)
                    val secondaryFailedForThisCycle = secondaryPreparationFailedMediaId == target.mediaId
                    if (!hasPreparedSecondaryPlayer && !secondaryFailedForThisCycle && remainingToTrigger <= prepareAhead) {
                        prepareSecondaryCrossfadePlayer(target, incomingStartMs)
                        hasPreparedSecondaryPlayer = true
                    }

                    if (hasPreparedSecondaryPlayer && !hasPrerolledSecondaryPlayer && !secondaryFailedForThisCycle &&
                        remainingToTrigger <= PlaybackConstants.CROSSFADE_PREROLL_LEAD_MS
                    ) {
                        val standby = secondaryCrossfadePlayer
                        if (standby != null && player.playWhenReady) {
                            standby.volume = 0f
                            val currentPlan = automixPlan
                            if (currentPlan?.tier == TransitionTier.SMART_BEATMATCH) {
                                standby.playbackParameters = PlaybackParameters(currentPlan.incomingTempoRatio)
                            } else {
                                standby.playbackParameters = player.playbackParameters
                            }
                            standby.playWhenReady = true
                            standby.play()
                            hasPrerolledSecondaryPlayer = true
                            prerollPrimaryPosition = player.currentPosition
                            prerollStandbyPosition = standby.currentPosition
                        }
                    }

                    if (remainingToTrigger <= 0L) {
                        if (secondaryPreparationFailedMediaId == target.mediaId) {
                            return@launch
                        }
                        if (automixEnabled && outgoingAnalysis == null && isTrackFullyCached(currentMediaId)) {
                            val currentDurationSec = if (duration > 0L && duration != C.TIME_UNSET) duration.toDouble() / 1000.0 else null
                            val fastAnalysis = runCatching {
                                withTimeoutOrNull(FAST_ANALYSIS_TIMEOUT_MS) {
                                    analyzeCachedTrack(currentMediaId, durationSeconds = currentDurationSec)
                                }
                            }.getOrNull()

                            if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                                return@launch
                            }
                            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                                return@launch
                            }

                            if (fastAnalysis != null) {
                                outgoingAnalysis = fastAnalysis
                                incomingAnalysis = TrackAnalyzer.getCached(target.mediaId)
                                val newPlan = TransitionPlanner.planSmartTransition(
                                    outgoingAnalysis = fastAnalysis,
                                    incomingAnalysis = incomingAnalysis,
                                    currentDurationMs = duration,
                                    preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                    aggressiveness = automixAggressiveness,
                                )
                                automixPlan = newPlan
                                activeAutomixPlan = newPlan
                                plannedDuration = newPlan.durationMs
                                triggerOffset = newPlan.triggerOffsetMs
                                triggerAt = computeCrossfadeTriggerAt(
                                    outgoingAnalysis = fastAnalysis,
                                    duration = duration,
                                    triggerOffset = triggerOffset,
                                    automixPlanTriggerAtMs = newPlan.triggerAtMs,
                                )
                                outgoingAnalysis = feedTriggerMixOutToPlayerState(currentMediaId, fastAnalysis, triggerAt)
                                prepareAhead = (newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS)
                                    .coerceAtLeast(PlaybackConstants.MIN_CROSSFADE_PREPARE_AHEAD_MS)

                                val updatedRemainingToTrigger = triggerAt - player.currentPosition
                                if (updatedRemainingToTrigger > 0L) {
                                    if (updatedRemainingToTrigger > PlaybackConstants.CROSSFADE_PREROLL_LEAD_MS && hasPrerolledSecondaryPlayer) {
                                        secondaryCrossfadePlayer?.apply {
                                            playWhenReady = false
                                            volume = 0f
                                            pause()
                                        }
                                        hasPrerolledSecondaryPlayer = false
                                        prerollPrimaryPosition = C.TIME_UNSET
                                        prerollStandbyPosition = C.TIME_UNSET
                                    }
                                    val sleepMs =
                                        when {
                                            updatedRemainingToTrigger > 5_000L -> 1_000L
                                            updatedRemainingToTrigger > 1_000L -> 250L
                                            else -> 50L
                                        }.coerceAtMost(updatedRemainingToTrigger).coerceAtLeast(1L)
                                    delay(sleepMs)
                                    continue
                                }
                            }
                        }

                        val currentOutgoing = outgoingAnalysis
                        val endLimit = if (currentOutgoing != null && currentOutgoing.mixOutTime > 0.0) {
                            (currentOutgoing.mixOutTime * 1000.0).roundToLong() + (crossfadeDurationMs / 2L)
                        } else if (automixPlan?.triggerAtMs != null && currentOutgoing != null && currentOutgoing.contentEndTime > 0.0) {
                            (currentOutgoing.contentEndTime * 1000.0).roundToLong()
                        } else {
                            duration
                        }
                        val adjustedDuration =
                            (endLimit - player.currentPosition - MusicService.CROSSFADE_END_GUARD_MS)
                                .coerceAtMost(plannedDuration)
                        if (adjustedDuration >= MusicService.MIN_CROSSFADE_DURATION_MS) {
                            val finalIncomingStartMs = resolveIncomingCueInMs(automixPlan, incomingAnalysis, incomingStartMs)
                            val prerollAnchor = if (hasPrerolledSecondaryPlayer && prerollPrimaryPosition != C.TIME_UNSET) {
                                PrerollDriftAnchor(
                                    primaryStartMs = prerollPrimaryPosition,
                                    standbyStartMs = prerollStandbyPosition,
                                )
                            } else {
                                null
                            }
                            hasPrerolledSecondaryPlayer = false
                            startCrossfade(target, adjustedDuration, finalIncomingStartMs, automixPlan, prerollAnchor)
                        } else {
                            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        }
                        return@launch
                    }

                    val remainingToPreroll = remainingToTrigger - PlaybackConstants.CROSSFADE_PREROLL_LEAD_MS
                    val nextStepTarget = if (!hasPrerolledSecondaryPlayer && remainingToPreroll > 0L) {
                        remainingToPreroll
                    } else {
                        remainingToTrigger
                    }
                    val sleepMs =
                        when {
                            remainingToTrigger > 5_000L -> 1_000L
                            remainingToTrigger > 1_000L -> 250L
                            else -> 50L
                        }.coerceAtMost(nextStepTarget).coerceAtLeast(1L)
                    delay(sleepMs)
                }
            } finally {
                if (hasPrerolledSecondaryPlayer && !isCrossfading) {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                }
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

internal fun MusicService.prepareSecondaryCrossfadePlayer(
    target: MusicService.CrossfadeTarget,
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

private fun MusicService.degradeToHardCut(
    target: MusicService.CrossfadeTarget,
    outgoingMediaId: String,
) {
    val userPlaybackSpeed = player.playbackParameters.takeIf { it != PlaybackParameters.DEFAULT }
    secondaryPreparationFailedMediaId = target.mediaId
    cancelCrossfade(resetVolume = false, resetPauseAtEnd = true)
    if (isPlayerInitialized() && localPlayer.volume != crossfadeBaseVolume) {
        localPlayer.volume = crossfadeBaseVolume.coerceIn(0f, maxSafeGainFactor)
    }
    scope.launch {
        while (isActive && player.currentMediaItem?.mediaId == outgoingMediaId && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE) {
            delay(50L)
        }
        val targetIndex = resolveCrossfadeTargetIndex(target)
        if (targetIndex != C.INDEX_UNSET &&
            player.playbackState != Player.STATE_IDLE &&
            (player.currentMediaItemIndex == targetIndex ||
                player.currentMediaItem?.mediaId == target.mediaId ||
                player.playbackState == Player.STATE_ENDED)
        ) {
            player.seekTo(targetIndex, 0L)
            player.playbackParameters = userPlaybackSpeed ?: PlaybackParameters.DEFAULT
            if (player.playWhenReady) {
                player.play()
            }
        }
    }
}

internal fun MusicService.startCrossfade(
    target: MusicService.CrossfadeTarget,
    durationMs: Long,
    incomingStartMs: Long = 0L,
    plan: AutomixPlan? = activeAutomixPlan,
    prerollAnchor: PrerollDriftAnchor? = null,
) {
    if (isCrossfading || !crossfadeEnabled) return
    if (secondaryPreparationFailedMediaId == target.mediaId) return

    val incomingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(target.mediaId) else null
    val cueInMs = resolveIncomingCueInMs(plan ?: activeAutomixPlan, incomingAnalysis, incomingStartMs)

    val incomingPlayer = prepareSecondaryCrossfadePlayer(target, cueInMs) ?: return
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
                if (prerollAnchor == null && cueInMs > 0L && incomingPlayer.currentPosition != cueInMs) {
                    incomingPlayer.seekTo(target.index, cueInMs)
                }
                val requiredBufferedMs = requiredCrossfadeStartBufferMs(durationMs)
                if (!awaitCrossfadePlayerReady(incomingPlayer, MusicService.CROSSFADE_READY_TIMEOUT_MS, requiredBufferedMs)) {
                    degradeToHardCut(target, outgoingMediaId)
                    return@launch
                }

                localPlayer.pauseAtEndOfMediaItems = true

                val standbyPlayer = incomingPlayer
                val outgoingPlayer = localPlayer

                outgoingPlayer.volume = (crossfadeBaseVolume * FALL(0f)).coerceIn(0f, maxSafeGainFactor)
                incomingPlayer.volume = (crossfadeIncomingBaseVolume * RISE(0f)).coerceIn(0f, maxSafeGainFactor)
                if (plan?.tier == TransitionTier.SMART_BEATMATCH) {
                    standbyPlayer.playbackParameters = PlaybackParameters(plan.incomingTempoRatio)
                } else {
                    standbyPlayer.playbackParameters = player.playbackParameters
                }
                standbyPlayer.playWhenReady = crossfadePlaybackRequested

                val hasPrerollDriftCorrected = if (prerollAnchor != null) {
                    val primaryAdvance = player.currentPosition - prerollAnchor.primaryStartMs
                    val expectedStandbyAdvance = if (plan?.tier == TransitionTier.SMART_BEATMATCH) {
                        (primaryAdvance * plan.incomingTempoRatio).roundToLong()
                    } else {
                        primaryAdvance
                    }
                    val expectedStandbyPos = prerollAnchor.standbyStartMs + expectedStandbyAdvance
                    val actualStandbyPos = standbyPlayer.currentPosition
                    val driftMs = actualStandbyPos - expectedStandbyPos
                    if (abs(driftMs) <= PlaybackConstants.PREROLL_DRIFT_TOLERANCE_MS) {
                        if (driftMs != 0L) {
                            standbyPlayer.seekTo(target.index, expectedStandbyPos)
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                if (!hasPrerollDriftCorrected) {
                    if (cueInMs > 0L) {
                        if (standbyPlayer.currentPosition != cueInMs) {
                            standbyPlayer.seekTo(target.index, cueInMs)
                        }
                    } else if (standbyPlayer.currentPosition > 0L) {
                        standbyPlayer.seekTo(target.index, 0L)
                    }
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
                                degradeToHardCut(target, outgoingMediaId)
                                return@launch
                            }
                            if (standbyPlayer.playbackState == Player.STATE_IDLE) {
                                standbyPlayer.prepare()
                            }
                            val stallStart = bufferingStartMs ?: nowMs.also { bufferingStartMs = it }
                            if (nowMs - stallStart >= PlaybackConstants.CROSSFADE_BUFFERING_TIMEOUT_MS) {
                                Timber.tag(MusicService.TAG).w("Crossfade incoming player buffering timed out; bailing out")
                                degradeToHardCut(target, outgoingMediaId)
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

                finishCrossfade(target, incomingPlayer, plan)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(MusicService.TAG).w(error, "Crossfade failed")
                degradeToHardCut(target, outgoingMediaId)
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
    plan: AutomixPlan? = activeAutomixPlan,
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
        val userPlaybackSpeed = playerA.playbackParameters.takeIf { it != PlaybackParameters.DEFAULT }
        transferAudioEffects(incomingPlayer)
        incomingPlayer.setShuffleOrder(playerA.shuffleOrder)
        playerA.playWhenReady = false
        playerA.volume = 0f
        playerA.stop()
        playerA.clearMediaItems()
        reserveCrossfadePlayer = playerA
        localPlayer = incomingPlayer

        if (plan?.tier == TransitionTier.SMART_BEATMATCH) {
            localPlayer.playbackParameters = userPlaybackSpeed ?: PlaybackParameters.DEFAULT
        }

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
    hasPreparedSecondaryPlayer = false
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

internal fun isSourceOrEofError(error: PlaybackException): Boolean {
    if (error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) return true
    var throwable: Throwable? = error.cause ?: error
    while (throwable != null) {
        if (throwable is EOFException) return true
        if (throwable is IOException &&
            throwable.message?.contains("unexpected end of stream", ignoreCase = true) == true
        ) return true
        throwable = throwable.cause
    }
    return false
}

internal fun MusicService.cancelSecondaryCrossfadePreparation() {
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
    if (isPlayerInitialized()) {
        dualForwardingPlayer.attachPlayer(localPlayer)
        dualPlayerRoleHolder.reset()
        localPlayer.pauseAtEndOfMediaItems = false
    }
    releaseSecondaryCrossfadePlayer()
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

internal fun MusicService.kickOffUpcomingTrackAnalysis(currentIndex: Int) {
    if (!automixEnabled) return
    val nextIndex = player.nextMediaItemIndex
    if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until player.mediaItemCount) return
    if (player.repeatMode == Player.REPEAT_MODE_ONE || nextIndex == currentIndex) return
    val nextMediaItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull() ?: return
    kickOffTrackAnalysis(nextMediaItem)
}

internal fun isFullyCached(cache: Cache, key: String): Boolean = runCatching {
    val spans = cache.getCachedSpans(key)
    if (spans.isEmpty()) return@runCatching false
    val contentLength = cache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
    contentLength > 0L && cache.isCached(key, 0L, contentLength)
}.getOrDefault(false)

internal fun MusicService.isTrackFullyCached(mediaId: String): Boolean {
    if (mediaId.isBlank()) return false
    val flacKey = flacCacheKey(mediaId)
    return isFullyCached(downloadCache, flacKey) ||
        isFullyCached(downloadCache, mediaId)
}

internal suspend fun MusicService.analyzeCachedTrack(
    mediaId: String,
    durationSeconds: Double? = null,
): TrackAnalysisResult? {
    if (mediaId.isBlank()) return null
    TrackAnalyzer.getCached(mediaId)?.let { return it }

    val flacKey = flacCacheKey(mediaId)
    if (isFullyCached(downloadCache, flacKey)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, flacKey, durationSeconds = durationSeconds)
    }

    if (isFullyCached(downloadCache, mediaId)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, mediaId, durationSeconds = durationSeconds)
    }

    return null
}

internal fun MusicService.registerCacheListenerForKey(key: String) {
    if (key.isBlank()) return
    if (registeredCacheKeys.add(key)) {
        runCatching { downloadCache.addListener(key, automixCacheListener) }
    }
}

internal fun MusicService.registerCacheListenersForMediaItem(mediaItem: MediaItem?) {
    if (mediaItem == null) return
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    if (mediaId.isBlank()) return
    registerCacheListenerForKey(mediaId)
    registerCacheListenerForKey(flacCacheKey(mediaId))
}

internal fun MusicService.checkTrackCacheReadiness(mediaItem: MediaItem?) {
    if (mediaItem == null || !automixEnabled) return
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    if (mediaId.isBlank() || TrackAnalyzer.hasCached(mediaId)) return

    if (isTrackFullyCached(mediaId)) {
        kickOffTrackAnalysis(mediaItem)
    }
}

internal fun MusicService.recheckCacheReadinessForCurrentAndNext() {
    if (!isPlayerInitialized() || !automixEnabled) return
    val currentItem = player.currentMediaItem
    registerCacheListenersForMediaItem(currentItem)
    checkTrackCacheReadiness(currentItem)

    val nextIndex = player.nextMediaItemIndex
    if (nextIndex != C.INDEX_UNSET &&
        nextIndex in 0 until player.mediaItemCount &&
        player.repeatMode != Player.REPEAT_MODE_ONE &&
        nextIndex != player.currentMediaItemIndex
    ) {
        val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull()
        registerCacheListenersForMediaItem(nextItem)
        checkTrackCacheReadiness(nextItem)
    }
}

internal fun MusicService.unregisterAllCacheListeners() {
    for (key in registeredCacheKeys) {
        runCatching { playerCache.removeListener(key, automixCacheListener) }
        runCatching { downloadCache.removeListener(key, automixCacheListener) }
    }
    registeredCacheKeys.clear()
}

internal fun MusicService.kickOffTrackAnalysis(mediaItem: MediaItem?) {
    if (mediaItem == null) {
        Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis skip: mediaItem=null")
        return
    }
    if (!automixEnabled) {
        Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis skip: automix disabled")
        return
    }
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    kickOffTrackAnalysis(mediaId, mediaItem)
}

internal fun MusicService.kickOffTrackAnalysis(mediaId: String, mediaItem: MediaItem? = null) {
    if (mediaId.isBlank()) {
        Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis skip: blank mediaId")
        return
    }
    if (!automixEnabled) {
        Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis skip: automix disabled mediaId=$mediaId")
        return
    }

    val service = this
    ioScope.launch {
        try {
            val cached = TrackAnalyzer.getOrFetchCached(mediaId)
            if (cached != null) {
                Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis ready from cache/Room: mediaId=$mediaId bpm=${cached.bpm}")
                return@launch
            }

            val durationSeconds = mediaItem?.metadata?.duration?.takeIf { it > 0 }?.toDouble()

            if (mediaId.isLocalMediaId()) {
                val uri = mediaItem?.localConfiguration?.uri ?: mediaId.toUri()
                val scheme = uri.scheme?.lowercase()
                if (scheme == "content" || scheme == "file" || scheme == "android.resource") {
                    TrackAnalyzer.analyze(mediaId, service, uri, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            if (mediaId.startsWith("/")) {
                val file = File(mediaId)
                if (file.exists() && file.canRead()) {
                    TrackAnalyzer.analyze(mediaId, file, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            val directUri = mediaItem?.localConfiguration?.uri
            if (directUri != null) {
                val scheme = directUri.scheme?.lowercase()
                if (scheme == "content" || scheme == "file" || scheme == "android.resource") {
                    TrackAnalyzer.analyze(mediaId, service, directUri, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            if (isTrackFullyCached(mediaId)) {
                Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis start: offline downloaded track mediaId=$mediaId")
                analyzeCachedTrack(mediaId, durationSeconds = durationSeconds)
                return@launch
            }

            Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis skip: network track without full local copy mediaId=$mediaId")
        } catch (e: Exception) {
            Timber.tag(MusicService.TAG).v(e, "Background TrackAnalyzer failed for mediaId=$mediaId")
        }
    }
}
