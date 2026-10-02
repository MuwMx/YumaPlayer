/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune

import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.lyrics.SharedLyricsEngine
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.utils.ProxyUtils
import moe.rukamori.archivetune.utils.YTPlayerUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import okhttp3.Dns
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Proxy
import kotlin.system.exitProcess

internal fun App.initializeDeferredAsync() {
    applicationScope.launch(Dispatchers.IO) {
        try {
            syncUtils.trySpotifyAutoSync()
        } catch (e: Exception) {
            Timber.e(e, "Error restoring Spotify auto-sync on app startup")
        }
    }

    applicationScope.launch(Dispatchers.IO) {
        try {
            val prefs = dataStore.data.first()

            prefs[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { country ->
                YouTube.locale = YouTube.locale.copy(gl = country)
            }
            prefs[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }?.let { lang ->
                YouTube.locale = YouTube.locale.copy(hl = lang)
            }

            LastFmServiceConfig.fromPreferences(prefs).apply(prefs[LastFMSessionKey])

            ProxyUtils.applyYouTubeProxy(
                enabled = prefs[ProxyEnabledKey] == true,
                type = prefs[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                host = prefs[ProxyHostKey],
                port = prefs[ProxyPortKey],
                username = prefs[ProxyUsernameKey],
                password = prefs[ProxyPasswordKey],
            )
            YouTube.streamBypassProxy = YouTube.proxy != null && prefs[StreamBypassProxyKey] == true

            if (prefs[IpRotationEnabledKey] == true) {
                try {
                    YouTube.enableIpRotation()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            SharedLyricsEngine.update()

            if (prefs[UseLoginForBrowse] != false) {
                YouTube.useLoginForBrowse = true
            }

            val initialVisitor = prefs[VisitorDataKey] ?: YouTube.visitorData
            if (!initialVisitor.isNullOrBlank()) {
                applicationScope.launch(Dispatchers.IO) {
                    BotGuardTokenGenerator.preWarm(initialVisitor)
                }
            }

            if (prefs[RandomThemeOnStartupKey] == true) {
                val randomPalette = ThemeSeedPaletteCodec.ThemePalettes.generateRandomPalette()
                val seedPalette =
                    ThemeSeedPalette(
                        primary = randomPalette.primary,
                        secondary = randomPalette.secondary,
                        tertiary = randomPalette.tertiary,
                        neutral = randomPalette.neutral,
                    )
                val encodedPalette = ThemeSeedPaletteCodec.encodeForPreference(seedPalette, "Random")
                dataStore.edit { settings ->
                    settings[CustomThemeColorKey] = encodedPalette
                }
            }

            isInitialized = true
        } catch (e: Exception) {
            Timber.e(e, "Error during deferred initialization")
            reportException(e)
        }
    }

    applicationScope.launch(Dispatchers.IO) {
        dataStore.data
            .map {
                Triple(
                    it[EnableDnsOverHttpsKey] ?: false,
                    it[DnsOverHttpsProviderKey] ?: "Cloudflare",
                    it[stringPreferencesKey("customDnsUrl")] ?: "https://",
                )
            }.distinctUntilChanged()
            .collect { (enabled, provider, customUrl) ->
                if (enabled) {
                    val dnsProviderUrls =
                        mapOf(
                            "Google" to "https://dns.google/dns-query",
                            "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                            "AdGuard" to "https://dns.adguard.com/dns-query",
                            "Quad9" to "https://dns.quad9.net/dns-query",
                        )
                    val url = if (provider == "Custom") customUrl else dnsProviderUrls[provider]
                    if (!url.isNullOrBlank() && url.startsWith("https://")) {
                        runCatching {
                            YouTube.dns = YouTube.createDnsOverHttps(url)
                        }
                    } else {
                        YouTube.dns = Dns.SYSTEM
                    }
                } else {
                    YouTube.dns = Dns.SYSTEM
                }
                SharedLyricsEngine.update()
            }
    }

    applicationScope.launch(Dispatchers.IO) {
        dataStore.data
            .map { it.toPlaybackAuthState() }
            .distinctUntilChanged()
            .collect { authState ->
                val previousFingerprint = YouTube.currentPlaybackAuthState().fingerprint
                YouTube.authState = authState
                if (previousFingerprint != authState.fingerprint) {
                    YTPlayerUtils.clearPlaybackAuthCaches()
                    val newSessionId = authState.sessionId
                    if (!newSessionId.isNullOrBlank()) {
                        BotGuardTokenGenerator.preWarm(newSessionId)
                    }
                }
            }
    }

    applicationScope.launch(Dispatchers.IO) {
        dataStore.data
            .map { it.toPlaybackAuthState().visitorData }
            .distinctUntilChanged()
            .collect { visitorData ->
                if (!visitorData.isNullOrBlank()) return@collect
                YouTube
                    .visitorData()
                    .onFailure {
                        reportException(it)
                    }.getOrNull()
                    ?.also { newVisitorData ->
                        dataStore.edit { settings ->
                            settings[VisitorDataKey] = newVisitorData
                        }
                    }
            }
    }

    try {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val rawStack = sw.toString()

                val maxPayloadChars = 65_536
                val safeStack = if (rawStack.length > maxPayloadChars) {
                    rawStack.take(maxPayloadChars) + "\n\n... [TRUNCATED DUE TO BINDER IPC LIMIT]"
                } else {
                    rawStack
                }

                val intent =
                    Intent(this@initializeDeferredAsync, DebugActivity::class.java).apply {
                        putExtra(DebugActivity.EXTRA_STACK_TRACE, safeStack)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                startActivity(intent)
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(2)
            }
        }
    } catch (e: Exception) {
        reportException(e)
    }

    applicationScope.launch(Dispatchers.IO) {
        dataStore.data
            .map { prefs ->
                LastFmServiceConfig.fromPreferences(prefs) to prefs[LastFMSessionKey]
            }.distinctUntilChanged()
            .collect { (serviceConfig, sessionKey) ->
                serviceConfig.apply(sessionKey)
            }
    }
}
