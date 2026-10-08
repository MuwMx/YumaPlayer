package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.constants.PlaybackSource

internal fun shouldBypassFlac(
    lowData: Boolean,
    metered: Boolean,
): Boolean = lowData && metered

internal fun effectiveSource(
    source: PlaybackSource,
    shouldBypassFlac: Boolean,
): PlaybackSource = if (shouldBypassFlac) PlaybackSource.YT_MUSIC else source

internal fun updateActualPlaybackSources(
    current: Map<String, PlaybackSource>,
    mediaId: String,
    source: PlaybackSource,
    currentPlayingId: String?,
): Map<String, PlaybackSource> {
    if (mediaId.isBlank()) return current
    if (mediaId == currentPlayingId && current[mediaId] == PlaybackSource.FLAC && source != PlaybackSource.FLAC) {
        return current
    }
    val updated = LinkedHashMap<String, PlaybackSource>(current)
    updated.remove(mediaId)
    updated[mediaId] = source
    while (updated.size > 32) {
        val oldest = updated.keys.firstOrNull { it != currentPlayingId } ?: break
        updated.remove(oldest)
    }
    return updated
}
