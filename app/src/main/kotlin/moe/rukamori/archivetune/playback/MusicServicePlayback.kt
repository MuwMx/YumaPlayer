/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import android.content.Intent
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.extensions.directorySizeBytes
import moe.rukamori.archivetune.extensions.findNextMediaItemById
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.crossfade.isTrackFullyCached
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

internal fun MusicService.resolvePlaybackDataSpec(
    dataSpec: DataSpec,
    allowCacheShortCircuit: Boolean,
): DataSpec {
    if (dataSpec.uri.shouldBypassYouTubeResolver()) return dataSpec
    val mediaId = extractMediaIdFromCacheKey(dataSpec.key ?: return dataSpec)
    val startTime = android.os.SystemClock.elapsedRealtime()

    Timber.tag("PlaybackTiming").i("[$mediaId] START resolvePlaybackDataSpec (allowCache=$allowCacheShortCircuit)")

    val storedFormat = null

    val lowDataEnabled = isLowDataEnabled
    val isMeteredConnection = connectivityManager.isActiveNetworkMetered ||
        (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
    val shouldBypassFlac = shouldBypassFlac(lowData = lowDataEnabled, metered = isMeteredConnection)
    val currentSource = currentPlaybackSource
    val isFlacFullyCached = isKeyFullyCached(flacStreamCacheKey(mediaId), mediaId) ||
        (validateLegacyCacheKeyAcrossCaches(downloadCache, playerCache, flacCacheKey(mediaId), PlaybackSource.FLAC) &&
            isKeyFullyCached(flacCacheKey(mediaId), mediaId))

    val effectiveSource = effectiveSource(source = currentSource, shouldBypassFlac = shouldBypassFlac && !isFlacFullyCached)
    if (!audioNormalizationFactorCache.containsKey(mediaId)) {
        scope.launch(Dispatchers.IO) {
            database.formatForSource(mediaId, effectiveSource).firstOrNull()?.let {
                audioNormalizationFactorCache[mediaId] = calculateAudioNormalizationFactor(it, normalizeAudio = true)
            }
        }
    }
    val primaryVersionedKey = streamCacheKey(mediaId, effectiveSource)
    val knownContentLength = getOrResolveContentLength(targetKey = primaryVersionedKey, mediaId = mediaId, storedFormat = storedFormat)

    val cacheProbeStart = android.os.SystemClock.elapsedRealtime()
    val diskShortCircuit = probeDiskCacheShortCircuit(
        dataSpec = dataSpec,
        mediaId = mediaId,
        currentSource = effectiveSource,
        storedFormat = storedFormat,
        allowCacheShortCircuit = allowCacheShortCircuit,
    )
    val cacheProbeDuration = android.os.SystemClock.elapsedRealtime() - cacheProbeStart

    if (diskShortCircuit != null) {
        val actual = when {
            diskShortCircuit.key?.startsWith(STREAM_V2_FLAC_PREFIX) == true || diskShortCircuit.key?.startsWith(LEGACY_FLAC_CACHE_KEY_PREFIX) == true -> PlaybackSource.FLAC
            else -> PlaybackSource.YT_MUSIC
        }
        setActualPlaybackSource(mediaId, actual)
        Timber.tag("PlaybackTiming").i(
            "[$mediaId] DISK CACHE HIT! Short-circuit in ${cacheProbeDuration}ms (Total: ${android.os.SystemClock.elapsedRealtime() - startTime}ms)"
        )
        return diskShortCircuit
    } else {
        Timber.tag("PlaybackTiming").d("[$mediaId] DISK CACHE MISS (checked in ${cacheProbeDuration}ms)")
    }

    if (preferredStreamClient == PlayerStreamClient.ARCHIVETUNE_EXTRACTOR) {
        setActualPlaybackSource(mediaId, PlaybackSource.YT_MUSIC)
        val res = resolveArchiveTuneExtractorDataSpec(dataSpec = dataSpec, mediaId = mediaId)
        Timber.tag("PlaybackTiming").i("[$mediaId] EXTRACTOR RESOLVE in ${android.os.SystemClock.elapsedRealtime() - startTime}ms")
        return res
    }

    val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
    val effectiveNetworkSource = effectiveSource(source = currentSource, shouldBypassFlac = shouldBypassFlac)
    val networkCacheKey = "${mediaId}_${effectiveNetworkSource.name}"

    val memProbeStart = android.os.SystemClock.elapsedRealtime()
    val memoryUrl = resolveFromMemoryUrlPolicy(
        dataSpec = dataSpec,
        mediaId = mediaId,
        authFingerprint = authFingerprint,
        effectiveSource = effectiveSource,
        knownContentLength = knownContentLength,
        storedFormat = storedFormat,
    )
    val memProbeDuration = android.os.SystemClock.elapsedRealtime() - memProbeStart

    if (memoryUrl != null) {
        val actual = when {
            memoryUrl.key?.startsWith(STREAM_V2_FLAC_PREFIX) == true || memoryUrl.key?.startsWith(LEGACY_FLAC_CACHE_KEY_PREFIX) == true -> PlaybackSource.FLAC
            else -> PlaybackSource.YT_MUSIC
        }
        setActualPlaybackSource(mediaId, actual)
        Timber.tag("PlaybackTiming").i(
            "[$mediaId] MEMORY URL HIT! Resolved in ${memProbeDuration}ms (Total: ${android.os.SystemClock.elapsedRealtime() - startTime}ms)"
        )
        return memoryUrl
    } else {
        Timber.tag("PlaybackTiming").d("[$mediaId] MEMORY URL MISS (checked in ${memProbeDuration}ms)")
    }

    val flacStart = android.os.SystemClock.elapsedRealtime()
    val flacDataSpec = resolveFlacPlaybackDataSpec(
        dataSpec = dataSpec,
        mediaId = mediaId,
        shouldBypassFlac = shouldBypassFlac,
        currentSource = currentSource,
        lowDataEnabled = lowDataEnabled,
        isMeteredConnection = isMeteredConnection,
    )
    if (flacDataSpec != null) {
        setActualPlaybackSource(mediaId, PlaybackSource.FLAC)
        Timber.tag("PlaybackTiming").i(
            "[$mediaId] FLAC HIT! Resolved in ${android.os.SystemClock.elapsedRealtime() - flacStart}ms (Total: ${android.os.SystemClock.elapsedRealtime() - startTime}ms)"
        )
        return flacDataSpec
    }

    setActualPlaybackSource(mediaId, PlaybackSource.YT_MUSIC)

    val ytResolveStart = android.os.SystemClock.elapsedRealtime()
    val playbackData = resolveYtPlaybackResponse(
        mediaId = mediaId, shouldBypassFlac = shouldBypassFlac, isMeteredConnection = isMeteredConnection
    )
    Timber.tag("PlaybackTiming").i(
        "[$mediaId] YT RESOLVE COMPLETED in ${android.os.SystemClock.elapsedRealtime() - ytResolveStart}ms (Total: ${android.os.SystemClock.elapsedRealtime() - startTime}ms)"
    )

    val persisted = persistPlaybackFormat(
        dataSpec = dataSpec,
        mediaId = mediaId,
        networkCacheKey = networkCacheKey,
        knownContentLength = knownContentLength,
        playbackData = playbackData,
    )
    val totalTime = android.os.SystemClock.elapsedRealtime() - startTime
    Timber.tag("PlaybackTiming").i("[$mediaId] FULL RESOLVE COMPLETED in ${totalTime}ms")
    return persisted
}

internal fun MusicService.createPlaybackRecoveryEngine(): PlaybackRecoveryEngine =
    PlaybackRecoveryEngine(
        scope = scope,
        playerActions = object : PlaybackRecoveryEngine.PlayerActions {
            override val currentMediaItem: MediaItem? get() = player.currentMediaItem
            override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex
            override val currentPosition: Long get() = player.currentPosition
            override val playWhenReady: Boolean get() = player.playWhenReady
            override val playbackState: Int get() = player.playbackState
            override val isPlaying: Boolean get() = player.isPlaying
            override val playbackSuppressionReason: Int get() = player.playbackSuppressionReason
            override val nextMediaItemIndex: Int get() = player.nextMediaItemIndex
            override fun findNextMediaItemById(mediaId: String): MediaItem? = player.findNextMediaItemById(mediaId)
            override fun prepare() = player.prepare()
            override fun play() = player.play()
            override fun pause() = player.pause()
            override fun stop() = player.stop()
            override fun seekTo(mediaItemIndex: Int, positionMs: Long) = player.seekTo(mediaItemIndex, positionMs)
            override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
            override fun evictMediaConnectionPools() = this@createPlaybackRecoveryEngine.evictMediaConnectionPools()
            override val isCrossfading: Boolean get() = this@createPlaybackRecoveryEngine.isCrossfading
            override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
                this@createPlaybackRecoveryEngine.cancelCrossfade(resetVolume, resetPauseAtEnd)
            override fun registerRetryAttempt(mediaId: String): Boolean =
                playbackStreamRecoveryTracker.registerRetryAttempt(mediaId)
            override fun shouldAutoSkipOnError(): Boolean =
                dataStore.get(AutoSkipNextOnErrorKey, false)
        },
        cacheOps = object : PlaybackRecoveryEngine.CacheOps {
            override val playerCache: Cache get() = this@createPlaybackRecoveryEngine.playerCache
            override val downloadCache: Cache get() = this@createPlaybackRecoveryEngine.downloadCache
            override fun isTrackFullyCached(mediaId: String): Boolean = this@createPlaybackRecoveryEngine.isTrackFullyCached(mediaId)
            override fun invalidatePlaybackUrlCache(mediaId: String) = this@createPlaybackRecoveryEngine.invalidatePlaybackUrlCache(mediaId)
            override fun handleStreamFailureRecovery(mediaId: String) {
                val source = actualPlaybackSources.value[mediaId] ?: currentPlaybackSource
                when (source) {
                    PlaybackSource.FLAC -> {
                        invalidateLosslessUrlCache(mediaId)
                        val v2Key = flacStreamCacheKey(mediaId)
                        runCatching { playerCache.removeResource(v2Key) }
                        val hasKnownLength = runCatching {
                            downloadCache.getContentMetadata(v2Key).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L) > 0L
                        }.getOrDefault(false)
                        if (hasKnownLength && !isKeyFullyCached(v2Key, mediaId)) {
                            runCatching { downloadCache.removeResource(v2Key) }
                        }
                    }
                    PlaybackSource.YT_MUSIC -> {
                        invalidatePlaybackUrlCache(mediaId)
                        removeExtractorPlaybackUrl(mediaId)
                        val v2Key = ytStreamCacheKey(mediaId)
                        runCatching { playerCache.removeResource(v2Key) }
                        val hasKnownLength = runCatching {
                            downloadCache.getContentMetadata(v2Key).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L) > 0L
                        }.getOrDefault(false)
                        if (hasKnownLength && !isKeyFullyCached(v2Key, mediaId)) {
                            runCatching { downloadCache.removeResource(v2Key) }
                        }
                    }
                }
            }
            override fun removeExtractorPlaybackUrl(mediaId: String) {
                extractorPlaybackUrlCache.remove(mediaId)
            }
            override fun getCachedFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue? {
                return (playbackUrlCache[mediaId]
                    ?: playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"])?.takeIf { it.url == failedUrl }
            }
            override fun getCachedExtractorFailedPlaybackUrl(mediaId: String, failedUrl: String): AuthScopedCacheValue? {
                return extractorPlaybackUrlCache[mediaId]?.takeIf { it.url == failedUrl }
            }
            override fun getPlayerCacheDirectorySizeBytes(): Long {
                val cacheDir = StorageLocationRepository.cacheDirectory(this@createPlaybackRecoveryEngine, StorageFolderKind.SONG_CACHE)
                return cacheDir.directorySizeBytes()
            }
        },
        networkState = { isNetworkConnected.value },
        loginPrompt = object : PlaybackRecoveryEngine.LoginPrompt {
            override fun isAppInForeground(): Boolean = this@createPlaybackRecoveryEngine.isAppInForeground()
            override fun openLogin(mediaId: String, targetUrl: String) {
                val deepLink = Uri.parse("archivetune://login?url=${Uri.encode(targetUrl)}")
                val intent =
                    Intent(Intent.ACTION_VIEW, deepLink, this@createPlaybackRecoveryEngine, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                runCatching {
                    startActivity(intent)
                }.onFailure {
                    Timber.e(it, "Failed to open login recovery for %s", mediaId)
                }
            }
        },
        databaseProvider = { database },
    )
