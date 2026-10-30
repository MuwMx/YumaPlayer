/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas
import moe.rukamori.archivetune.cipher.CipherBootstrap
import moe.rukamori.archivetune.cipher.CipherEngineType
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.SharedLyricsEngine
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.SpotifyHashDiscovery
import moe.rukamori.archivetune.spotify.SpotifyHashRefresher
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.ProxyAuth
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.clearPlaybackAuthSession
import moe.rukamori.archivetune.utils.clearPlaybackWebAuthSession
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@HiltAndroidApp
class App :
    Application(),
    SingletonImageLoader.Factory {
    @Inject internal lateinit var syncUtils: SyncUtils

    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile internal var isInitialized = false
    internal val didRunImageCacheTrim = AtomicBoolean(false)

    private fun currentProcessName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        val t0 = if (BuildConfig.DEBUG) moe.rukamori.archivetune.playback.PlaybackTiming.markProcessStart() else 0L
        if (BuildConfig.DEBUG) {
            moe.rukamori.archivetune.innertube.InnerTube.TimingHook.onRetry = { attempt, delayMs ->
                Timber.tag(moe.rukamori.archivetune.playback.PlaybackTiming.TAG).d(
                    "HTTP_RETRY attempt=$attempt delayMs=$delayMs tNs=${moe.rukamori.archivetune.playback.PlaybackTiming.nowNanos()}"
                )
            }
        }
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            Timber.plant(Timber.DebugTree())
            return
        }
        BotGuardTokenGenerator.initialize(this)
        PreferenceStore.start(this)
        Timber.plant(Timber.DebugTree())
        if (BuildConfig.DEBUG) {
            Timber.tag(moe.rukamori.archivetune.playback.PlaybackTiming.TAG).d("PROCESS_START tNs=$t0")
        }
        try {
            Timber.plant(moe.rukamori.archivetune.utils.GlobalLogTree())
        } catch (_: Exception) {
        }

        initializeCriticalSync()
        initializeDeferredAsync()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

    private fun initializeCriticalSync() {
        ProxyAuth.init()
        CanvasArtworkPlaybackCache.init(this)
        CipherBootstrap.init(this, CipherEngineType.QUICKJS)
        ArchiveTuneCanvas.initialize(
            bearerToken = BuildConfig.CANVAS_BEARER_TOKEN,
            tidalToken = BuildConfig.CANVAS_TIDAL_TOKEN,
        )
        SharedLyricsEngine.update()

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it in CountryCodeToName } ?: "US",
            hl = locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en",
        )
        if (languageTag == "zh-TW") KuGou.useTraditionalChinese = true
        LastFM.initialize(apiKey = BuildConfig.LASTFM_API_KEY, secret = BuildConfig.LASTFM_SECRET)
        Spotify.logger = { level, message ->
            when (level) {
                "D" -> Timber.tag("SpotifyPipeline").d(message)
                "W" -> Timber.tag("SpotifyPipeline").w(message)
                "E" -> Timber.tag("SpotifyPipeline").e(message)
                else -> Timber.tag("SpotifyPipeline").i(message)
            }
        }
        installSpotifyHashRefresher()
        YouTube.traceLogger = { message ->
            Timber.tag("ArtistItemsTrace").d(message)
        }
    }

    /** Lets the Spotify client look up a current hash when Spotify retires one the app ships with. */
    private fun installSpotifyHashRefresher() {
        val refresher =
            SpotifyHashRefresher(
                discovery =
                    SpotifyHashDiscovery(
                        fetch = { url -> SpotifyGraphqlClient.gqlClient.get(url) { header(HttpHeaders.Accept, "*/*") }.bodyAsText() },
                    ),
                store = File(filesDir, "spotify_gql_hashes.json"),
            )
        Spotify.hashRefresher = refresher
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = createAppImageLoader(context)

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(
            context: Context,
            clearWebAuthSession: Boolean = true,
        ) {
            if (clearWebAuthSession) clearPlaybackWebAuthSession(context)
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings -> settings.clearPlaybackAuthSession() }
            }
        }
    }
}
