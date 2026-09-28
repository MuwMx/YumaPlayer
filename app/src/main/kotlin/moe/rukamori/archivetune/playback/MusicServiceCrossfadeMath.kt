package moe.rukamori.archivetune.playback

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.audiodsp.CrossfadeConstants
import moe.rukamori.archivetune.extensions.metadata

@Deprecated("Use moe.rukamori.archivetune.audiodsp.CURVE_IN_DEFAULT directly")
internal const val CURVE_IN_DEFAULT = moe.rukamori.archivetune.audiodsp.CURVE_IN_DEFAULT

@Deprecated("Use moe.rukamori.archivetune.audiodsp.CURVE_OUT_DEFAULT directly")
internal const val CURVE_OUT_DEFAULT = moe.rukamori.archivetune.audiodsp.CURVE_OUT_DEFAULT

@Deprecated("Use CrossfadeConstants.DEFAULT_MS directly", ReplaceWith("CrossfadeConstants.DEFAULT_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
internal const val FADE_DEFAULT_MS = CrossfadeConstants.DEFAULT_MS

@Deprecated("Use CrossfadeConstants.MIN_FADE_MS directly", ReplaceWith("CrossfadeConstants.MIN_FADE_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
internal const val MIN_FADE_MS = CrossfadeConstants.MIN_FADE_MS

@Deprecated("Use CrossfadeConstants.END_GUARD_MS directly", ReplaceWith("CrossfadeConstants.END_GUARD_MS", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
internal const val GUARD_WINDOW_MS = CrossfadeConstants.END_GUARD_MS

@Deprecated("Use moe.rukamori.archivetune.audiodsp.ARM_LEAD_MS directly")
internal const val ARM_LEAD_MS = moe.rukamori.archivetune.audiodsp.ARM_LEAD_MS

@Deprecated("Use moe.rukamori.archivetune.audiodsp.FADE_TIMEOUT_MS directly")
internal const val FADE_TIMEOUT_MS = moe.rukamori.archivetune.audiodsp.FADE_TIMEOUT_MS

@Deprecated("Use CrossfadeConstants.CLAMP_MIN_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MIN_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
internal const val FADE_CLAMP_MIN_S = CrossfadeConstants.CLAMP_MIN_S

@Deprecated("Use CrossfadeConstants.CLAMP_MAX_S directly", ReplaceWith("CrossfadeConstants.CLAMP_MAX_S", "moe.rukamori.archivetune.audiodsp.CrossfadeConstants"))
internal const val FADE_CLAMP_MAX_S = CrossfadeConstants.CLAMP_MAX_S

@Deprecated("Use moe.rukamori.archivetune.audiodsp.RISE directly")
internal val RISE = moe.rukamori.archivetune.audiodsp.RISE

@Deprecated("Use moe.rukamori.archivetune.audiodsp.FALL directly")
internal val FALL = moe.rukamori.archivetune.audiodsp.FALL

@Deprecated("Use moe.rukamori.archivetune.audiodsp.effectiveCrossfadeDuration directly")
internal fun MusicService.effectiveCrossfadeDuration(duration: Long): Long? =
    moe.rukamori.archivetune.audiodsp.effectiveCrossfadeDuration(duration, crossfadeDurationMs)

@Deprecated("Use moe.rukamori.archivetune.audiodsp.isGaplessAlbumTransition directly")
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
    return moe.rukamori.archivetune.audiodsp.isGaplessAlbumTransition(currentAlbum, targetAlbum)
}

@Deprecated("Use moe.rukamori.archivetune.audiodsp.requiredStartBufferMs directly")
internal fun MusicService.requiredCrossfadeStartBufferMs(durationMs: Long): Long =
    moe.rukamori.archivetune.audiodsp.requiredStartBufferMs(crossfadeDurationMs)
