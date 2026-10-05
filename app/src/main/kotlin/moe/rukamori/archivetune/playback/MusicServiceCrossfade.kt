/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMx> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.audiodsp.TransitionPlanner
import moe.rukamori.archivetune.audiodsp.TransitionTier
import moe.rukamori.archivetune.audiodsp.boundedFoldMs
import moe.rukamori.archivetune.audiodsp.fadeProgress
import moe.rukamori.archivetune.playback.crossfade.advanceCueForLateStart
import moe.rukamori.archivetune.playback.crossfade.audioTrackDescription
import moe.rukamori.archivetune.playback.crossfade.computeCrossfadeTriggerAt
import moe.rukamori.archivetune.playback.crossfade.isFullyCached
import moe.rukamori.archivetune.playback.crossfade.isPlanGenerationCurrent
import moe.rukamori.archivetune.playback.crossfade.isTrackFullyCached
import moe.rukamori.archivetune.playback.crossfade.kickOffTrackAnalysis
import moe.rukamori.archivetune.playback.crossfade.resolveCrossfadeTarget
import moe.rukamori.archivetune.playback.crossfade.resolveCrossfadeTargetIndex
import moe.rukamori.archivetune.playback.crossfade.resolveCrossfadeTriggerAt
import moe.rukamori.archivetune.playback.crossfade.resolveIncomingCueInMs
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import timber.log.Timber

private const val SYNC_LOG_PERIOD_MS = 400L

private const val FAST_ANALYSIS_TIMEOUT_MS = 500L

internal val crossfadePlanGeneration = AtomicLong(0L)

internal fun bumpCrossfadePlanGeneration(): Long = crossfadePlanGeneration.incrementAndGet()

