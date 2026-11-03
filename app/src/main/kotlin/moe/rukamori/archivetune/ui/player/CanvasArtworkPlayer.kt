/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.di.CanvasCache
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Credentials
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L
private const val CanvasMaxVideoWidth = 4_096
private const val CanvasMaxVideoHeight = 4_096

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CanvasArtworkPlayerEntryPoint {
    @CanvasCache
    fun canvasCache(): Cache
}

@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onReadyChange: (Boolean) -> Unit = {},
) {
    val currentOnReadyChange by rememberUpdatedState(onReadyChange)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val canvasCache =
        remember(context) {
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    CanvasArtworkPlayerEntryPoint::class.java,
                ).canvasCache()
            }.getOrNull()
        }
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback
    if (initial == null) {
        SideEffect {
            currentOnReadyChange(false)
        }
        return
    }
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var hasPlaybackFailed by remember(initial) { mutableStateOf(false) }

    LaunchedEffect(isVideoReady) {
        currentOnReadyChange(isVideoReady)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentOnReadyChange(false)
        }
    }

    LaunchedEffect(primary, fallback) {
        val target = primary ?: fallback
        if (target != null && target != currentUrl) {
            currentUrl = target
            isVideoReady = false
            hasPlaybackFailed = false
            currentOnReadyChange(false)
        }
    }

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .dns(YouTube.dns)
                .proxy(null)
                .proxySelector(
                    object : ProxySelector() {
                        override fun select(uri: URI?): List<Proxy> {
                            val isYouTube = isYouTubeMediaHost(uri?.host)
                            val targetProxy = if (isYouTube) YouTube.streamOkHttpProxy ?: Proxy.NO_PROXY else Proxy.NO_PROXY
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
        }
    val mediaSourceFactory =
        remember(context, okHttpClient, canvasCache) {
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
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    upstreamFactory,
                ),
            )
        }
    val exoPlayer =
        remember(mediaSourceFactory) {
            val renderersFactory =
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
            ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setForceHighestSupportedBitrate(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .setMaxVideoSize(CanvasMaxVideoWidth, CanvasMaxVideoHeight)
                            .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false
                }
        }

    val playerId = remember(exoPlayer) { Integer.toHexString(System.identityHashCode(exoPlayer)) }

    LaunchedEffect(isPlaying, lifecycleState, exoPlayer, hasPlaybackFailed) {
        Timber.tag(CanvasPlaybackLogTag).d("Canvas playback sync: player=$playerId isPlaying=$isPlaying lifecycle=$lifecycleState failed=$hasPlaybackFailed")
        exoPlayer.setCanvasPlayback(
            isPlaying = isPlaying && !hasPlaybackFailed,
            isStarted = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
        )
    }

    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary || hasPlaybackFailed) return@LaunchedEffect

        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary && !hasPlaybackFailed) {
            delay(CanvasPlaybackStallCheckIntervalMs)

            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                stalledForMs = 0L
                lastPosition = exoPlayer.currentPosition
                continue
            }

            val currentPosition = exoPlayer.currentPosition
            val playbackState = exoPlayer.playbackState
            val positionAdvanced = currentPosition != lastPosition
            val isActivelyRendering =
                playbackState == Player.STATE_READY &&
                    exoPlayer.isPlaying &&
                    positionAdvanced

            stalledForMs =
                if (isActivelyRendering) {
                    0L
                } else {
                    stalledForMs + CanvasPlaybackStallCheckIntervalMs
                }

            if (stalledForMs >= CanvasPlaybackStallTimeoutMs) {
                currentUrl = fallback
                isVideoReady = false
                hasPlaybackFailed = false
                currentOnReadyChange(false)
                return@LaunchedEffect
            }

            lastPosition = currentPosition
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed on $currentUrl")
                    val next =
                        when (currentUrl) {
                            primary -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        Timber.tag(CanvasPlaybackLogTag).i("Switching canvas to fallback URL: $next")
                        currentUrl = next
                        isVideoReady = false
                        hasPlaybackFailed = false
                        currentOnReadyChange(false)
                    } else {
                        Timber.tag(CanvasPlaybackLogTag).e("All canvas URLs failed, stopping canvas player")
                        hasPlaybackFailed = true
                        isVideoReady = false
                        currentOnReadyChange(false)
                        exoPlayer.stop()
                    }
                }

                override fun onRenderedFirstFrame() {
                    Timber.tag(CanvasPlaybackLogTag).i("First video frame rendered on Canvas! player=$playerId isVideoReady = true")
                    isVideoReady = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "$playbackState"
                    }
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas player playbackState: $stateName, player=$playerId playWhenReady=${exoPlayer.playWhenReady}, isPlaying=${exoPlayer.isPlaying}")
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onPlayWhenReadyChanged: player=$playerId playWhenReady=$playWhenReady, reason=$reason")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onIsPlayingChanged: player=$playerId isPlaying=$isPlaying")
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() == normalized) return@LaunchedEffect
        hasPlaybackFailed = false
        isVideoReady = false
        currentOnReadyChange(false)
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        Timber.tag(CanvasPlaybackLogTag).d("Preparing mediaItem: player=$playerId url=$normalized, mimeType=$mimeType, isPlaying=$isPlaying")
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    ContentFrame(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop

        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit

        else -> ContentScale.Fit
    }

internal fun Player.setCanvasPlayback(
    isPlaying: Boolean,
    isStarted: Boolean = true,
) {
    if (isPlaying && isStarted) {
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "PlayerCanvas"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
