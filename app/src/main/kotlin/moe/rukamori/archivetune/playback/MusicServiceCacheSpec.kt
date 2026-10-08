package moe.rukamori.archivetune.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import moe.rukamori.archivetune.constants.PlaybackSource
import java.io.File
import java.util.Locale

internal const val STREAM_V2_YT_PREFIX = "stream_v2_YT_MUSIC_"
internal const val STREAM_V2_FLAC_PREFIX = "stream_v2_FLAC_"
internal const val LEGACY_FLAC_CACHE_KEY_PREFIX = "flac_"
internal const val FLAC_CACHE_KEY_PREFIX = LEGACY_FLAC_CACHE_KEY_PREFIX

internal fun ytStreamCacheKey(mediaId: String): String = "$STREAM_V2_YT_PREFIX$mediaId"
internal fun flacStreamCacheKey(mediaId: String): String = "$STREAM_V2_FLAC_PREFIX$mediaId"
internal fun streamCacheKey(mediaId: String, source: PlaybackSource): String =
    when (source) {
        PlaybackSource.YT_MUSIC -> ytStreamCacheKey(mediaId)
        PlaybackSource.FLAC -> flacStreamCacheKey(mediaId)
    }

internal fun legacyDataKey(mediaId: String, source: PlaybackSource): String =
    when (source) {
        PlaybackSource.YT_MUSIC -> mediaId
        PlaybackSource.FLAC -> flacCacheKey(mediaId)
    }

internal fun flacCacheKey(mediaId: String): String = "$LEGACY_FLAC_CACHE_KEY_PREFIX$mediaId"

internal fun extractMediaIdFromCacheKey(key: String): String =
    when {
        key.startsWith(STREAM_V2_YT_PREFIX) -> key.removePrefix(STREAM_V2_YT_PREFIX)
        key.startsWith(STREAM_V2_FLAC_PREFIX) -> key.removePrefix(STREAM_V2_FLAC_PREFIX)
        key.startsWith(LEGACY_FLAC_CACHE_KEY_PREFIX) -> key.removePrefix(LEGACY_FLAC_CACHE_KEY_PREFIX)
        else -> key
    }

private val FLAC_SIGNATURE = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
private val EBML_SIGNATURE = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
private val FTYP_SIGNATURE = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
private val OGGS_SIGNATURE = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())

internal fun validateLegacyHeader(header: ByteArray, source: PlaybackSource): Boolean {
    if (header.size < 4) return false
    return when (source) {
        PlaybackSource.FLAC -> {
            header.sliceArray(0..3).contentEquals(FLAC_SIGNATURE)
        }
        PlaybackSource.YT_MUSIC -> {
            if (header.sliceArray(0..3).contentEquals(EBML_SIGNATURE)) true
            else if (header.sliceArray(0..3).contentEquals(OGGS_SIGNATURE)) true
            else if (header.size >= 8 && header.sliceArray(4..7).contentEquals(FTYP_SIGNATURE)) true
            else false
        }
    }
}

internal fun validateLegacyCacheKey(cache: Cache, legacyKey: String, source: PlaybackSource): Boolean {
    val spans = runCatching { cache.getCachedSpans(legacyKey) }.getOrNull() ?: return false
    val span0 = spans.firstOrNull { it.position == 0L } ?: return false
    val file = span0.file ?: return false
    if (!file.exists() || !file.canRead() || file.length() < 4L) return false

    val header = ByteArray(12)
    val bytesRead = runCatching {
        file.inputStream().use { it.read(header) }
    }.getOrDefault(-1)
    if (bytesRead < 4) return false

    return validateLegacyHeader(header.copyOf(bytesRead), source)
}

internal fun validateLegacyCacheKeyAcrossCaches(
    downloadCache: Cache,
    playerCache: Cache,
    legacyKey: String,
    source: PlaybackSource,
): Boolean {
    val downloadSpans = runCatching { downloadCache.getCachedSpans(legacyKey) }.getOrNull().orEmpty()
    val playerSpans = runCatching { playerCache.getCachedSpans(legacyKey) }.getOrNull().orEmpty()

    val hasDownload = downloadSpans.isNotEmpty()
    val hasPlayer = playerSpans.isNotEmpty()

    if (!hasDownload && !hasPlayer) return false

    if (hasDownload && !validateLegacyCacheKey(downloadCache, legacyKey, source)) {
        return false
    }
    if (hasPlayer && !validateLegacyCacheKey(playerCache, legacyKey, source)) {
        return false
    }

    return true
}

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
