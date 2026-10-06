package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.audiodsp.CrossfadeConstants

object PlaybackConstants {
    internal fun shouldStopServiceOnTaskRemoved(
        stopMusicOnTaskClearEnabled: Boolean,
        isHostSessionActive: Boolean,
        isPlaybackInactive: Boolean,
    ): Boolean = (isHostSessionActive && isPlaybackInactive) || stopMusicOnTaskClearEnabled

    const val CHUNK_LENGTH = 8 * 1024 * 1024L

    @Deprecated("Use CrossfadeConstants.MIN_FADE_MS directly", ReplaceWith("CrossfadeConstants.MIN_FADE_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
    const val MIN_CROSSFADE_DURATION_MS = CrossfadeConstants.MIN_FADE_MS

    @Deprecated("Use CrossfadeConstants.END_GUARD_MS directly", ReplaceWith("CrossfadeConstants.END_GUARD_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
    const val CROSSFADE_END_GUARD_MS = CrossfadeConstants.END_GUARD_MS
    const val CROSSFADE_PREPARE_AHEAD_MS = 30_000L
    const val CROSSFADE_READY_TIMEOUT_MS = 5_000L
    const val CROSSFADE_HANDOFF_READY_TIMEOUT_MS = 5_000L
    const val CROSSFADE_HANDOFF_BUFFER_MS = 5_000L
    const val CROSSFADE_HANDOFF_SEEK_GUARD_MS = 750L
    const val CROSSFADE_MIN_BUFFER_BEFORE_START_MS = 2_000L
    const val CROSSFADE_MAX_BUFFER_BEFORE_START_MS = 2_000L
    const val CROSSFADE_BUFFERING_TIMEOUT_MS = 5_000L
    const val PRIMARY_MIN_BUFFER_MS = 2_500
    const val PRIMARY_MAX_BUFFER_MS = 30_000
    const val PRIMARY_BUFFER_FOR_PLAYBACK_MS = 750
    const val PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500
    const val CROSSFADE_MIN_BUFFER_MS = 15_000
    const val CROSSFADE_MAX_BUFFER_MS = 45_000
    const val CROSSFADE_FRAME_MS = 32L
}
