/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.engine

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import moe.rukamori.archivetune.audiodsp.AudioDeck
import moe.rukamori.archivetune.audiodsp.CrossfadeTarget
import moe.rukamori.archivetune.audiodsp.DeckController
import moe.rukamori.archivetune.audiodsp.DjFilterAudioProcessor
import moe.rukamori.archivetune.audiodsp.DualForwardingPlayer
import moe.rukamori.archivetune.audiodsp.DualPlayerRoleHolder
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.playback.ExoDeckController
import moe.rukamori.archivetune.playback.PlaybackConstants
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.moriextractor.StreamingExtractionManager
import moe.rukamori.archivetune.playback.resolvers.StreamUrlCache
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import moe.rukamori.archivetune.utils.ProxyAuth
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@UnstableApi
class PlayerEngineHolder {
    lateinit var localPlayer: ExoPlayer
    lateinit var player: Player
    lateinit var controller: DeckController

    val isControllerInitialized: Boolean get() = ::controller.isInitialized
    val activeDeck: AudioDeck get() = controller.activeDeck
    val transitionDeck: AudioDeck? get() = if (isControllerInitialized) controller.transitionDeck else null

    val dualForwardingPlayer: DualForwardingPlayer
        get() = (controller as ExoDeckController).dualForwardingPlayer
    val dualPlayerRoleHolder: DualPlayerRoleHolder
        get() = if (isControllerInitialized) (controller as ExoDeckController).dualPlayerRoleHolder else DualPlayerRoleHolder()
    val secondaryCrossfadePlayer: ExoPlayer?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadePlayer else null
    var secondaryCrossfadeTarget: CrossfadeTarget?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadeTarget else null
        set(value) { if (isControllerInitialized) (controller as? ExoDeckController)?.secondaryCrossfadeTarget = value }
    val reserveCrossfadePlayer: ExoPlayer?
        get() = if (isControllerInitialized) (controller as? ExoDeckController)?.reserveCrossfadePlayer else null

    val djFilterByPlayer = ConcurrentHashMap<ExoPlayer, DjFilterAudioProcessor>()

    val mediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(YouTube.streamOkHttpProxy)
            .proxyAuthenticator(ProxyAuth.proxyAuthenticator)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                val isYouTubeMediaHost =
                    host.endsWith("googlevideo.com") ||
                        host.endsWith("googleusercontent.com") ||
                        host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com")

                if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

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

    val extractorMediaOkHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                        ).header("Accept", "*/*")
                        .build()
                chain.proceed(request)
            }.build()
    }

    fun evictMediaConnectionPools() {
        runCatching { mediaOkHttpClient.connectionPool.evictAll() }
        runCatching { extractorMediaOkHttpClient.connectionPool.evictAll() }
    }

    val playbackUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
    val losslessUrlCache = StreamUrlCache()
    val extractorPlaybackUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
    val remotePlaybackTrackingUrlCache = ConcurrentHashMap<String, String>()
    val contentLengthCache = ConcurrentHashMap<String, Long>()

    fun invalidatePlaybackUrlCache(mediaId: String) {
        playbackUrlCache.remove(mediaId)
        playbackUrlCache.remove(flacCacheKey(mediaId))
        playbackUrlCache.remove("${mediaId}_${PlaybackSource.FLAC.name}")
        playbackUrlCache.remove(ytStreamCacheKey(mediaId))
        playbackUrlCache.remove(flacStreamCacheKey(mediaId))
        playbackUrlCache.remove("${mediaId}_${PlaybackSource.YT_MUSIC.name}")
    }

    fun invalidateLosslessUrlCache(mediaId: String) {
        losslessUrlCache.remove(mediaId)
        losslessUrlCache.remove("${mediaId}_${PlaybackSource.YT_MUSIC.name}")
        losslessUrlCache.remove(flacCacheKey(mediaId))
        losslessUrlCache.remove(ytStreamCacheKey(mediaId))
        losslessUrlCache.remove(flacStreamCacheKey(mediaId))
        losslessUrlCache.remove("${mediaId}_${PlaybackSource.FLAC.name}")
    }

    val streamingExtractionManager by lazy {
        StreamingExtractionManager(
            bearerToken = moe.rukamori.archivetune.BuildConfig.EXTRACTOR_BEARER,
        )
    }

    fun djFilterFor(player: ExoPlayer?): DjFilterAudioProcessor? = player?.let { djFilterByPlayer[it] }

    fun resetDjFilters(vararg players: ExoPlayer?) {
        for (player in players) {
            djFilterFor(player)?.clearAutomation()
        }
    }

    fun removeDjFilterFromLocalPlayer() {
        runCatching { djFilterByPlayer.remove(localPlayer) }
    }

    fun transferAudioEffects(to: ExoPlayer, listener: Player.Listener) {
        localPlayer.removeListener(listener)
        to.addListener(listener)
    }

    fun updateAudioOffload(enabled: Boolean, crossfadeEnabled: Boolean) {
        val effectiveEnabled = enabled && !crossfadeEnabled
        runCatching {
            val builder = localPlayer.trackSelectionParameters.buildUpon()
            val audioOffloadPrefsClass = Class.forName("androidx.media3.common.AudioOffloadPreferences")
            val audioOffloadPrefsBuilderClass = Class.forName("androidx.media3.common.AudioOffloadPreferences\$Builder")

            val modeFieldName = if (effectiveEnabled) "AUDIO_OFFLOAD_MODE_ENABLED" else "AUDIO_OFFLOAD_MODE_DISABLED"
            val mode = audioOffloadPrefsClass.getField(modeFieldName).getInt(null)

            val prefsBuilder = audioOffloadPrefsBuilderClass.getDeclaredConstructor().newInstance()
            audioOffloadPrefsBuilderClass.getMethod("setAudioOffloadMode", Int::class.javaPrimitiveType).invoke(prefsBuilder, mode)
            val prefs = audioOffloadPrefsBuilderClass.getMethod("build").invoke(prefsBuilder)

            val setMethod =
                builder.javaClass.methods.firstOrNull { method ->
                    method.name == "setAudioOffloadPreferences" && method.parameterTypes.size == 1
                }
            if (setMethod != null) {
                setMethod.invoke(builder, prefs)
                localPlayer.trackSelectionParameters = builder.build()
            }
        }
        localPlayer.setOffloadEnabled(effectiveEnabled)
    }

    fun createPrimaryLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                PlaybackConstants.PRIMARY_MIN_BUFFER_MS,
                PlaybackConstants.PRIMARY_MAX_BUFFER_MS,
                PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_MS,
                PlaybackConstants.PRIMARY_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    fun createCrossfadeLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                PlaybackConstants.CROSSFADE_MIN_BUFFER_MS,
                PlaybackConstants.CROSSFADE_MAX_BUFFER_MS,
                PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
                PlaybackConstants.CROSSFADE_MIN_BUFFER_BEFORE_START_MS.toInt(),
            ).setPrioritizeTimeOverSizeThresholds(true)
            .build()

    fun createRenderersFactory(context: Context, djFilter: DjFilterAudioProcessor) =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        SonicAudioProcessor(),
                        djFilter,
                    ),
                ).build()
        }

    fun releaseReserveAndTransitionDecks() {
        if (isControllerInitialized) {
            (controller as? ExoDeckController)?.releaseReserveDeck()
            (controller as? ExoDeckController)?.releaseTransitionDeck()
        }
    }

    fun releasePlayer() {
        player.release()
    }
}
