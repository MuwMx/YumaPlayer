package moe.rukamori.archivetune.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import java.util.Locale

internal const val FLAC_CACHE_KEY_PREFIX = "flac_"

internal fun flacCacheKey(mediaId: String): String = "$FLAC_CACHE_KEY_PREFIX$mediaId"

internal fun MusicService.resolveCachedDataSpec(
    dataSpec: DataSpec,
    cacheKey: String,
    knownContentLength: Long?,
): DataSpec? {
    val requestedLength =
        when {
            dataSpec.length > 0L -> {
                dataSpec.length
            }

            knownContentLength != null && knownContentLength > dataSpec.position -> {
                knownContentLength - dataSpec.position
            }

            else -> {
                return null
            }
        }

    val cachedLength =
        getContinuousCachedLength(
            cacheKey = cacheKey,
            position = dataSpec.position,
            requestedLength = requestedLength,
        )

    if (cachedLength < requestedLength) return null

    val specWithKey =
        if (dataSpec.key != cacheKey) {
            dataSpec.buildUpon().setKey(cacheKey).build()
        } else {
            dataSpec
        }

    return specWithKey.subrange(0L, requestedLength)
}

internal fun MusicService.getContinuousCachedLength(
    cacheKey: String,
    position: Long,
    requestedLength: Long,
): Long {
    val targetEnd = position.saturatingAdd(requestedLength)
    var cursor = position
    val spans =
        (
            runCatching { downloadCache.getCachedSpans(cacheKey).toList() }.getOrNull().orEmpty() +
                runCatching { playerCache.getCachedSpans(cacheKey).toList() }.getOrNull().orEmpty()
            ).asSequence()
            .filter { span -> span.position.saturatingAdd(span.length) > position }
            .sortedBy { span -> span.position }
            .toList()

    for (span in spans) {
        if (span.position > cursor) break
        val spanEnd = span.position.saturatingAdd(span.length)
        if (spanEnd > cursor) {
            cursor = minOf(spanEnd, targetEnd)
            if (cursor >= targetEnd) break
        }
    }

    return (cursor - position).coerceAtLeast(0L)
}

internal fun Long.saturatingAdd(value: Long): Long {
    if (value <= 0L) return this
    val result = this + value
    return if (result < this) Long.MAX_VALUE else result
}

internal fun Uri.shouldBypassYouTubeResolver(): Boolean {
    val normalizedScheme = scheme?.lowercase(Locale.US)
    return normalizedScheme == "content" ||
        normalizedScheme == "file" ||
        normalizedScheme == "android.resource" ||
        normalizedScheme == "http" ||
        normalizedScheme == "https"
}
