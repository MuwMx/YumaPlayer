package moe.rukamori.archivetune.playback

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.metadata

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
