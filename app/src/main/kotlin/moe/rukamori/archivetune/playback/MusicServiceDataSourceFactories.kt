package moe.rukamori.archivetune.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import moe.rukamori.archivetune.playback.MusicService.ResolvedUrlRoutingDataSource
import moe.rukamori.archivetune.playback.MusicService.SchemeRoutingDataSource
import moe.rukamori.archivetune.utils.isLowDataModeActive

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
            OkHttpDataSource.Factory(mediaOkHttpClient),
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
