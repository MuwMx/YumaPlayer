/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

internal object PaxsenixApi {
    const val BASE_URL = "https://api.paxsenix.org/"
    const val STATS_URL = "https://lyrics.paxsenix.org/api/stats"

    @Volatile
    private var apiKey: String = ""

    var userAgent: String = "ArchiveTune"

    fun setUserAgent(
        appName: String,
        versionName: String,
    ) {
        userAgent = "$appName/$versionName"
    }

    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey.trim()
    }

    @Volatile
    private var customClient: HttpClient? = null

    fun setClient(client: HttpClient) {
        synchronized(this) {
            customClient = client
        }
    }

    val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val defaultOkHttpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    private val defaultClient: HttpClient by lazy {
        createDefaultClient()
    }

    private fun createDefaultClient(): HttpClient =
        HttpClient(OkHttp) {
            engine {
                preconfigured = defaultOkHttpClient
            }

            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }

            expectSuccess = false
        }

    val client: HttpClient
        get() = customClient ?: defaultClient

    fun resolveUrl(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else "$BASE_URL${path.removePrefix("/")}"

    suspend fun apiGet(
        path: String,
        request: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val currentApiKey = apiKey
        check(currentApiKey.isNotEmpty()) { "Paxsenix API key is not configured" }

        return client.get(resolveUrl(path)) {
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "application/json, text/plain, */*")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            header(HttpHeaders.Authorization, "Bearer $currentApiKey")
            request()
        }
    }

    suspend fun apiBody(
        path: String,
        request: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val response = apiGet(path, request)
        val body = response.body<String>()
        check(response.status.value in 200..299) {
            "Paxsenix request failed with HTTP ${response.status.value}"
        }
        return body
    }

    suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    fun resolveDurationMs(durationSeconds: Int): Long =
        durationSeconds.coerceAtLeast(0) * 1_000L
}
