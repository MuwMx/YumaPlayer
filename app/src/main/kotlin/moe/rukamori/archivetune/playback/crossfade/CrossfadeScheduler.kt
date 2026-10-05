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
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.TransitionPlanner
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.bumpCrossfadePlanGeneration
import moe.rukamori.archivetune.playback.crossfadePlanGeneration
import moe.rukamori.archivetune.playback.effectiveCrossfadeDuration
import moe.rukamori.archivetune.playback.isPlayerInitialized
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

private const val FAST_ANALYSIS_TIMEOUT_MS = 500L

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
                    delay(sleepMs.milliseconds)
                }
            } finally {
                if (hasPrimedIncomingPlayer && !handedOffToCrossfade) {
                    controller.stopIncoming()
                }
            }
        }
}
