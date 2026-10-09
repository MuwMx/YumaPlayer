/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.*
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.moriextractor.StreamingExtractionManager
import moe.rukamori.archivetune.playback.crossfade.isFullyCached
import moe.rukamori.archivetune.playback.resolvers.LosslessStreamResolver
import moe.rukamori.archivetune.playback.resolvers.StreamUrlCache
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.retryWithoutPlaybackLoginContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "StreamPrefetcher"
private const val EXTRACTOR_CACHE_FINGERPRINT_PREFIX = "archivetune_extractor:"
private const val EXTRACTOR_CACHE_TTL_MS = 5 * 60 * 1000L

internal class PrefetchCacheSpec(
    val downloadCache: Cache, val playbackUrlCache: ConcurrentHashMap<String, AuthScopedCacheValue>,
    val extractorPlaybackUrlCache: ConcurrentHashMap<String, AuthScopedCacheValue>, val losslessUrlCache: StreamUrlCache,
    val remotePlaybackTrackingUrlCache: ConcurrentHashMap<String, String>, val contentLengthCache: ConcurrentHashMap<String, Long>,
    val audioNormalizationFactorCache: ConcurrentHashMap<String, Float>,
)

internal class PrefetchDataSourceFactories(val mediaOkHttpClient: OkHttpClient, val extractorMediaOkHttpClient: OkHttpClient)

