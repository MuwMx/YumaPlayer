package moe.rukamori.archivetune.playback

import android.media.MediaCodecList
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import moe.rukamori.archivetune.playback.engine.ResolvedUrlRoutingDataSource
import moe.rukamori.archivetune.playback.engine.SchemeRoutingDataSource
import moe.rukamori.archivetune.utils.isLowDataModeActive
import java.util.Locale

internal fun MusicService.createPlayerCacheDataSourceFactory(cacheWriteEnabled: Boolean): CacheDataSource.Factory =
    CacheDataSource
        .Factory()
        .setCache(playerCache)
        .setUpstreamDataSourceFactory(createResolvedUpstreamDataSourceFactory())
        .apply {
            if (!cacheWriteEnabled) {
                setCacheWriteDataSinkFactory(null)
            }
        }.setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

internal fun MusicService.createCacheDataSource(): CacheDataSource.Factory =
    CacheDataSource
        .Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(
            DataSource.Factory {
                createPlayerCacheDataSourceFactory(
                    cacheWriteEnabled = !isLowDataModeActive(),
                ).createDataSource()
            },
        ).setCacheWriteDataSinkFactory(null)
        .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

internal fun MusicService.createDataSourceFactory(): DataSource.Factory {
    val cachedFactory =
        ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            resolvePlaybackDataSpec(
                dataSpec = dataSpec,
                allowCacheShortCircuit = true,
            )
        }
    val directFactory = createResolvedUpstreamDataSourceFactory()

    return DataSource.Factory {
        SchemeRoutingDataSource(
            cachedFactory = cachedFactory,
            directFactory = directFactory,
        )
    }
}

internal fun MusicService.createResolvedUpstreamDataSourceFactory(): DataSource.Factory {
    val youtubeMediaFactory =
        DefaultDataSource.Factory(
            this,
            ChunkedDataSource.Factory(
                upstream = OkHttpDataSource.Factory(mediaOkHttpClient),
                chunkBytes = STREAM_CHUNK_BYTES,
            ),
        )
    val extractorMediaFactory =
        DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(extractorMediaOkHttpClient),
        )
    val routingFactory =
        DataSource.Factory {
            ResolvedUrlRoutingDataSource(
                defaultFactory = youtubeMediaFactory,
                extractorFactory = extractorMediaFactory,
                shouldUseExtractorFactory = ::isExtractorPlaybackUri,
            )
        }

    return ResolvingDataSource.Factory(routingFactory) { dataSpec ->
        resolvePlaybackDataSpec(
            dataSpec = dataSpec,
            allowCacheShortCircuit = false,
        )
    }
}

internal fun MusicService.createMediaSourceFactory() =
    DefaultMediaSourceFactory(
        createDataSourceFactory(),
        DefaultExtractorsFactory(),
    )

internal fun MusicService.resolveMediaItemForCast(mediaItem: MediaItem): MediaItem {
    val uri = mediaItem.localConfiguration?.uri ?: return mediaItem
    if (uri.shouldBypassYouTubeResolver()) return mediaItem
    val dataSpec =
        DataSpec
            .Builder()
            .setUri(uri)
            .setKey(mediaItem.localConfiguration?.customCacheKey ?: mediaItem.mediaId)
            .build()
    val resolvedDataSpec =
        resolvePlaybackDataSpec(
            dataSpec = dataSpec,
            allowCacheShortCircuit = false,
        )
    return if (resolvedDataSpec.uri == uri) {
        mediaItem
    } else {
        mediaItem
            .buildUpon()
            .setUri(resolvedDataSpec.uri)
            .build()
    }
}

internal fun MusicService.isExtractorPlaybackUri(uri: Uri): Boolean {
    val url = uri.toString()
    return extractorPlaybackUrlCache.values.any { it.url == url } ||
        uri.path?.startsWith("/api/play/") == true
}

internal fun Uri.shouldBypassPlayerCache(): Boolean {
    val normalizedScheme = scheme?.lowercase(Locale.US)
    return normalizedScheme == "content" ||
        normalizedScheme == "file" ||
        normalizedScheme == "android.resource"
}

internal fun deviceSupportsMimeType(mimeType: String): Boolean =
    runCatching {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        codecList.codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }.getOrDefault(false)
