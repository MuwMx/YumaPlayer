/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.listeners

import moe.rukamori.archivetune.together.ControlAction

data class TogetherEchoSnapshot(
    val isApplyingRemote: Boolean,
    val suppressEchoUntilElapsedMs: Long = 0L,
    val lastRemoteAppliedIndex: Int = -1,
    val lastRemoteAppliedPlayWhenReady: Boolean? = null,
)

object TogetherPlaybackEchoPolicy {
    fun isTransitionEcho(
        snapshot: TogetherEchoSnapshot,
        currentIndex: Int,
        nowElapsedMs: Long,
    ): Boolean =
        snapshot.isApplyingRemote ||
            (nowElapsedMs < snapshot.suppressEchoUntilElapsedMs && snapshot.lastRemoteAppliedIndex == currentIndex)

    fun isTransitionEcho(
        isApplyingRemote: Boolean,
        suppressEchoUntilElapsedMs: Long,
        lastRemoteAppliedIndex: Int,
        currentIndex: Int,
        nowElapsedMs: Long,
    ): Boolean =
        isApplyingRemote ||
            (nowElapsedMs < suppressEchoUntilElapsedMs && lastRemoteAppliedIndex == currentIndex)

    fun isPlayWhenReadyEcho(
        snapshot: TogetherEchoSnapshot,
        playWhenReady: Boolean,
        nowElapsedMs: Long,
    ): Boolean =
        snapshot.isApplyingRemote ||
            (
                nowElapsedMs < snapshot.suppressEchoUntilElapsedMs &&
                    snapshot.lastRemoteAppliedPlayWhenReady != null &&
                    snapshot.lastRemoteAppliedPlayWhenReady == playWhenReady
            )

    fun isPlayWhenReadyEcho(
        isApplyingRemote: Boolean,
        suppressEchoUntilElapsedMs: Long,
        lastRemoteAppliedPlayWhenReady: Boolean?,
        playWhenReady: Boolean,
        nowElapsedMs: Long,
    ): Boolean =
        isApplyingRemote ||
            (
                nowElapsedMs < suppressEchoUntilElapsedMs &&
                    lastRemoteAppliedPlayWhenReady != null &&
                    lastRemoteAppliedPlayWhenReady == playWhenReady
            )

    fun isRemoteApplyingEcho(snapshot: TogetherEchoSnapshot): Boolean =
        snapshot.isApplyingRemote

    fun isRemoteApplyingEcho(isApplyingRemote: Boolean): Boolean =
        isApplyingRemote

    fun buildSeekAction(
        trackId: String?,
        index: Int,
        positionMs: Long,
    ): ControlAction {
        val cleanTrackId = trackId?.trim().orEmpty()
        val safePositionMs = positionMs.coerceAtLeast(0L)
        return if (cleanTrackId.isBlank()) {
            ControlAction.SeekToIndex(
                index = index,
                positionMs = safePositionMs,
            )
        } else {
            ControlAction.SeekToTrack(
                trackId = cleanTrackId,
                positionMs = safePositionMs,
            )
        }
    }

    fun buildPlayPauseAction(playWhenReady: Boolean): ControlAction =
        if (playWhenReady) {
            ControlAction.Play
        } else {
            ControlAction.Pause
        }

    fun buildShuffleAction(shuffleEnabled: Boolean): ControlAction =
        ControlAction.SetShuffleEnabled(shuffleEnabled = shuffleEnabled)

    fun buildRepeatAction(repeatMode: Int): ControlAction =
        ControlAction.SetRepeatMode(repeatMode = repeatMode)
}
