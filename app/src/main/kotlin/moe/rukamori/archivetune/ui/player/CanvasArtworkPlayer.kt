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
        Timber.tag(CanvasPlaybackLogTag).d("Canvas isPlaying state changed: isPlaying=$isPlaying, hasPlaybackFailed=$hasPlaybackFailed")
        if (!hasPlaybackFailed) {
            exoPlayer.setCanvasPlayback(
                isPlaying = isPlaying,
                isStarted = true,
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
                    } else {
                        Timber.tag(CanvasPlaybackLogTag).e("All canvas URLs failed, stopping canvas player")
                        hasPlaybackFailed = true
                        exoPlayer.stop()
                    }
                }

                override fun onRenderedFirstFrame() {
                    Timber.tag(CanvasPlaybackLogTag).i("First video frame rendered on Canvas! isVideoReady = true")
                    isVideoReady = true
                    if (shouldPlay && !hasPlaybackFailed) {
                        exoPlayer.setCanvasPlayback(
                            isPlaying = true,
                            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        )
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "$playbackState"
                    }
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas player playbackState: $stateName, playWhenReady=${exoPlayer.playWhenReady}, isPlaying=${exoPlayer.isPlaying}")
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
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onPlayWhenReadyChanged: playWhenReady=$playWhenReady, reason=$reason")
                    if (shouldPlay && !playWhenReady && !hasPlaybackFailed) {
                        exoPlayer.setCanvasPlayback(
                            isPlaying = true,
                            isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                        )
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onIsPlayingChanged: isPlaying=$isPlaying")
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

        Timber.tag(CanvasPlaybackLogTag).d("Preparing mediaItem: url=$normalized, mimeType=$mimeType, isPlaying=$isPlaying")
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying, isStarted = true)
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
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "PlayerCanvas"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
