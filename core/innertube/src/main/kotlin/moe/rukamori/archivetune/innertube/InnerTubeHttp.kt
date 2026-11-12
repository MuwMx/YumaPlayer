/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.proxy.RotatingProxySelector
import moe.rukamori.archivetune.innertube.utils.sha1
import moe.rukamori.archivetune.innertube.utils.youtubeLoginCookieValue
import okhttp3.Dispatcher
import okhttp3.Dns
import java.io.IOException
import java.net.Proxy

internal class InnerTubeHttp {
    private companion object {
        const val HTTP_HEADER_ACCEPT_LANGUAGE = "Accept-Language"
        const val HTTP_HEADER_CACHE_CONTROL = "Cache-Control"
        const val PLAYBACK_TELEMETRY_ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        const val PLAYBACK_TELEMETRY_CACHE_CONTROL = "no-cache"
    }

    object TimingHook {
        @Volatile
        var onRetry: ((attempt: Int, delayMs: Long) -> Unit)? = null
    }

    var proxy: Proxy? = null
        set(value) {
            field = value
            rebuildClient()
        }

    var proxyUsername: String? = null
        set(value) {
            field = value
            rebuildClient()
        }

    var proxyPassword: String? = null
        set(value) {
            field = value
            rebuildClient()
        }

    var proxySelector: RotatingProxySelector? = null
        set(value) {
            field = value
            rebuildClient()
        }

    var dns: Dns = Dns.SYSTEM
        set(value) {
            field = value
            rebuildClient()
        }

    private var httpClient: HttpClient = createClient()

    val client: HttpClient
        get() = httpClient

    private fun rebuildClient() {
        httpClient.close()
        httpClient = createClient()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() =
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                )
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            engine {
                config {
                    dns(this@InnerTubeHttp.dns)
                    dispatcher(
                        Dispatcher().apply {
                            maxRequests = 64
                            maxRequestsPerHost = 16
                        },
                    )
                    val sel = this@InnerTubeHttp.proxySelector
                    if (sel != null) {
                        proxySelector(sel)
                    } else if (this@InnerTubeHttp.proxy == null) {
                        proxy(Proxy.NO_PROXY)
                    } else if (this@InnerTubeHttp.proxy != null && !proxyUsername.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                        proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                return@proxyAuthenticator null
                            }
                            val credential = okhttp3.Credentials.basic(proxyUsername!!, proxyPassword!!)
                            response.request
                                .newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build()
                        }
                    }
                }
                if (this@InnerTubeHttp.proxySelector == null && this@InnerTubeHttp.proxy != null) {
                    proxy = this@InnerTubeHttp.proxy
                }
            }

            defaultRequest {
                url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            }
        }

    fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        authState: PlaybackAuthState,
        includeVisitorData: Boolean = true,
    ) {
        val requestOrigin = client.requestOrigin()
        val requestReferer = client.requestReferer()
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", requestOrigin)
            append("Referer", requestReferer)
            if (includeVisitorData) {
                authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            }
            if (setLogin && client.loginSupported) {
                authState.cookie?.let { cookie ->
                    append("cookie", cookie)
                    val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    fun HttpRequestBuilder.ytPlaybackTrackingClient(
        client: YouTubeClient,
        authState: PlaybackAuthState,
    ) {
        val requestOrigin = client.requestOrigin()
        contentType(ContentType.Application.Json)
        headers {
            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
            append(HTTP_HEADER_ACCEPT_LANGUAGE, PLAYBACK_TELEMETRY_ACCEPT_LANGUAGE)
            append(HTTP_HEADER_CACHE_CONTROL, PLAYBACK_TELEMETRY_CACHE_CONTROL)
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", requestOrigin)
            append("Referer", client.requestReferer())
            authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            if (client.loginSupported) {
                authState.cookie?.let { cookie ->
                    append("cookie", cookie)
                    val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
            }
        }
        userAgent(client.userAgent)
    }

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        val resolvedMaxAttempts = proxySelector?.activeCount()?.coerceIn(maxAttempts, 6) ?: maxAttempts
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException || !e.isTransientNetworkFailure()) throw e
                attempt++
                proxySelector?.markLastSelectedFailed()
                proxySelector?.rotate()
                if (attempt >= resolvedMaxAttempts) throw e
                runCatching { TimingHook.onRetry?.invoke(attempt, currentDelay) }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException || current is HttpRequestTimeoutException) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    suspend fun HttpResponse.ensureNoInnertubeError(action: String): HttpResponse {
        val rawBody = bodyAsText()
        if (rawBody.contains("\"error\"")) {
            throw IOException("InnerTube $action refused by server: ${rawBody.take(300)}")
        }
        return this
    }
}
