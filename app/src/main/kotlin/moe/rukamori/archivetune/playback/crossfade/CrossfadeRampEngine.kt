/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.crossfade

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import java.io.EOFException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.boundedFoldMs
import moe.rukamori.archivetune.audiodsp.fadeProgress
import moe.rukamori.archivetune.playback.ExoDeckController
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.bumpCrossfadePlanGeneration
import moe.rukamori.archivetune.playback.currentEffectivePlayerVolume
import moe.rukamori.archivetune.playback.currentEffectivePlayerVolumeForMediaId
import moe.rukamori.archivetune.playback.requiredCrossfadeStartBufferMs
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import timber.log.Timber

private const val SYNC_LOG_PERIOD_MS = 400L

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
