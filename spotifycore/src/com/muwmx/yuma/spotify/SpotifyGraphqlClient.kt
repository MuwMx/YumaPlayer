/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.TimeUnit

typealias SpotifyException = Spotify.SpotifyException

internal fun JsonObject.obj(key: String): JsonObject? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonObject
    } catch (_: Exception) {
        null
    }

internal fun JsonObject.str(key: String): String? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }

internal fun JsonObject.int(key: String): Int? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
    } catch (_: Exception) {
        null
    }

internal fun JsonObject.arr(key: String): JsonArray? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonArray
    } catch (_: Exception) {
        null
    }

object SpotifyGraphqlClient {
    @Volatile
    var accessToken: String? = null

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null

    @Volatile
    var onHashExpired: ((operationName: String) -> Unit)? = null

    fun isAuthenticated(): Boolean = accessToken != null

    const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"

    fun randomUserAgent(): String {
        val osOptions =
            arrayOf(
                "Windows NT 10.0; Win64; x64",
                "Macintosh; Intel Mac OS X 10_15_7",
                "X11; Linux x86_64",
            )
        val chromeBase = 140
        val chromeMajor = chromeBase - (0..4).random()
        val chromePatch = (0..499).random()
        val os = osOptions.random()
        return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeMajor.0.$chromePatch.0 Safari/537.36"
    }

    val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    val restClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                url("https://api.spotify.com/v1/")
                header("User-Agent", randomUserAgent())
                header("app-platform", "WebPlayer")
                header("Origin", "https://open.spotify.com")
                header("Referer", "https://open.spotify.com/")
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
            }
            expectSuccess = false
        }
    }

    fun log(
        level: String,
        message: String,
    ) {
        logger?.invoke(level, message)
    }

    suspend fun parseErrorResponseBody(response: HttpResponse): String =
        try {
            response.bodyAsText()
        } catch (_: Exception) {
            ""
        }

    suspend fun graphqlPost(
        operationName: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "GQL $operationName — no token")
            }

        val primaryHash = SpotifyHashProvider.getHash(operationName)
        val hashCandidates =
            buildList {
                add(primaryHash)
                SpotifyHashProvider.getPreviousHash(operationName)?.let { prev ->
                    if (prev != primaryHash) add(prev)
                }
            }

        for ((hashIdx, sha256Hash) in hashCandidates.withIndex()) {
            val body = buildGqlBody(operationName, sha256Hash, variables)
            val result = executeGqlWithRetries(operationName, token, body)

            if (result.isPersistedQueryNotFound) {
                if (hashIdx < hashCandidates.lastIndex) {
                    log("W", "GQL $operationName hash rejected, trying previous_hash")
                    continue
                }
                log("E", "GQL $operationName all known hashes rejected, triggering remote refresh")
                onHashExpired?.invoke(operationName)
                throw SpotifyException(412, "PersistedQueryNotFound for $operationName — hash may have rotated")
            }

            return result.json!!
        }

        throw SpotifyException(412, "No valid hash found for $operationName")
    }

    private fun buildGqlBody(
        operationName: String,
        sha256Hash: String,
        variables: JsonObject,
    ): JsonObject =
        buildJsonObject {
            put("variables", variables)
            put("operationName", operationName)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", sha256Hash)
                }
            }
        }

    private class GqlResult(
        val json: JsonObject?,
        val isPersistedQueryNotFound: Boolean,
    )

    private suspend fun executeGqlWithRetries(
        operationName: String,
        token: String,
        body: JsonObject,
    ): GqlResult {
        val maxRetries = 3
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "GQL POST $operationName (token: ${token.take(8)}...)" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )

            val response =
                gqlClient.post(GQL_URL) {
                    header("Authorization", "Bearer $token")
                    setBody(
                        TextContent(
                            body.toString(),
                            ContentType.Application.Json.withParameter("charset", "UTF-8"),
                        ),
                    )
                }

            log("D", "GQL POST $operationName -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (attempt < maxRetries - 1) {
                    log("W", "GQL $operationName -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (response.status == HttpStatusCode.PreconditionFailed) {
                return GqlResult(json = null, isPersistedQueryNotFound = true)
            }
            if (response.status.value !in 200..299) {
                val bodyText = parseErrorResponseBody(response)
                log("E", "GQL $operationName FAILED: ${response.status.value} — ${bodyText.take(200)}")
                throw SpotifyException(response.status.value, "GraphQL error ${response.status.value}: $bodyText")
            }

            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val errors = responseJson.arr("errors")
            if (errors != null && errors.isNotEmpty()) {
                val errorMsg = errors[0].jsonObject.str("message") ?: "Unknown GraphQL error"
                if (errorMsg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                    return GqlResult(json = null, isPersistedQueryNotFound = true)
                }
                log("E", "GQL $operationName returned error: $errorMsg")
                throw SpotifyException(400, "GraphQL: $errorMsg")
            }

            return GqlResult(json = responseJson, isPersistedQueryNotFound = false)
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST $endpoint — no token")
            }

        val maxRetries = if (failFastOn429) 1 else 3
        val maxRetryDelaySec = 3L
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "REST GET $endpoint (token: ${token.take(8)}...)" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )
            val response =
                restClient.get(endpoint) {
                    header("Authorization", "Bearer $token")
                    block()
                }
            log("D", "REST GET $endpoint -> ${response.status.value}")

            if (response.status == HttpStatusCode.Unauthorized) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: (2L * (attempt + 1))
                if (failFastOn429 || retryAfter > maxRetryDelaySec) {
                    log("W", "REST $endpoint -> 429, failing fast (retryAfter=${retryAfter}s)")
                    throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
                }
                if (attempt < maxRetries - 1) {
                    log("W", "REST $endpoint -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (response.status.value !in 200..299) {
                val bodyText = parseErrorResponseBody(response)
                log("E", "REST $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
                throw SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
            }
            return response.body()
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    suspend inline fun <reified T> restGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = authenticatedGet(endpoint, failFastOn429, block)

    suspend inline fun <reified T> restPost(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST POST $endpoint — no token")
            }
        val response =
            restClient.post(endpoint) {
                header("Authorization", "Bearer $token")
                if (body != null) {
                    setBody(body)
                }
                block()
            }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SpotifyException(401, "Token expired or invalid")
        }
        if (response.status.value !in 200..299) {
            val bodyText = parseErrorResponseBody(response)
            log("E", "REST POST $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
            throw SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
        }
        return response.body()
    }

    suspend inline fun <reified T> restPut(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST PUT $endpoint — no token")
            }
        val response =
            restClient.put(endpoint) {
                header("Authorization", "Bearer $token")
                if (body != null) {
                    setBody(body)
                }
                block()
            }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SpotifyException(401, "Token expired or invalid")
        }
        if (response.status.value !in 200..299) {
            val bodyText = parseErrorResponseBody(response)
            log("E", "REST PUT $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
            throw SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
        }
        return response.body()
    }

    suspend inline fun <reified T> restDelete(
        endpoint: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST DELETE $endpoint — no token")
            }
        val response =
            restClient.delete(endpoint) {
                header("Authorization", "Bearer $token")
                block()
            }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SpotifyException(401, "Token expired or invalid")
        }
        if (response.status.value !in 200..299) {
            val bodyText = parseErrorResponseBody(response)
            log("E", "REST DELETE $endpoint FAILED: ${response.status.value} — ${bodyText.take(200)}")
            throw SpotifyException(response.status.value, "Spotify API error ${response.status.value}: $bodyText")
        }
        return response.body()
    }
}
