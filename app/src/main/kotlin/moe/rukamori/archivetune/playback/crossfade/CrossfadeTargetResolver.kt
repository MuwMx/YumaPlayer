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
import kotlin.math.roundToLong
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.audiodsp.TransitionPlanner
import moe.rukamori.archivetune.audiodsp.advanceCueForElapsed
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.crossfadePlanGeneration
import moe.rukamori.archivetune.playback.isGaplessAlbumTransition

internal fun MusicService.isPlanGenerationCurrent(
    generation: Long,
    currentMediaId: String,
    targetMediaId: String,
    startPositionMs: Long,
): Boolean {
    if (crossfadePlanGeneration.get() != generation) return false
    if (player.currentMediaItem?.mediaId != currentMediaId) return false
    val currentTarget = resolveCrossfadeTarget() ?: return false
    if (currentTarget.mediaId != targetMediaId) return false
    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return false
    if (!player.playWhenReady) return false
    if (player.currentPosition < startPositionMs) return false
    return true
}

internal fun resolveCrossfadeTriggerAt(
    automixEnabled: Boolean,
    outgoingAnalysis: TrackAnalysisResult?,
    durationMs: Long,
    triggerOffsetMs: Long,
    planTriggerAtMs: Long?,
): Long {
    if (automixEnabled && planTriggerAtMs != null) {
        return planTriggerAtMs
    }
    val contentEndMs = TransitionPlanner.resolveTrustedContentEndMs(
        reportedContentEndMs = outgoingAnalysis?.contentEndTime
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1000.0).roundToLong() }
            ?: 0L,
        currentDurationMs = durationMs,
    )
    val mixOutMs = outgoingAnalysis?.mixOutTime
        ?.takeIf { it > 0.0 }
        ?.let { (it * 1000.0).roundToLong() }
        ?.let { TransitionPlanner.sanitizeOutroMs(it, contentEndMs) }
        ?.coerceAtMost(durationMs)
    return if (automixEnabled && mixOutMs != null) {
        mixOutMs
    } else {
        contentEndMs - triggerOffsetMs
    }
}

internal fun MusicService.computeCrossfadeTriggerAt(
    outgoingAnalysis: TrackAnalysisResult?,
    duration: Long,
    triggerOffset: Long,
    automixPlanTriggerAtMs: Long?,
): Long = resolveCrossfadeTriggerAt(
    automixEnabled = automixEnabled,
    outgoingAnalysis = outgoingAnalysis,
    durationMs = duration,
    triggerOffsetMs = triggerOffset,
    planTriggerAtMs = automixPlanTriggerAtMs,
)

internal fun Player.audioTrackDescription(): String {
    val audioFormat = currentTracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        ?.getTrackFormat(0)
        ?: return "audio=none"
    return "audio=${audioFormat.sampleRate}Hz/${audioFormat.channelCount}ch"
}

internal fun resolveIncomingCueInMs(
    automixPlan: AutomixPlan?,
    incomingAnalysis: TrackAnalysisResult?,
    explicitStartMs: Long = 0L,
): Long {
    if (explicitStartMs > 0L) return explicitStartMs
    val planStart = automixPlan?.incomingStartMs ?: 0L
    if (planStart > 0L) return planStart
    val mixInSec = incomingAnalysis?.mixInTime ?: 0.0
    return if (mixInSec > 0.0) (mixInSec * 1000.0).roundToLong() else 0L
}

internal fun MusicService.resolveCrossfadeTarget(): CrossfadeTarget? {
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

    return CrossfadeTarget(
        index = targetIndex,
        mediaId = targetItem.mediaId,
    )
}

internal fun advanceCueForLateStart(
    incomingPlayer: ExoPlayer,
    cueInMs: Long,
    elapsedBeforeStartMs: Long,
): Long {
    if (cueInMs <= 0L || elapsedBeforeStartMs <= 0L) return cueInMs
    val incomingDuration = incomingPlayer.duration
    val maxPositionMs = if (incomingDuration != C.TIME_UNSET && incomingDuration > 0L) {
        incomingDuration - MusicService.CROSSFADE_END_GUARD_MS
    } else {
        null
    }
    return advanceCueForElapsed(cueInMs, elapsedBeforeStartMs, maxPositionMs)
}

internal fun MusicService.resolveCrossfadeTargetIndex(target: CrossfadeTarget): Int {
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
