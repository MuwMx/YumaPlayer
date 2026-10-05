/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

internal fun MusicService.persistPlaybackFormat(
    dataSpec: DataSpec,
    mediaId: String,
    flacKey: String,
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
        contentLengthCache[mediaId] = it
        contentLengthCache[flacKey] = it
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
            id = mediaId,
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
    playbackUrlCache[networkCacheKey] = cacheValue
    playbackUrlCache[mediaId] = cacheValue
    playbackUrlCache[flacKey] = cacheValue
    playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"] = cacheValue
    val specWithMediaId = if (dataSpec.key != mediaId) dataSpec.buildUpon().setKey(mediaId).build() else dataSpec
    val resolvedDataSpec = specWithMediaId.withUri(streamUrl.toUri())
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