internal fun MusicService.scheduleCrossfade() {
    if (!isPlayerInitialized()) return
    if (isCrossfading) {
        Timber.tag("MusicServiceCrossfade").d("scheduleCrossfade bail: already crossfading")
        return
    }
    if (!player.playWhenReady) {
        Timber.tag("MusicServiceCrossfade").d("scheduleCrossfade bail: not playWhenReady")
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        return
    }

    val earlyTarget = resolveCrossfadeTarget()
    val earlyMediaId = player.currentMediaItem?.mediaId
    if (earlyTarget != null && earlyMediaId != null &&
        crossfadeTriggerJob?.isActive == true &&
        activeCrossfadeScheduledKey == (earlyMediaId to earlyTarget)
    ) {
        return
    }

    val currentGeneration = bumpCrossfadePlanGeneration()
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    activeCrossfadeScheduledKey = if (earlyTarget != null && earlyMediaId != null) {
        earlyMediaId to earlyTarget
    } else {
        null
    }

    val target = resolveCrossfadeTarget()
    val duration = player.duration
    val effectiveDuration = effectiveCrossfadeDuration(duration)
    if (target == null || effectiveDuration == null) {
        Timber.tag("MusicServiceCrossfade").d("scheduleCrossfade bail: target=${target?.mediaId} effectiveDuration=$effectiveDuration duration=$duration items=${player.mediaItemCount} state=${player.playbackState}")
        localPlayer.pauseAtEndOfMediaItems = false
        releaseSecondaryCrossfadePlayer()
        activeAutomixPlan = null
        activeCrossfadeScheduledKey = null
        return
    }
    Timber.tag("MusicServiceCrossfade").d("scheduleCrossfade: enabled=$crossfadeEnabled, durationMs=$crossfadeDurationMs, automix=$automixEnabled")
    Timber.tag("MusicServiceCrossfade").d("scheduleCrossfade armed for target: ${target.mediaId} at index ${target.index}")

    val currentMediaId = player.currentMediaItem?.mediaId ?: return
    val currentIndex = player.currentMediaItemIndex

    if (automixEnabled) {
        kickOffTrackAnalysis(player.currentMediaItem)
        kickOffTrackAnalysis(runCatching { player.getMediaItemAt(target.index) }.getOrNull())
    }

    var outgoingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(currentMediaId) else null
    var incomingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(target.mediaId) else null
    val automixAggr = CrossfadeConstants.Aggressiveness.STANDARD.name.lowercase()
    val resolvedPreferred = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration)

    var automixPlan = if (automixEnabled && outgoingAnalysis != null) {
        TransitionPlanner.planSmartTransition(
            outgoingAnalysis = outgoingAnalysis,
            incomingAnalysis = incomingAnalysis,
            currentDurationMs = duration,
            preferredDurationMs = resolvedPreferred,
            aggressiveness = automixAggr,
            currentPositionMs = player.currentPosition,
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
    var prepareAhead = automixPlan?.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS

    activeCrossfadeScheduledKey = currentMediaId to target
    crossfadeTriggerJob =
        scope.launch {
            hasPreparedSecondaryPlayer = false
            var hasPrimedIncomingPlayer = false
            var isPlanFrozen = false
            var handedOffToCrossfade = false
            try {
                while (isActive) {
                    if (!crossfadeEnabled || isCrossfading) return@launch
                    if (crossfadePlanGeneration.get() != currentGeneration) return@launch
                    if (player.currentMediaItem?.mediaId != currentMediaId || player.currentMediaItemIndex != currentIndex) {
                        return@launch
                    }
                    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                        return@launch
                    }
                    if (!player.playWhenReady) {
                        return@launch
                    }

                    if (automixEnabled && !isPlanFrozen && (outgoingAnalysis == null || (incomingAnalysis == null && (automixPlan?.incomingStartMs ?: 0L) == 0L))) {
                        val recomputeStartGeneration = crossfadePlanGeneration.get()
                        val recomputeStartPosition = player.currentPosition
                        val latestOutgoing = TrackAnalyzer.getCached(currentMediaId)
                        val latestIncoming = TrackAnalyzer.getCached(target.mediaId)
                        if (latestOutgoing != null && latestOutgoing != outgoingAnalysis) {
                            val newPlan = TransitionPlanner.planSmartTransition(
                                outgoingAnalysis = latestOutgoing,
                                incomingAnalysis = latestIncoming,
                                currentDurationMs = duration,
                                preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                aggressiveness = automixAggr,
                                currentPositionMs = recomputeStartPosition,
                            )
                            if (!isPlanFrozen && isPlanGenerationCurrent(recomputeStartGeneration, currentMediaId, target.mediaId, recomputeStartPosition)) {
                                outgoingAnalysis = latestOutgoing
                                incomingAnalysis = latestIncoming
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
                                prepareAhead = newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS
                            }
                        } else {
                            val currentOutgoing = outgoingAnalysis
                            if (latestIncoming != null && latestIncoming != incomingAnalysis && currentOutgoing != null) {
                                val newPlan = TransitionPlanner.planSmartTransition(
                                    outgoingAnalysis = currentOutgoing,
                                    incomingAnalysis = latestIncoming,
                                    currentDurationMs = duration,
                                    preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                    aggressiveness = automixAggr,
                                    currentPositionMs = recomputeStartPosition,
                                )
                                if (!isPlanFrozen && isPlanGenerationCurrent(recomputeStartGeneration, currentMediaId, target.mediaId, recomputeStartPosition)) {
                                    incomingAnalysis = latestIncoming
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
                                    prepareAhead = newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS
                                }
                            }
                        }
                    }

                    val remainingToTrigger = triggerAt - player.currentPosition
                    val incomingStartMs = resolveIncomingCueInMs(automixPlan, incomingAnalysis)
                    val secondaryFailedForThisCycle = secondaryPreparationFailedMediaId == target.mediaId
                    if (!hasPreparedSecondaryPlayer && !secondaryFailedForThisCycle && remainingToTrigger <= prepareAhead) {
                        prepareSecondaryCrossfadePlayer(target, incomingStartMs)
                        hasPreparedSecondaryPlayer = true
                        isPlanFrozen = true
                    }
                    if (hasPreparedSecondaryPlayer && !hasPrimedIncomingPlayer && player.playWhenReady &&
                        remainingToTrigger <= CrossfadeConstants.PRIME_LEAD_MS
                    ) {
                        controller.primeIncoming(incomingStartMs)
                        hasPrimedIncomingPlayer = true
                    }
                    if (remainingToTrigger <= prepareAhead) {
                        isPlanFrozen = true
                    }

                    if (remainingToTrigger <= 0L) {
                        if (secondaryPreparationFailedMediaId == target.mediaId) {
                            return@launch
                        }
                        if (automixEnabled && outgoingAnalysis == null && isTrackFullyCached(currentMediaId)) {
                            val currentDurationSec = if (duration > 0L && duration != C.TIME_UNSET) duration.toDouble() / 1000.0 else null
                            val recomputeStartGeneration = crossfadePlanGeneration.get()
                            val recomputeStartPosition = player.currentPosition
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
                            if (!player.playWhenReady || crossfadePlanGeneration.get() != currentGeneration) {
                                return@launch
                            }

                            if (fastAnalysis != null) {
                                if (isPlanGenerationCurrent(recomputeStartGeneration, currentMediaId, target.mediaId, recomputeStartPosition)) {
                                    outgoingAnalysis = fastAnalysis
                                    incomingAnalysis = TrackAnalyzer.getCached(target.mediaId)
                                    val newPlan = TransitionPlanner.planSmartTransition(
                                        outgoingAnalysis = fastAnalysis,
                                        incomingAnalysis = incomingAnalysis,
                                        currentDurationMs = duration,
                                        preferredDurationMs = TransitionPlanner.resolvePreferredDurationMs(automixTransitionPreset, effectiveDuration),
                                        aggressiveness = automixAggr,
                                        currentPositionMs = recomputeStartPosition,
                                    )
                                    automixPlan = newPlan
                                    activeAutomixPlan = newPlan
                                    plannedDuration = newPlan.durationMs
                                    triggerOffset = newPlan.triggerOffsetMs
                                    prepareAhead = newPlan.prepareAheadMs ?: MusicService.CROSSFADE_PREPARE_AHEAD_MS
                                }
                            }
                        }

                        val currentOutgoing = outgoingAnalysis
                        val endLimit = if (currentOutgoing != null && currentOutgoing.contentEndTime > 0.0) {
                            (currentOutgoing.contentEndTime * 1000.0).roundToLong()
                        } else {
                            duration
                        }
                        val adjustedDuration =
                            (endLimit - player.currentPosition - MusicService.CROSSFADE_END_GUARD_MS)
                                .coerceAtMost(plannedDuration)
                        val finalIncomingStartMs = resolveIncomingCueInMs(automixPlan, incomingAnalysis, incomingStartMs)
                        localPlayer.pauseAtEndOfMediaItems = false
                        handedOffToCrossfade = true
                        val fadeDuration = adjustedDuration.coerceAtLeast(MusicService.MIN_CROSSFADE_DURATION_MS)
                        startCrossfade(target, fadeDuration, finalIncomingStartMs, automixPlan, triggerAt)
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
            } finally {
                if (hasPrimedIncomingPlayer && !handedOffToCrossfade) {
                    controller.stopIncoming()
                }
            }
        }
}

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

internal fun MusicService.startCrossfade(
    target: CrossfadeTarget,
    durationMs: Long,
    incomingStartMs: Long = 0L,
    plan: AutomixPlan? = activeAutomixPlan,
    triggerAtMs: Long? = null,
) {
    if (isCrossfading || !crossfadeEnabled) return
    if (secondaryPreparationFailedMediaId == target.mediaId) return

    val incomingAnalysis = if (automixEnabled) TrackAnalyzer.getCached(target.mediaId) else null
    val cueInMs = resolveIncomingCueInMs(plan ?: activeAutomixPlan, incomingAnalysis, incomingStartMs)

    val incomingPlayer = prepareSecondaryCrossfadePlayer(target, cueInMs) ?: return
    val outgoingMediaId = player.currentMediaItem?.mediaId ?: return

    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    activeCrossfadeScheduledKey = null
    crossfadeJob?.cancel()
    crossfadeJob =
        scope.launch {
            isCrossfading = true
            crossfadeProgress = 0f
            crossfadeBaseVolume = currentEffectivePlayerVolume()
            crossfadeIncomingBaseVolume = currentEffectivePlayerVolumeForMediaId(target.mediaId)
            crossfadePlaybackRequested = player.playWhenReady

            try {
                if (cueInMs > 0L && incomingPlayer.currentPosition != cueInMs) {
                    incomingPlayer.seekTo(target.index, cueInMs)
                }
                val requiredBufferedMs = requiredCrossfadeStartBufferMs(durationMs)
                if (!awaitCrossfadePlayerReady(incomingPlayer, MusicService.CROSSFADE_READY_TIMEOUT_MS, requiredBufferedMs)) {
                    cancelCrossfade(resetVolume = true, resetPauseAtEnd = false)
                    return@launch
                }

                val effectivePlan = plan ?: AutomixPlan(
                    triggerOffsetMs = 0L,
                    durationMs = durationMs,
                    incomingStartMs = cueInMs,
                    enableBassSwap = false,
                )
                controller.startCrossfade(effectivePlan)
                val standbyPlayer = incomingPlayer

                val nowMs = android.os.SystemClock.elapsedRealtime()
                val latenessMs = triggerAtMs?.takeIf { it > 0L }
                    ?.let { player.currentPosition - it } ?: 0L
                val elapsedBeforeStartMs = boundedFoldMs(latenessMs, durationMs)
                val effectiveCueMs = advanceCueForLateStart(standbyPlayer, cueInMs, elapsedBeforeStartMs)

                if (effectiveCueMs > 0L) {
                    val driftMs = (standbyPlayer.currentPosition - effectiveCueMs).let { if (it < 0L) -it else it }
                    if (driftMs > CrossfadeConstants.PRIME_MAX_DRIFT_MS) {
                        Timber.tag("MusicServiceCrossfade").d("Incoming cue drift ${driftMs}ms over tolerance, seeking")
                        standbyPlayer.seekTo(target.index, effectiveCueMs)
                    }
                } else if (standbyPlayer.currentPosition > CrossfadeConstants.PRIME_MAX_DRIFT_MS) {
                    standbyPlayer.seekTo(target.index, 0L)
                }
                val outDurationMs = runCatching { player.duration }.getOrDefault(-1L)
                val outPositionMs = runCatching { player.currentPosition }.getOrDefault(-1L)
                val outRemainingMs = if (outDurationMs > 0L && outPositionMs >= 0L) {
                    outDurationMs - outPositionMs
                } else {
                    -1L
                }
                Timber.tag("MusicServiceCrossfade").d(
                    "Fade start: foldMs=$elapsedBeforeStartMs cueMs=$effectiveCueMs" +
                        " outPos=$outPositionMs outDuration=$outDurationMs outRemaining=$outRemainingMs" +
                        " pauseAtEnd=${localPlayer.pauseAtEndOfMediaItems} plannedMs=$durationMs" +
                        " outgoing=${player.audioTrackDescription()} incoming=${standbyPlayer.audioTrackDescription()}",
                )
                if (crossfadePlaybackRequested) {
                    standbyPlayer.play()
                }

                var elapsedMs = elapsedBeforeStartMs
                var lastTickMs = nowMs
                var bufferingStartMs: Long? = null
                var stallWarnedForEpisode = false
                var stallEpisodes = 0
                var stalledTotalMs = 0L
                var maxTickGapMs = 0L
                var lastSyncLogMs = 0L
                var outgoingEndedLogged = false
                val fadeStartedMs = nowMs
                while (isActive && elapsedMs < durationMs) {
                    if (!outgoingEndedLogged && player.playbackState == Player.STATE_ENDED) {
                        outgoingEndedLogged = true
                        Timber.tag("MusicServiceCrossfade").w(
                            "Outgoing reached STATE_ENDED mid-fade at p=${"%.2f".format(crossfadeProgress)} " +
                                "elapsedMs=$elapsedMs of $durationMs volume=${player.volume}",
                        )
                    }
                    if (player.currentMediaItem?.mediaId != outgoingMediaId) {
                        cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                        return@launch
                    }

                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    maxTickGapMs = maxOf(maxTickGapMs, nowMs - lastTickMs)
                    if (crossfadePlaybackRequested) {
                        standbyPlayer.playWhenReady = true
                        if (standbyPlayer.playerError != null || standbyPlayer.playbackState == Player.STATE_ENDED) {
                            cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
                            return@launch
                        }
                        if (standbyPlayer.playbackState == Player.STATE_IDLE) {
                            standbyPlayer.prepare()
                        }
                        val incomingProducing = standbyPlayer.playbackState == Player.STATE_READY && standbyPlayer.isPlaying
                        if (incomingProducing) {
                            bufferingStartMs = null
                            stallWarnedForEpisode = false
                            elapsedMs = (elapsedMs + (nowMs - lastTickMs)).coerceAtMost(durationMs)
                            crossfadeProgress = fadeProgress(elapsedMs, elapsedBeforeStartMs, durationMs)
                            activeDeck.applyAutomation(crossfadeProgress, effectivePlan)
                            transitionDeck?.applyAutomation(crossfadeProgress, effectivePlan)
                            if (nowMs - lastSyncLogMs >= SYNC_LOG_PERIOD_MS) {
                                lastSyncLogMs = nowMs
                                val outPos = runCatching { player.currentPosition }.getOrDefault(-1L)
                                val inPos = runCatching { standbyPlayer.currentPosition }.getOrDefault(-1L)
                                val outVol = activeDeck.player.volume
                                val inVol = transitionDeck?.player?.volume ?: 0f
                                val outDuck = activeDeck.djFilter.gain
                                val inDuck = transitionDeck?.djFilter?.gain ?: 1.0
                                val sum = outVol * outDuck + inVol * inDuck
                                Timber.tag("MusicServiceCrossfade").d(
                                    "Sync p=${"%.2f".format(crossfadeProgress)} out=$outPos in=$inPos delta=${inPos - outPos}" +
                                        " outVol=${"%.3f".format(outVol)} inVol=${"%.3f".format(inVol)}" +
                                        " outDuck=${"%.3f".format(outDuck)} inDuck=${"%.3f".format(inDuck)}" +
                                        " sum=${"%.3f".format(sum)}",
                                )
                            }
                        } else {
                            val stallStart = bufferingStartMs ?: nowMs.also {
                                bufferingStartMs = it
                                stallEpisodes++
                            }
                            stalledTotalMs += nowMs - lastTickMs
                            if (!stallWarnedForEpisode) {
                                stallWarnedForEpisode = true
                                Timber.tag("MusicServiceCrossfade").w(
                                    "Incoming stalled mid-fade at p=%.2f state=%d error=%s; holding progress",
                                    crossfadeProgress,
                                    standbyPlayer.playbackState,
                                    standbyPlayer.playerError?.errorCodeName,
                                )
                            }
                            if (nowMs - stallStart >= MusicService.CROSSFADE_BUFFERING_TIMEOUT_MS) {
                                Timber.tag("MusicServiceCrossfade").w("Incoming stalled for the whole timeout; cancelling crossfade")
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
                Timber.tag("MusicServiceCrossfade").d(
                    "Crossfade closed: plannedMs=$durationMs wallMs=${android.os.SystemClock.elapsedRealtime() - fadeStartedMs}" +
                        " maxTickGapMs=$maxTickGapMs stallEpisodes=$stallEpisodes stalledMs=$stalledTotalMs",
                )

                finishCrossfade(target, incomingPlayer, plan)
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

internal fun MusicService.cancelCrossfade(
    resetVolume: Boolean,
    resetPauseAtEnd: Boolean,
) {
    bumpCrossfadePlanGeneration()
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    activeCrossfadeScheduledKey = null
    crossfadeJob?.cancel()
    crossfadeJob = null
    isCrossfading = false
    crossfadeHandoffInProgress = false
    activeAutomixPlan = null
    crossfadeProgress = 0f
    crossfadeIncomingBaseVolume = 1f
    crossfadePlaybackRequested = false
    hasPreparedSecondaryPlayer = false
    if (isControllerInitialized) {
        (controller as? ExoDeckController)?.cancel(resetVolume = resetVolume, resetPauseAtEnd = resetPauseAtEnd)
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

