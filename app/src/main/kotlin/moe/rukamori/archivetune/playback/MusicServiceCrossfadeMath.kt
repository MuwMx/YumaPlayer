package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import moe.rukamori.archivetune.extensions.metadata

internal const val CURVE_IN_DEFAULT = "S_CURVE"
internal const val CURVE_OUT_DEFAULT = "S_CURVE"
internal const val FADE_DEFAULT_MS = 2000L
internal const val MIN_FADE_MS = 500L
internal const val GUARD_WINDOW_MS = 150L
internal const val ARM_LEAD_MS = 4000L
internal const val FADE_TIMEOUT_MS = 12000L
internal const val FADE_CLAMP_MIN_S = 1
internal const val FADE_CLAMP_MAX_S = 12

internal val RISE: (Float) -> Float = { p -> sin(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }
internal val FALL: (Float) -> Float = { p -> cos(p.coerceIn(0f, 1f) * PI.toFloat() / 2f) }

internal fun MusicService.effectiveCrossfadeDuration(duration: Long): Long? {
    if (duration == C.TIME_UNSET || duration <= 0L) return null
    val maxDuration = duration - GUARD_WINDOW_MS
    if (maxDuration < MIN_FADE_MS) return null
    return crossfadeDurationMs
        .coerceAtLeast(MIN_FADE_MS)
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

internal fun MusicService.requiredCrossfadeStartBufferMs(durationMs: Long): Long =
    (durationMs + MusicService.CROSSFADE_HANDOFF_BUFFER_MS)
        .coerceAtLeast(MusicService.CROSSFADE_MIN_BUFFER_BEFORE_START_MS)
        .coerceAtMost(MusicService.CROSSFADE_MAX_BUFFER_BEFORE_START_MS)