internal class StreamPrefetcher(
    private val cacheSpec: PrefetchCacheSpec,
    private val dataSourceFactories: PrefetchDataSourceFactories,
    private val scope: CoroutineScope,
    private val delegate: Delegate,
) {
    interface Delegate {
        val context: Context
        val isPlaying: Boolean; val playWhenReady: Boolean; val nextMediaItemIndex: Int; val mediaItemCount: Int; val repeatMode: Int
        fun getMediaItemAt(index: Int): MediaItem?
        val connectivityManager: ConnectivityManager?; fun isLowDataModeActive(): Boolean; fun isTrackFullyCached(mediaId: String): Boolean
        fun isSourceFullyCached(mediaId: String, source: PlaybackSource): Boolean
        val isLowDataEnabled: Boolean; val currentPlaybackSource: PlaybackSource; val preferredStreamClient: PlayerStreamClient
        val audioQuality: AudioQuality; val enableMemoryCache: Boolean
        val database: MusicDatabase; val streamingExtractionManager: StreamingExtractionManager
        val losslessStreamResolver: LosslessStreamResolver; val dataStore: DataStore<Preferences>
        fun createTransientSong(metadata: MediaMetadata): Song
        fun calculateAudioNormalization(format: FormatEntity): Float
        fun kickOffTrackAnalysis(mediaId: String, mediaItem: MediaItem?)
    }

    private var prefetchJob: Job? = null; private val prefetchTimelineGeneration = AtomicLong(0L)
    private val resolvingPrefetchMediaIds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activePrefetchMediaId: String? = null; @Volatile private var activeCacheWriter: CacheWriter? = null

    fun cancelPrefetch() {
        prefetchTimelineGeneration.incrementAndGet()
        activePrefetchMediaId = null
        activeCacheWriter?.cancel(); activeCacheWriter = null
        prefetchJob?.cancel(); prefetchJob = null
        resolvingPrefetchMediaIds.clear()
    }

    fun prefetchAround(currentIndex: Int) {
        if (!delegate.isPlaying && !delegate.playWhenReady) return
        val nextIndex = delegate.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex < 0 || nextIndex >= delegate.mediaItemCount || delegate.repeatMode == REPEAT_MODE_ONE || nextIndex == currentIndex) return
        val nextMediaItem = runCatching { delegate.getMediaItemAt(nextIndex) }.getOrNull() ?: return
        val mediaId = nextMediaItem.mediaId.ifBlank { nextMediaItem.metadata?.id.orEmpty() }
        if (mediaId.isBlank() || mediaId.isLocalMediaId() || mediaId.startsWith("/") || mediaId.contains(".m3u8", ignoreCase = true)) return
        val directUri = nextMediaItem.localConfiguration?.uri
        if (directUri != null) {
            val scheme = directUri.scheme?.lowercase(Locale.US)
            if (scheme in setOf("content", "file", "android.resource")) return
            if ((scheme == "http" || scheme == "https") && directUri.toString().let { it.contains(".m3u8", true) || it.contains("manifest", true) }) return
        }

        val cm = delegate.connectivityManager ?: return
        val activeNetwork = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return
        val isMetered = cm.isActiveNetworkMetered || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (isMetered && delegate.isLowDataModeActive()) return
        if (activePrefetchMediaId == mediaId && prefetchJob?.isActive == true) return

        val metadata = nextMediaItem.metadata
        val bypassFlac = shouldBypassFlac(lowData = delegate.isLowDataEnabled, metered = isMetered)
        val effectiveSource = effectiveSource(source = delegate.currentPlaybackSource, shouldBypassFlac = bypassFlac)
        if (delegate.isSourceFullyCached(mediaId, effectiveSource)) return

        cancelPrefetch()
        if (!resolvingPrefetchMediaIds.add(mediaId)) return
        activePrefetchMediaId = mediaId
        val targetGeneration = prefetchTimelineGeneration.get()
        val cacheKey = "${mediaId}_${effectiveSource.name}"
        val streamClient = delegate.preferredStreamClient
        val quality = delegate.audioQuality

        prefetchJob = scope.launch {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                if (targetGeneration != prefetchTimelineGeneration.get() || !isActive) return@launch
                var downloadUrl: String? = null; var targetCacheKey = streamCacheKey(mediaId, effectiveSource); var downloadClient = dataSourceFactories.mediaOkHttpClient
                val requestHeaders = mutableMapOf<String, String>()

                if (streamClient == PlayerStreamClient.ARCHIVETUNE_EXTRACTOR) {
                    val authState = YouTube.currentPlaybackAuthState()
                    val streamUrl = delegate.streamingExtractionManager.extractAudioUrl(mediaId.toYouTubeWatchUrl(), authState.resolveExtractorPoToken(), authState.resolveExtractorCookies(), authState.resolveExtractorGvsToken())
                    if (targetGeneration != prefetchTimelineGeneration.get() || !isActive) return@launch
                    val cacheValue = AuthScopedCacheValue(streamUrl, System.currentTimeMillis() + EXTRACTOR_CACHE_TTL_MS, EXTRACTOR_CACHE_FINGERPRINT_PREFIX + authState.fingerprint)
                    cacheSpec.extractorPlaybackUrlCache[mediaId] = cacheValue
                    downloadUrl = streamUrl
                    targetCacheKey = ytStreamCacheKey(mediaId)
                    downloadClient = dataSourceFactories.extractorMediaOkHttpClient
                } else {
                    var resolvedFlac = false
                    if (!bypassFlac && effectiveSource == PlaybackSource.FLAC) {
                        val flacQuality = delegate.dataStore.get(FlacStreamingQualityKey, FlacQuality.CD.name).toEnum(FlacQuality.CD)
                        val song = delegate.database.song(mediaId).firstOrNull() ?: metadata?.let { delegate.createTransientSong(it) }
                        if (song != null) {
                            val cachedFlac = if (delegate.enableMemoryCache) cacheSpec.losslessUrlCache.get(cacheKey) else null
                            val flacResult = cachedFlac ?: delegate.losslessStreamResolver.resolve(song, flacQuality)
                            if (flacResult != null && flacResult.url.isNotBlank()) {
                                if (targetGeneration != prefetchTimelineGeneration.get() || !isActive) return@launch
                                if (delegate.enableMemoryCache) {
                                    cacheSpec.losslessUrlCache.put("${mediaId}_${PlaybackSource.FLAC.name}", flacResult)
                                }
                                if (flacResult.origin in listOf("squid", "kennyy", "arcod", "qobuz", "qbdlx")) {
                                    requestHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                                    requestHeaders["Referer"] = "https://music.youtube.com/"
                                }
                                val flacTargetKey = flacStreamCacheKey(mediaId)
                                val flacFormatId = "${mediaId}_${PlaybackSource.FLAC.name}"
                                val inMemoryLength = cacheSpec.contentLengthCache[flacTargetKey]
                                val metadataLength = resolveFlacKeyMetadataLength(flacTargetKey, listOf(cacheSpec.downloadCache))
                                val externalLength = inMemoryLength?.takeIf { it > 0L } ?: metadataLength

                                delegate.database.transaction {
                                    val existing = getFormatById(flacFormatId)
                                    val preservedLength = resolvePreservedFlacContentLength(
                                        targetKey = flacTargetKey,
                                        existingLength = existing?.contentLength,
                                        inMemoryLength = externalLength,
                                    )
                                    if (preservedLength > 0L) {
                                        cacheSpec.contentLengthCache[flacTargetKey] = preservedLength
                                    }
                                    val flacFormat = FormatEntity(
                                        id = flacFormatId,
                                        itag = 0,
                                        mimeType = "audio/flac",
                                        codecs = flacResult.codec ?: "flac",
                                        bitrate = flacResult.bitrateKbps ?: 0,
                                        sampleRate = flacResult.sampleRateHz,
                                        contentLength = preservedLength,
                                        loudnessDb = existing?.loudnessDb,
                                        perceptualLoudnessDb = existing?.perceptualLoudnessDb,
                                        playbackUrl = flacResult.url,
                                        bitsPerSample = flacResult.bitsPerSample ?: existing?.bitsPerSample,
                                    )
                                    upsert(flacFormat)
                                }
                                resolvedFlac = true; downloadUrl = flacResult.url; targetCacheKey = flacTargetKey
                            }
                        }
                    }
                    if (!resolvedFlac) {
                        val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
                        val cached = cacheSpec.playbackUrlCache[cacheKey]?.takeIf { it.isValidFor(authFingerprint, YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS) }
                        if (cached != null) {
                            cacheSpec.playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"] = cached
                            downloadUrl = cached.url
                            targetCacheKey = ytStreamCacheKey(mediaId)
                        } else {
                            val res = delegate.context.retryWithoutPlaybackLoginContext {
                                YTPlayerUtils.playerResponseForPlayback(mediaId, null, if (bypassFlac) AudioQuality.LOW else quality, cm, streamClient, isMetered)
                            }.getOrNull()
                            if (res != null) {
                                if (targetGeneration != prefetchTimelineGeneration.get() || !isActive) return@launch
                                res.playbackTracking?.remotePlaybackTrackingUrl()?.let { cacheSpec.remotePlaybackTrackingUrlCache[mediaId] = it }
                                val format = res.format
                                val len = format.contentLength ?: 0L
                                val codecs = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\"").substringBefore("\"")
                                if (len > 0L) {
                                    cacheSpec.contentLengthCache[ytStreamCacheKey(mediaId)] = len
                                }
                                val formatEntity = FormatEntity(id = "${mediaId}_${PlaybackSource.YT_MUSIC.name}", itag = format.itag, mimeType = format.mimeType.split(";")[0], codecs = codecs, bitrate = format.bitrate, sampleRate = format.audioSampleRate, contentLength = len, loudnessDb = res.audioConfig?.loudnessDb, perceptualLoudnessDb = res.audioConfig?.perceptualLoudnessDb, playbackUrl = res.playbackTracking?.videostatsPlaybackUrl?.baseUrl)
                                cacheSpec.audioNormalizationFactorCache[mediaId] = delegate.calculateAudioNormalization(formatEntity)
                                delegate.database.query { upsert(formatEntity) }
                                val cacheValue = AuthScopedCacheValue(res.streamUrl, System.currentTimeMillis() + (res.streamExpiresInSeconds * 1000L), res.authFingerprint)
                                cacheSpec.playbackUrlCache["${mediaId}_${PlaybackSource.YT_MUSIC.name}"] = cacheValue
                                downloadUrl = res.streamUrl
                                targetCacheKey = ytStreamCacheKey(mediaId)
                            }
                        }
                    }
                }

                if (targetGeneration != prefetchTimelineGeneration.get() || !isActive) return@launch
                val cache = cacheSpec.downloadCache

                if (downloadUrl.isNullOrBlank() || delegate.isSourceFullyCached(mediaId, effectiveSource)) {
                    if (delegate.isSourceFullyCached(mediaId, effectiveSource)) delegate.kickOffTrackAnalysis(mediaId, nextMediaItem)
                    return@launch
                }

                val dataSpec = DataSpec.Builder().setUri(downloadUrl.toUri()).setKey(targetCacheKey).apply {
                    if (requestHeaders.isNotEmpty()) setHttpRequestHeaders(requestHeaders)
                }.build()
                val cacheDataSource = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(OkHttpDataSource.Factory(downloadClient))
                    .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache).setBufferSize(256 * 1024))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource()

                val writer = CacheWriter(cacheDataSource, dataSpec, ByteArray(128 * 1024), null).also { activeCacheWriter = it }
                Timber.tag(TAG).d("Prefetch start: mediaId=$mediaId, key=$targetCacheKey")
                try {
                    writer.cache()
                    if (targetGeneration == prefetchTimelineGeneration.get() && isActive) {
                        Timber.tag(TAG).d("Prefetch finish: mediaId=$mediaId, key=$targetCacheKey")
                        if (delegate.isTrackFullyCached(mediaId)) delegate.kickOffTrackAnalysis(mediaId, nextMediaItem)
                    }
                } finally {
                    if (activeCacheWriter === writer) activeCacheWriter = null
                    runCatching { cacheDataSource.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            } finally {
                if (activePrefetchMediaId == mediaId) activePrefetchMediaId = null
                resolvingPrefetchMediaIds.remove(mediaId)
            }
        }
    }
}
