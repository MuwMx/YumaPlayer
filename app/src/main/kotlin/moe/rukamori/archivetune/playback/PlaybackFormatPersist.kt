/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber

internal fun formatIdForSource(mediaId: String, source: PlaybackSource): String =
    "${mediaId}_${source.name}"

internal fun isFlacFormat(format: FormatEntity): Boolean =
    format.mimeType.contains("flac", ignoreCase = true) ||
        format.codecs.contains("flac", ignoreCase = true) ||
        format.codecs.contains("alac", ignoreCase = true) ||
        format.itag == 0

internal fun FormatEntity.matchesSource(source: PlaybackSource): Boolean =
    when (source) {
        PlaybackSource.FLAC -> isFlacFormat(this)
        PlaybackSource.YT_MUSIC -> !isFlacFormat(this)
    }

internal fun MusicDatabase.formatForSource(
    mediaId: String?,
    source: PlaybackSource,
): Flow<FormatEntity?> {
    if (mediaId == null) return flowOf(null)
    if (mediaId.isLocalMediaId()) return format(mediaId)

    val scopedId = formatIdForSource(mediaId, source)
    return format(scopedId).flatMapLatest { scoped ->
        if (scoped != null) {
            flowOf(scoped)
        } else {
            format(mediaId).map { legacy ->
                legacy?.takeIf { it.matchesSource(source) }
            }
        }
    }
}

internal suspend fun MusicDatabase.getFormatForSource(
    mediaId: String,
    source: PlaybackSource,
): FormatEntity? {
    if (mediaId.isLocalMediaId()) return format(mediaId).firstOrNull()
    val scopedId = formatIdForSource(mediaId, source)
    val scoped = format(scopedId).firstOrNull()
    if (scoped != null) return scoped
    val legacy = format(mediaId).firstOrNull()
    return legacy?.takeIf { it.matchesSource(source) }
}

internal fun MusicService.persistPlaybackFormat(
    dataSpec: DataSpec,
    mediaId: String,
    networkCacheKey: String,
    knownContentLength: Long?,
    playbackData: YTPlayerUtils.PlaybackData,
): DataSpec {
    playbackData.playbackTracking
        ?.remotePlaybackTrackingUrl()
        ?.let { remotePlaybackTrackingUrlCache[mediaId] = it }
    val format = playbackData.format
    val loudnessDb = playbackData.audioConfig?.loudnessDb
    val perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb
    val resolvedContentLength = format.contentLength ?: knownContentLength ?: 0L
    val resolvedCodecs =
        format.mimeType
            .substringAfter("codecs=", "")
            .removeSurrounding("\"")
            .substringBefore("\"")
    resolvedContentLength.takeIf { it > 0L }?.let {
        contentLengthCache[ytStreamCacheKey(mediaId)] = it
    }

    Timber
        .tag(
            "AudioNormalization",
        ).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
    if (loudnessDb == null && perceptualLoudnessDb == null) {
        Timber.tag("AudioNormalization").w("No loudness data available from YouTube for video: $mediaId")
    }

    val formatEntity =
        FormatEntity(
            id = formatIdForSource(mediaId, PlaybackSource.YT_MUSIC),
            itag = format.itag,
            mimeType = format.mimeType.split(";")[0],
            codecs = resolvedCodecs,
            bitrate = format.bitrate,
            sampleRate = format.audioSampleRate,
            contentLength = resolvedContentLength,
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb,
            playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
        )
    val resolvedNormalizationFactor = calculateAudioNormalizationFactor(formatEntity, normalizeAudio = true)
    audioNormalizationFactorCache[mediaId] = resolvedNormalizationFactor
    scope.launch {
        if (currentMediaMetadata.value?.id == mediaId &&
            dataStore.get(AudioNormalizationKey, true)
        ) {
            normalizeFactor.value = resolvedNormalizationFactor
        }
    }

    database.query {
        upsert(
            formatEntity,
        )
    }
    scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }

    val streamUrl = playbackData.streamUrl

    val trackingExpiryMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)

    val cacheValue =
        AuthScopedCacheValue(
            url = streamUrl,
            expiresAtMs = trackingExpiryMs,
            authFingerprint = playbackData.authFingerprint,
        )
    playbackUrlCache[formatIdForSource(mediaId, PlaybackSource.YT_MUSIC)] = cacheValue
    val targetDataKey = resolveTargetDataKey(mediaId, PlaybackSource.YT_MUSIC)
    val specWithKey = if (dataSpec.key != targetDataKey) dataSpec.buildUpon().setKey(targetDataKey).build() else dataSpec
    val resolvedDataSpec = specWithKey.withUri(streamUrl.toUri())
    val length =
        resolveStreamChunkLength(
            requestedLength = dataSpec.length,
            position = dataSpec.position,
            knownContentLength = knownContentLength ?: format.contentLength,
            chunkLength = MusicService.CHUNK_LENGTH,
            mimeType = format.mimeType,
        )
    return length?.let { nonNullLength ->
        resolvedDataSpec.subrange(0L, nonNullLength)
    } ?: resolvedDataSpec
}
