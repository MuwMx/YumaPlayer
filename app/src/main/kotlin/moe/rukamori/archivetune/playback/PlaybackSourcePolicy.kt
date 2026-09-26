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
