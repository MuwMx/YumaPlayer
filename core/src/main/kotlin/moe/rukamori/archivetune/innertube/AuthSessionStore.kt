/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.response.AccountMenuResponse
import moe.rukamori.archivetune.innertube.proxy.RotatingProxyClient
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Proxy

object AuthSessionStore {
    internal val innerTube = InnerTube()
    private val accountSwitcherClient = WEB.copy(loginSupported = true)
    private val mutableAuthState = MutableStateFlow(PlaybackAuthState.EMPTY)

    val authStateFlow: StateFlow<PlaybackAuthState> = mutableAuthState.asStateFlow()

    private val _historySyncEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val historySyncEvent: SharedFlow<Unit> = _historySyncEvent.asSharedFlow()

    fun notifyHistorySynced() {
        _historySyncEvent.tryEmit(Unit)
    }

    var authState: PlaybackAuthState
        get() = mutableAuthState.value
        set(value) {
            val normalized = value.normalized()
            mutableAuthState.value = normalized
            innerTube.applyAuthState(normalized)
        }

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value)
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value)
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value)
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value)
        }
    var webClientPoTokenEnabled: Boolean
        get() = authState.webClientPoTokenEnabled
        set(value) {
            authState = authState.copy(webClientPoTokenEnabled = value)
        }
    var poTokenGvs: String?
        get() = authState.poTokenGvs
        set(value) {
            authState = authState.copy(poTokenGvs = value)
        }
    var poTokenPlayer: String?
        get() = authState.poTokenPlayer
        set(value) {
            authState = authState.copy(poTokenPlayer = value)
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var proxyUsername: String?
        get() = innerTube.proxyUsername
        set(value) {
            innerTube.proxyUsername = value
        }
    var proxyPassword: String?
        get() = innerTube.proxyPassword
        set(value) {
            innerTube.proxyPassword = value
        }
    var dns: Dns
        get() = innerTube.dns
        set(value) {
            innerTube.dns = value
        }
    var streamBypassProxy: Boolean = false
    val streamProxy: Proxy?
        get() = if (streamBypassProxy) null else proxy
    val streamOkHttpProxy: Proxy
        get() = streamProxy ?: Proxy.NO_PROXY
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    val rotatingProxyClient = RotatingProxyClient()
    private val _ipRotationActiveCount = MutableStateFlow(0)
    val ipRotationActiveCount: StateFlow<Int> = _ipRotationActiveCount.asStateFlow()

    suspend fun enableIpRotation() {
        withContext(Dispatchers.IO) {
            rotatingProxyClient.fetchAndLoad()
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    suspend fun refreshIpRotation() {
        withContext(Dispatchers.IO) {
            if (rotatingProxyClient.activeCount() <= 1) {
                rotatingProxyClient.fetchAndLoad()
            } else {
                rotatingProxyClient.rotate()
            }
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    fun disableIpRotation() {
        innerTube.proxySelector = null
        _ipRotationActiveCount.value = 0
    }

    fun currentPlaybackAuthState(): PlaybackAuthState = authState

    fun createDnsOverHttps(url: String): Dns {
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps
            .Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .build()
    }

    fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String?,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String? =
        PlayerClient.resolvePlayerPoToken(
            client = client,
            explicitPoToken = explicitPoToken,
            authState = authState,
        )

    fun hasLoginCookie(): Boolean = authState.hasLoginCookie

    fun hasPlaybackLoginContext(): Boolean = authState.hasPlaybackLoginContext

    fun resolveGvsPoToken(authState: PlaybackAuthState = currentPlaybackAuthState()): String? =
        PlayerClient.resolveGvsPoToken(authState)

    fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String = PlayerClient.appendGvsPoToken(url, client, authState)

    suspend fun visitorData(): Result<String> =
        PlayerClient.visitorData()

    suspend fun accountInfo(): Result<AccountInfo> =
        runCatching {
            val response = innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            val accountInfo =
                response.actions
                    .firstOrNull()
                    ?.openPopupAction
                    ?.popup
                    ?.multiPageMenuRenderer
                    ?.header
                    ?.activeAccountHeaderRenderer
                    ?.toAccountInfo()
            accountInfo ?: throw IllegalStateException("Failed to get account info - user may not be logged in")
        }

    suspend fun accountChannels(): Result<List<AccountChannel>> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response
                .objectsNamed("accountItemRenderer")
                .mapNotNull(::parseAccountChannel)
                .sortedByDescending(AccountChannel::isSelected)
                .distinctBy(AccountChannel::dataSyncId)
                .toList()
        }

    suspend fun accountDataSyncId(): Result<String> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response.findMainAppWebDataSyncId()
                ?: response
                    .objectsNamed("accountItemRenderer")
                    .mapNotNull { renderer ->
                        val isDisabled = renderer.booleanValue("isDisabled") ?: false
                        val hasChannel = renderer.booleanValue("hasChannel") ?: true
                        if (isDisabled || !hasChannel) return@mapNotNull null

                        renderer.parseAccountChannelDataSyncId()?.let { dataSyncId ->
                            dataSyncId to (renderer.booleanValue("isSelected") ?: false)
                        }
                    }.sortedByDescending { (_, isSelected) -> isSelected }
                    .firstOrNull()
                    ?.first
                ?: throw IllegalStateException("Failed to get YouTube DataSyncId")
        }
}
