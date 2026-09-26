package moe.rukamori.archivetune.playback

object PlaybackConstants {
    internal fun shouldStopServiceOnTaskRemoved(
        stopMusicOnTaskClearEnabled: Boolean,
        isHostSessionActive: Boolean,
        isPlaybackInactive: Boolean,
    ): Boolean = (isHostSessionActive && isPlaybackInactive) || stopMusicOnTaskClearEnabled

    const val CHUNK_LENGTH = 8 * 1024 * 1024L
    const val MIN_CROSSFADE_DURATION_MS = 500L
    const val CROSSFADE_END_GUARD_MS = 150L
    const val CROSSFADE_PREPARE_AHEAD_MS = 30_000L
    const val CROSSFADE_READY_TIMEOUT_MS = 5_000L
    const val CROSSFADE_HANDOFF_READY_TIMEOUT_MS = 5_000L
    const val CROSSFADE_HANDOFF_BUFFER_MS = 5_000L
    const val CROSSFADE_HANDOFF_SEEK_GUARD_MS = 750L
    const val CROSSFADE_MIN_BUFFER_BEFORE_START_MS = 5_000L
    const val CROSSFADE_MAX_BUFFER_BEFORE_START_MS = 12_500L
    const val PRIMARY_MIN_BUFFER_MS = 20_000
    const val PRIMARY_MAX_BUFFER_MS = 60_000
    const val PRIMARY_BUFFER_FOR_PLAYBACK_MS = 750
    const val PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500
    const val CROSSFADE_MIN_BUFFER_MS = 15_000
    const val CROSSFADE_MAX_BUFFER_MS = 45_000
    const val CROSSFADE_FRAME_MS = 32L
}
