/*
 * Copyright (C) 2026 MuwMix <https://github.com/MuwMix> (YumaPlayer)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package moe.rukamori.archivetune.playback.crossfade

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import java.io.File
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import moe.rukamori.archivetune.playback.streamCacheKey
import moe.rukamori.archivetune.playback.legacyDataKey
import moe.rukamori.archivetune.playback.validateLegacyCacheKey
import moe.rukamori.archivetune.playback.validateLegacyCacheKeyAcrossCaches
import moe.rukamori.archivetune.playback.isPlayerInitialized
import moe.rukamori.archivetune.playback.smart.TrackAnalyzer
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber

internal fun MusicService.kickOffUpcomingTrackAnalysis(currentIndex: Int) {
    if (!automixEnabled) return
    val nextIndex = player.nextMediaItemIndex
    if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until player.mediaItemCount) return
    if (player.repeatMode == Player.REPEAT_MODE_ONE || nextIndex == currentIndex) return
    val nextMediaItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull() ?: return
    kickOffTrackAnalysis(nextMediaItem)
}

internal fun isFullyCached(cache: Cache, key: String): Boolean = runCatching {
    val spans = cache.getCachedSpans(key)
    if (spans.isEmpty()) return@runCatching false
    val contentLength = cache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
    contentLength > 0L && cache.isCached(key, 0L, contentLength)
}.getOrDefault(false)

internal fun MusicService.isSourceFullyCached(mediaId: String, source: PlaybackSource): Boolean {
    if (mediaId.isBlank()) return false
    val versioned = streamCacheKey(mediaId, source)
    if (isFullyCached(downloadCache, versioned) || isFullyCached(playerCache, versioned)) return true

    val legacy = legacyDataKey(mediaId, source)
    return validateLegacyCacheKeyAcrossCaches(downloadCache, playerCache, legacy, source) &&
        (isFullyCached(downloadCache, legacy) || isFullyCached(playerCache, legacy))
}

internal fun MusicService.isTrackFullyCached(mediaId: String): Boolean =
    isSourceFullyCached(mediaId, PlaybackSource.FLAC) ||
        isSourceFullyCached(mediaId, PlaybackSource.YT_MUSIC)

internal fun MusicService.registerCacheListenerForKey(key: String) {
    if (key.isBlank()) return
    if (registeredCacheKeys.add(key)) {
        runCatching { downloadCache.addListener(key, automixCacheListener) }
    }
}

internal fun MusicService.registerCacheListenersForMediaItem(mediaItem: MediaItem?) {
    if (mediaItem == null) return
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    if (mediaId.isBlank()) return
    registerCacheListenerForKey(ytStreamCacheKey(mediaId))
    registerCacheListenerForKey(flacStreamCacheKey(mediaId))
    registerCacheListenerForKey(mediaId)
    registerCacheListenerForKey(flacCacheKey(mediaId))
}

internal fun MusicService.checkTrackCacheReadiness(mediaItem: MediaItem?) {
    if (mediaItem == null || !automixEnabled) return
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    if (mediaId.isBlank() || TrackAnalyzer.hasCached(mediaId)) return

    if (isTrackFullyCached(mediaId)) {
        kickOffTrackAnalysis(mediaItem)
    }
}

internal fun MusicService.recheckCacheReadinessForCurrentAndNext() {
    if (!isPlayerInitialized() || !automixEnabled) return
    val currentItem = player.currentMediaItem
    registerCacheListenersForMediaItem(currentItem)
    checkTrackCacheReadiness(currentItem)

    val nextIndex = player.nextMediaItemIndex
    if (nextIndex != C.INDEX_UNSET &&
        nextIndex in 0 until player.mediaItemCount &&
        player.repeatMode != Player.REPEAT_MODE_ONE &&
        nextIndex != player.currentMediaItemIndex
    ) {
        val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull()
        registerCacheListenersForMediaItem(nextItem)
        checkTrackCacheReadiness(nextItem)
    }
}

internal fun MusicService.unregisterAllCacheListeners() {
    for (key in registeredCacheKeys) {
        runCatching { playerCache.removeListener(key, automixCacheListener) }
        runCatching { downloadCache.removeListener(key, automixCacheListener) }
    }
    registeredCacheKeys.clear()
}

internal fun MusicService.kickOffTrackAnalysis(mediaItem: MediaItem?) {
    if (mediaItem == null || !automixEnabled) return
    val mediaId = mediaItem.mediaId.ifBlank { mediaItem.metadata?.id.orEmpty() }
    kickOffTrackAnalysis(mediaId, mediaItem)
}

internal fun MusicService.kickOffTrackAnalysis(mediaId: String, mediaItem: MediaItem? = null) {
    if (mediaId.isBlank() || !automixEnabled) return
    if (TrackAnalyzer.shouldThrottleKickOff(mediaId)) return

    val currentId = runCatching { player.currentMediaItem?.mediaId }.getOrNull()
    val currentDurationMs =
        if (currentId == mediaId) runCatching { player.duration }.getOrDefault(0L) else 0L
    val playlistDurations: Map<String, Double> = if (currentDurationMs > 0L) {
        emptyMap()
    } else {
        runCatching {
            buildMap {
                for (i in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(i)
                    val id = item.mediaId.ifBlank { item.metadata?.id.orEmpty() }
                    val seconds = item.metadata?.duration?.takeIf { it > 0 }?.toDouble()
                    if (id.isNotBlank() && seconds != null) put(id, seconds)
                }
            }
        }.getOrDefault(emptyMap())
    }

    val service = this
    ioScope.launch {
        try {
            val cached = TrackAnalyzer.getOrFetchCached(mediaId)
            if (cached != null) {
                Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis ready from cache/Room: mediaId=$mediaId bpm=${cached.bpm}")
                return@launch
            }

            var durationSeconds = mediaItem?.metadata?.duration?.takeIf { it > 0 }?.toDouble()
            if (durationSeconds == null || durationSeconds <= 0.0) {
                durationSeconds = if (currentId == mediaId && currentDurationMs > 0L) {
                    currentDurationMs / 1000.0
                } else {
                    playlistDurations[mediaId]
                }
            }

            if (mediaId.isLocalMediaId()) {
                val uri = mediaItem?.localConfiguration?.uri ?: mediaId.toUri()
                val scheme = uri.scheme?.lowercase()
                if (scheme == "content" || scheme == "file" || scheme == "android.resource") {
                    TrackAnalyzer.analyze(mediaId, service, uri, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            if (mediaId.startsWith("/")) {
                val file = File(mediaId)
                if (file.exists() && file.canRead()) {
                    TrackAnalyzer.analyze(mediaId, file, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            val directUri = mediaItem?.localConfiguration?.uri
            if (directUri != null) {
                val scheme = directUri.scheme?.lowercase()
                if (scheme == "content" || scheme == "file" || scheme == "android.resource") {
                    TrackAnalyzer.analyze(mediaId, service, directUri, durationSeconds = durationSeconds)
                    return@launch
                }
            }

            if (isTrackFullyCached(mediaId)) {
                Timber.tag(MusicService.TAG).d("kickOffTrackAnalysis start: offline downloaded track mediaId=$mediaId")
                analyzeCachedTrack(mediaId, durationSeconds = durationSeconds)
                return@launch
            }
        } catch (e: Exception) {
            Timber.tag(MusicService.TAG).v(e, "Background TrackAnalyzer failed for mediaId=$mediaId")
        }
    }
}

internal suspend fun MusicService.analyzeCachedTrack(
    mediaId: String,
    durationSeconds: Double? = null,
): TrackAnalysisResult? {
    if (mediaId.isBlank()) return null
    TrackAnalyzer.getCached(mediaId)?.let { return it }

    val v2Flac = flacStreamCacheKey(mediaId)
    if (isFullyCached(downloadCache, v2Flac)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, v2Flac, durationSeconds = durationSeconds)
    }

    val v2Yt = ytStreamCacheKey(mediaId)
    if (isFullyCached(downloadCache, v2Yt)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, v2Yt, durationSeconds = durationSeconds)
    }

    val legacyFlac = flacCacheKey(mediaId)
    if (validateLegacyCacheKey(downloadCache, legacyFlac, PlaybackSource.FLAC) && isFullyCached(downloadCache, legacyFlac)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, legacyFlac, durationSeconds = durationSeconds)
    }

    if (validateLegacyCacheKey(downloadCache, mediaId, PlaybackSource.YT_MUSIC) && isFullyCached(downloadCache, mediaId)) {
        return TrackAnalyzer.analyze(mediaId, downloadCache, mediaId, durationSeconds = durationSeconds)
    }

    return null
}

