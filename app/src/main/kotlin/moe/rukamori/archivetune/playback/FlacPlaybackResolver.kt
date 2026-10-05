/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

internal fun MusicService.resolveFlacPlaybackDataSpec(
    dataSpec: DataSpec,
    mediaId: String,
    flacKey: String,
    cacheKey: String,
    shouldBypassFlac: Boolean,
    currentSource: PlaybackSource,
    lowDataEnabled: Boolean,
    isMeteredConnection: Boolean,
): DataSpec? {
    val losslessResult = if (!shouldBypassFlac && currentSource == PlaybackSource.FLAC) {
        val isOffline = connectivityManager.activeNetwork == null
        val hasLocalCache = runCatching {
            playerCache.getCachedSpans(mediaId).isNotEmpty() || downloadCache.getCachedSpans(mediaId).isNotEmpty() ||
                playerCache.getCachedSpans(flacKey).isNotEmpty() || downloadCache.getCachedSpans(flacKey).isNotEmpty()
        }.getOrDefault(false)

        if (isOffline || hasLocalCache) {
            Timber.tag("FLAC_PLAYBACK").d("Bypassed FLAC due to offline or local cache present")
            null
        } else {
            try {
                runBlocking(Dispatchers.IO) {
                    val quality = dataStore.get(FlacStreamingQualityKey, FlacQuality.CD.name).toEnum(FlacQuality.CD)
                    var song = database.song(mediaId).firstOrNull()

                    if (song == null) {
                        Timber.tag("FLAC_PLAYBACK").w("Song $mediaId not found in DB for FLAC resolving, attempting to create transient song")
                        val metadata = (dataSpec.customData as? MediaMetadata)
                            ?: withContext(Dispatchers.Main) {
                                player.currentMediaItem?.takeIf { it.mediaId == mediaId }?.metadata
                                    ?: player.findNextMediaItemById(mediaId)?.metadata
                            }

                        if (metadata != null) {
                            song = createTransientSongFromMedia(metadata)
                        }
                    }

                    val result = song?.let {
                        Timber.tag("FLAC_PLAYBACK").d("Resolving FLAC for: ${it.song.title} (ISRC: ${it.song.isrc ?: "none"}, mediaId: $mediaId)")
                        losslessStreamResolver.resolve(it, quality)
                    }

                    if (result != null) {
                        Timber.tag("FLAC_PLAYBACK").i("FLAC resolved successfully: origin=${result.origin}, url=${result.url}")
                        if (enableMemoryCache) {
                            losslessUrlCache.put(cacheKey, result)
                            losslessUrlCache.put(mediaId, result)
                            losslessUrlCache.put(flacKey, result)
                        }
                    } else {
                        Timber.tag("FLAC_PLAYBACK").w("FLAC resolver returned NULL for $mediaId")
                        losslessUrlCache.remove(cacheKey)
                    }
                    result
                }
            } catch (e: InterruptedException) {
                Timber.tag("FLAC_PLAYBACK").d("FLAC resolving interrupted by player skip")
                losslessUrlCache.remove(cacheKey)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag("FLAC_PLAYBACK").e(e, "FLAC resolve failed")
                losslessUrlCache.remove(cacheKey)
                null
            }
        }
    } else {
        Timber.tag("FLAC_PLAYBACK").d("Bypassed FLAC due to shouldBypassFlac=$shouldBypassFlac (lowDataEnabled=$lowDataEnabled, isMetered=$isMeteredConnection)")
        null
    }

    if (losslessResult != null && losslessResult.url.isNotBlank()) {
        return buildResolvedFlacDataSpec(
            dataSpec = dataSpec,
            mediaId = mediaId,
            flacKey = flacKey,
            streamUrl = losslessResult,
        )
    }

    return null
}
