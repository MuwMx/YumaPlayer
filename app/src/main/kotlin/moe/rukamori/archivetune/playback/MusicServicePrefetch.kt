package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowDataModeActive

internal fun MusicService.getOrCreateStreamPrefetcher(): StreamPrefetcher {
    streamPrefetcher?.let { return it }
    val prefetcher = StreamPrefetcher(
        cacheSpec = PrefetchCacheSpec(
            downloadCache = downloadCache,
            playbackUrlCache = playbackUrlCache,
            extractorPlaybackUrlCache = extractorPlaybackUrlCache,
            losslessUrlCache = losslessUrlCache,
            remotePlaybackTrackingUrlCache = remotePlaybackTrackingUrlCache,
            contentLengthCache = contentLengthCache,
            audioNormalizationFactorCache = audioNormalizationFactorCache,
        ),
        dataSourceFactories = PrefetchDataSourceFactories(
            mediaOkHttpClient = mediaOkHttpClient,
            extractorMediaOkHttpClient = extractorMediaOkHttpClient,
        ),
        scope = ioScope,
        delegate = object : StreamPrefetcher.Delegate {
            override val context: Context get() = this@getOrCreateStreamPrefetcher
            override val isPlaying: Boolean get() = player.isPlaying
            override val playWhenReady: Boolean get() = player.playWhenReady
            override val nextMediaItemIndex: Int get() = player.nextMediaItemIndex
            override val mediaItemCount: Int get() = player.mediaItemCount
            override val repeatMode: Int get() = player.repeatMode
            override fun getMediaItemAt(index: Int): MediaItem? = player.getMediaItemAt(index)

            override val connectivityManager get() = this@getOrCreateStreamPrefetcher.connectivityManager
            override fun isLowDataModeActive() = this@getOrCreateStreamPrefetcher.isLowDataModeActive()
            override fun isTrackFullyCached(mediaId: String) = this@getOrCreateStreamPrefetcher.isTrackFullyCached(mediaId)

            override val isLowDataEnabled get() = this@getOrCreateStreamPrefetcher.isLowDataEnabled
            override val currentPlaybackSource get() = this@getOrCreateStreamPrefetcher.currentPlaybackSource
            override val preferredStreamClient get() = this@getOrCreateStreamPrefetcher.preferredStreamClient
            override val audioQuality get() = this@getOrCreateStreamPrefetcher.audioQuality
            override val enableMemoryCache get() = this@getOrCreateStreamPrefetcher.enableMemoryCache

            override val database get() = this@getOrCreateStreamPrefetcher.database
            override val streamingExtractionManager get() = this@getOrCreateStreamPrefetcher.streamingExtractionManager
            override val losslessStreamResolver get() = this@getOrCreateStreamPrefetcher.losslessStreamResolver
            override val dataStore get() = this@getOrCreateStreamPrefetcher.dataStore

            override fun createTransientSong(metadata: MediaMetadata) =
                this@getOrCreateStreamPrefetcher.createTransientSongFromMedia(metadata)

            override fun calculateAudioNormalization(format: FormatEntity) =
                this@getOrCreateStreamPrefetcher.calculateAudioNormalizationFactor(format, normalizeAudio = true)

            override fun kickOffTrackAnalysis(mediaId: String, mediaItem: MediaItem?) {
                this@getOrCreateStreamPrefetcher.kickOffTrackAnalysis(mediaId, mediaItem)
            }
        },
    )
    streamPrefetcher = prefetcher
    return prefetcher
}

internal fun MusicService.cancelPrefetch() {
    streamPrefetcher?.cancelPrefetch()
}

internal fun MusicService.prefetchAround(currentIndex: Int = player.currentMediaItemIndex) {
    getOrCreateStreamPrefetcher().prefetchAround(currentIndex)
}
