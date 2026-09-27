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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import moe.rukamori.archivetune.lyrics.SharedLyricsEngine
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Credentials
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L
private const val CanvasMaxVideoWidth = 1_920
private const val CanvasMaxVideoHeight = 1_920

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
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val initial = primary ?: fallback ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var hasPlaybackFailed by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying)

    LaunchedEffect(primary, fallback) {
        val target = primary ?: fallback
        if (target != null && target != currentUrl) {
            currentUrl = target
            isVideoReady = false
            hasPlaybackFailed = false
        }
    }

    val okHttpClient =
        remember {
            SharedLyricsEngine.okHttpClient
                .newBuilder()
                .dns(YouTube.dns)
                .proxy(YouTube.streamOkHttpProxy)
                .apply {
                    val username = YouTube.proxyUsername
                    val password = YouTube.proxyPassword
                    if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                        proxyAuthenticator { _, response ->
                            val credential = Credentials.basic(username, password)
                            response.request
                                .newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    }
                }.connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) {
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
        remember(initial, mediaSourceFactory) {
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
                    playWhenReady = isPlaying
                }
        }

    LaunchedEffect(isPlaying) {
        if (!hasPlaybackFailed) {
            exoPlayer.setCanvasPlayback(
                isPlaying = isPlaying,
                isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            )
        }
    }

    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary || hasPlaybackFailed) return@LaunchedEffect

        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary && !hasPlaybackFailed) {
            delay(CanvasPlaybackStallCheckIntervalMs)

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
                return@LaunchedEffect
            }

            lastPosition = currentPosition
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner, okHttpClient) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        if (!hasPlaybackFailed) {
                            exoPlayer.setCanvasPlayback(
                                isPlaying = shouldPlay,
                                isStarted = true,
                            )
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.setCanvasPlayback(false)
                    }
                    Lifecycle.Event.ON_STOP -> {
                        exoPlayer.stop()
                        okHttpClient.dispatcher.cancelAll()
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed")
                    val next =
                        when (currentUrl) {
                            primary -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false
                        hasPlaybackFailed = false
                    } else {
                        hasPlaybackFailed = true
                        exoPlayer.stop()
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    if (shouldPlay && !hasPlaybackFailed) {
                        exoPlayer.setCanvasPlayback(
                            isPlaying = true,
                            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        )
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!shouldPlay || hasPlaybackFailed) return
                    exoPlayer.setCanvasPlayback(
                        isPlaying = true,
                        isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                    )
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (shouldPlay && !playWhenReady && !hasPlaybackFailed) {
                        exoPlayer.setCanvasPlayback(
                            isPlaying = true,
                            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        )
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (shouldPlay && !isPlaying && !hasPlaybackFailed) {
                        exoPlayer.setCanvasPlayback(
                            isPlaying = true,
                            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        )
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        hasPlaybackFailed = false
        val normalized = currentUrl.trim()
        isVideoReady = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            exoPlayer.prepare()
            exoPlayer.setCanvasPlayback(isPlaying, isStarted = true)
        }
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

private fun ExoPlayer.setCanvasPlayback(
    isPlaying: Boolean,
    isStarted: Boolean = true,
) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (isStarted && playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        if (isStarted) play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "CanvasPlayback"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
