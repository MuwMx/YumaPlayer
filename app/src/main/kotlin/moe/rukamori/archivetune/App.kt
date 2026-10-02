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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.kugou.KuGou
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.lyrics.SharedLyricsEngine
import moe.rukamori.archivetune.obfuscator.MoriCipherConfig
import moe.rukamori.archivetune.obfuscator.MoriCipherRuntime
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
            Application.getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (currentProcessName()?.endsWith(":crash") == true) {
            Timber.plant(Timber.DebugTree())
            return
        }
        BotGuardTokenGenerator.initialize(this)
        PreferenceStore.start(this)
        Timber.plant(Timber.DebugTree())
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
        MoriCipherRuntime.initialize(
            MoriCipherConfig(
                cacheDirectory = File(noBackupFilesDir, "mori_cipher"),
                proxyProvider = { YouTube.streamProxy },
            ),
        )
        ArchiveTuneCanvas.initialize(BuildConfig.CANVAS_BEARER_TOKEN)
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
        moe.rukamori.archivetune.spotify.Spotify.logger = { level, message ->
            when (level) {
                "D" -> Timber.tag("SpotifyPipeline").d(message)
                "W" -> Timber.tag("SpotifyPipeline").w(message)
                "E" -> Timber.tag("SpotifyPipeline").e(message)
                else -> Timber.tag("SpotifyPipeline").i(message)
            }
        }
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
