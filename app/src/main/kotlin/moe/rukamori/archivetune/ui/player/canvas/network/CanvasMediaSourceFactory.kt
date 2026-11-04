/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.network

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.di.CanvasCache
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.player.isYouTubeMediaHost
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CanvasArtworkPlayerEntryPoint {
    @CanvasCache
    fun canvasCache(): Cache
}

internal fun resolveCanvasCache(context: Context): Cache? =
    runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CanvasArtworkPlayerEntryPoint::class.java,
        ).canvasCache()
    }.getOrNull()

internal fun createOkHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .dns(YouTube.dns)
        .proxy(null)
        .proxySelector(
            object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val isYouTube = isYouTubeMediaHost(uri?.host)
                    val targetProxy = if (isYouTube) YouTube.streamOkHttpProxy else Proxy.NO_PROXY
                    return listOf(targetProxy)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                }
            },
        )
        .proxyAuthenticator { route, response ->
            val host = route?.address?.url?.host ?: response.request.url.host
            if (!isYouTubeMediaHost(host)) {
                return@proxyAuthenticator null
            }
            val username = YouTube.proxyUsername
            val password = YouTube.proxyPassword
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val credential = Credentials.basic(username, password)
                response.request
                    .newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            } else {
                null
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!isYouTubeMediaHost(request.url.host)) {
                return@addInterceptor chain.proceed(
                    request
                        .newBuilder()
                        .header("User-Agent", CanvasPlaybackUserAgent)
                        .build(),
                )
            }

            val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
            chain.proceed(
                StreamClientUtils
                    .applyRequestProfile(
                        request.newBuilder(),
                        requestProfile,
                    ).build(),
            )
        }.build()

internal fun createCanvasMediaSourceFactory(
    context: Context,
    okHttpClient: OkHttpClient,
    canvasCache: Cache?,
): MediaSource.Factory {
    val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
    val upstreamFactory =
        if (canvasCache != null) {
            CacheDataSource
                .Factory()
                .setCache(canvasCache)
                .setCacheKeyFactory { dataSpec ->
                    dataSpec.key ?: dataSpec.uri.buildUpon().clearQuery().build().toString()
                }
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            httpDataSourceFactory
        }
    return DefaultMediaSourceFactory(
        DefaultDataSource.Factory(
            context,
            upstreamFactory,
        ),
    )
}
