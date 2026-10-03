/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyGraphqlClient
import moe.rukamori.archivetune.spotify.arr
import moe.rukamori.archivetune.spotify.str
import java.util.Locale
import java.util.concurrent.TimeUnit

object SpotifyGqlExecutor {
    const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"

    val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    fun randomUserAgent(): String {
        val os = arrayOf("Windows NT 10.0; Win64; x64", "Macintosh; Intel Mac OS X 10_15_7", "X11; Linux x86_64").random()
        val chromeMajor = 140 - (0..4).random()
        val chromePatch = (0..499).random()
        return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.$chromePatch.0 Safari/537.36"
    }

    val restClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                url("https://api.spotify.com/v1/")
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
                header("Accept-Language", Locale.getDefault().toLanguageTag())
            }
            expectSuccess = false
        }
    }

    val gqlClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                }
            }
            defaultRequest {
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
                header("Accept", "application/json")
                header("Accept-Language", Locale.getDefault().toLanguageTag())
            }
            expectSuccess = false
        }
    }

    suspend fun parseErrorResponseBody(response: HttpResponse): String =
        try { response.bodyAsText() } catch (_: Exception) { "" }

    suspend fun checkResponse(response: HttpResponse, method: String, endpoint: String): HttpResponse {
        if (response.status == HttpStatusCode.Unauthorized) {
            throw Spotify.SpotifyException(401, "Token expired or invalid")
        }
        if (response.status.value !in 200..299) {
            val bodyText = parseErrorResponseBody(response)
            SpotifyGraphqlClient.log("E", "REST $method $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
            throw Spotify.SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
        }
        return response
    }

    fun requireToken(endpoint: String, method: String = "REST"): String =
        SpotifyGraphqlClient.accessToken ?: throw Spotify.SpotifyException(401, "Not authenticated").also {
            SpotifyGraphqlClient.log("E", "$method $endpoint — no token")
        }

    class GqlResult(
        val json: JsonObject?,
        val isPersistedQueryNotFound: Boolean,
    )

    suspend fun executeGqlWithRetries(
        operationName: String,
        token: String,
        body: JsonObject,
    ): GqlResult {
        val maxRetries = 3
        for (attempt in 0 until maxRetries) {
            val retrySuffix = if (attempt > 0) " [retry $attempt]" else ""
            SpotifyGraphqlClient.log("D", "GQL POST $operationName (token: ${token.take(8)}...)$retrySuffix")

            val response = gqlClient.post(GQL_URL) {
                header("Authorization", "Bearer $token")
                setBody(TextContent(body.toString(), ContentType.Application.Json.withParameter("charset", "UTF-8")))
            }

            SpotifyGraphqlClient.log("D", "GQL POST $operationName -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw Spotify.SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (attempt < maxRetries - 1) {
                    SpotifyGraphqlClient.log("W", "GQL $operationName -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw Spotify.SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (response.status == HttpStatusCode.PreconditionFailed) return GqlResult(json = null, isPersistedQueryNotFound = true)
            if (response.status.value !in 200..299) {
                val bodyText = parseErrorResponseBody(response)
                SpotifyGraphqlClient.log("E", "GQL $operationName FAILED: ${response.status.value} — ${bodyText.take(200)}")
                throw Spotify.SpotifyException(response.status.value, "GraphQL error ${response.status.value}: $bodyText")
            }

            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val errors = responseJson.arr("errors")
            if (errors != null && errors.isNotEmpty()) {
                val errorMsg = errors[0].jsonObject.str("message") ?: "Unknown GraphQL error"
                if (errorMsg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                    return GqlResult(json = null, isPersistedQueryNotFound = true)
                }
                SpotifyGraphqlClient.log("E", "GQL $operationName returned error: $errorMsg")
                throw Spotify.SpotifyException(400, "GraphQL: $errorMsg")
            }

            return GqlResult(json = responseJson, isPersistedQueryNotFound = false)
        }

        throw Spotify.SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token = requireToken(endpoint, "REST")
        val maxRetries = if (failFastOn429) 1 else 3
        val maxRetryDelaySec = 3L
        for (attempt in 0 until maxRetries) {
            val retrySuffix = if (attempt > 0) " [retry $attempt]" else ""
            SpotifyGraphqlClient.log("D", "REST GET $endpoint (token: ${token.take(8)}...)$retrySuffix")
            val response = restClient.get(endpoint) {
                header("Authorization", "Bearer $token")
                block()
            }
            SpotifyGraphqlClient.log("D", "REST GET $endpoint -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw Spotify.SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (failFastOn429 || retryAfter > maxRetryDelaySec) {
                    SpotifyGraphqlClient.log("W", "REST $endpoint -> 429, failing fast (retryAfter=${retryAfter}s)")
                    throw Spotify.SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
                }
                if (attempt < maxRetries - 1) {
                    SpotifyGraphqlClient.log("W", "REST $endpoint -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw Spotify.SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            return checkResponse(response, "GET", endpoint).body()
        }

        throw Spotify.SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    suspend inline fun <reified T> restPost(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token = requireToken(endpoint, "REST POST")
        val response =
            restClient.post(endpoint) {
                header("Authorization", "Bearer $token")
                if (body != null) setBody(body)
                block()
            }
        return checkResponse(response, "POST", endpoint).body()
    }

    suspend inline fun <reified T> restPut(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token = requireToken(endpoint, "REST PUT")
        val response =
            restClient.put(endpoint) {
                header("Authorization", "Bearer $token")
                if (body != null) setBody(body)
                block()
            }
        return checkResponse(response, "PUT", endpoint).body()
    }

    suspend inline fun <reified T> restDelete(
        endpoint: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token = requireToken(endpoint, "REST DELETE")
        val response =
            restClient.delete(endpoint) {
                header("Authorization", "Bearer $token")
                block()
            }
        return checkResponse(response, "DELETE", endpoint).body()
    }
}
