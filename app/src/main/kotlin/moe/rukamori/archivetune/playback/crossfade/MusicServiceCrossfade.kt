/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.atomic.AtomicLong
import moe.rukamori.archivetune.audiodsp.AutomixPlan
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.playback.crossfade.analyzeCachedTrack as analyzeCachedTrackImpl
import moe.rukamori.archivetune.playback.crossfade.awaitCrossfadePlayerReady as awaitCrossfadePlayerReadyImpl
import moe.rukamori.archivetune.playback.crossfade.awaitPrimaryCrossfadeHandoffReady as awaitPrimaryCrossfadeHandoffReadyImpl
import moe.rukamori.archivetune.playback.crossfade.canHandoffWithoutRebuffer as canHandoffWithoutRebufferImpl
import moe.rukamori.archivetune.playback.crossfade.cancelCrossfade as cancelCrossfadeImpl
import moe.rukamori.archivetune.playback.crossfade.cancelSecondaryCrossfadePreparation as cancelSecondaryCrossfadePreparationImpl
import moe.rukamori.archivetune.playback.crossfade.createSecondaryCrossfadePlayer as createSecondaryCrossfadePlayerImpl
import moe.rukamori.archivetune.playback.crossfade.finishCrossfade as finishCrossfadeImpl
import moe.rukamori.archivetune.playback.crossfade.hasBufferedForSmoothStart as hasBufferedForSmoothStartImpl
import moe.rukamori.archivetune.playback.crossfade.isSourceOrEofError as isSourceOrEofErrorImpl
import moe.rukamori.archivetune.playback.crossfade.prepareSecondaryCrossfadePlayer as prepareSecondaryCrossfadePlayerImpl
import moe.rukamori.archivetune.playback.crossfade.releaseSecondaryCrossfadePlayer as releaseSecondaryCrossfadePlayerImpl
import moe.rukamori.archivetune.playback.crossfade.scheduleCrossfade as scheduleCrossfadeImpl
import moe.rukamori.archivetune.playback.crossfade.startCrossfade as startCrossfadeImpl

internal val crossfadePlanGeneration = AtomicLong(0L)
internal fun bumpCrossfadePlanGeneration(): Long = crossfadePlanGeneration.incrementAndGet()

internal fun MusicService.scheduleCrossfade() = scheduleCrossfadeImpl()
internal fun MusicService.prepareSecondaryCrossfadePlayer(target: CrossfadeTarget, startPositionMs: Long = 0L): ExoPlayer? =
    prepareSecondaryCrossfadePlayerImpl(target, startPositionMs)
internal fun MusicService.createSecondaryCrossfadePlayer(): ExoPlayer = createSecondaryCrossfadePlayerImpl()
internal fun MusicService.startCrossfade(
    target: CrossfadeTarget,
    durationMs: Long,
    incomingStartMs: Long = 0L,
    plan: AutomixPlan? = activeAutomixPlan,
    triggerAtMs: Long? = null,
) = startCrossfadeImpl(target, durationMs, incomingStartMs, plan, triggerAtMs)
internal suspend fun MusicService.awaitCrossfadePlayerReady(
    crossfadePlayer: ExoPlayer,
    timeoutMs: Long,
    minimumBufferedMs: Long,
): Boolean = awaitCrossfadePlayerReadyImpl(crossfadePlayer, timeoutMs, minimumBufferedMs)
internal suspend fun MusicService.finishCrossfade(
    target: CrossfadeTarget,
    incomingPlayer: ExoPlayer,
    plan: AutomixPlan? = activeAutomixPlan,
) = finishCrossfadeImpl(target, incomingPlayer, plan)
internal suspend fun MusicService.awaitPrimaryCrossfadeHandoffReady(incomingPlayer: ExoPlayer): Boolean =
    awaitPrimaryCrossfadeHandoffReadyImpl(incomingPlayer)
internal fun MusicService.canHandoffWithoutRebuffer(incomingPlayer: ExoPlayer): Boolean =
    canHandoffWithoutRebufferImpl(incomingPlayer)
internal fun MusicService.hasBufferedForSmoothStart(targetPlayer: ExoPlayer, minimumBufferedMs: Long): Boolean =
    hasBufferedForSmoothStartImpl(targetPlayer, minimumBufferedMs)
internal fun MusicService.cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
    cancelCrossfadeImpl(resetVolume, resetPauseAtEnd)
internal fun isSourceOrEofError(error: PlaybackException): Boolean = isSourceOrEofErrorImpl(error)
internal fun MusicService.cancelSecondaryCrossfadePreparation() = cancelSecondaryCrossfadePreparationImpl()
internal fun MusicService.releaseSecondaryCrossfadePlayer() = releaseSecondaryCrossfadePlayerImpl()
internal suspend fun MusicService.analyzeCachedTrack(mediaId: String, durationSeconds: Double? = null): TrackAnalysisResult? =
    analyzeCachedTrackImpl(mediaId, durationSeconds)
