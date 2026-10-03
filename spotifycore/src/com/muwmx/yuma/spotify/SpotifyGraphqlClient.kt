/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.spotify.net.SpotifyGqlExecutor
import moe.rukamori.archivetune.spotify.net.SpotifyGqlPayloadBuilder

typealias SpotifyException = Spotify.SpotifyException

internal fun JsonObject.obj(key: String): JsonObject? =
    try { this[key]?.takeIf { it !is JsonNull }?.jsonObject } catch (_: Exception) { null }

internal fun JsonObject.str(key: String): String? =
    try { this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }

internal fun JsonObject.int(key: String): Int? =
    try { this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }

internal fun JsonObject.arr(key: String): JsonArray? =
    try { this[key]?.takeIf { it !is JsonNull }?.jsonArray } catch (_: Exception) { null }

internal fun JsonObject.bool(key: String): Boolean? =
    try { this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull } catch (_: Exception) { null }

object SpotifyGraphqlClient {
    @Volatile
    var accessToken: String? = null

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null

    @Volatile
    var onHashExpired: ((operationName: String) -> Unit)? = null

    fun isAuthenticated(): Boolean = accessToken != null

    const val GQL_URL = SpotifyGqlExecutor.GQL_URL

    val json get() = SpotifyGqlExecutor.json
    val restClient get() = SpotifyGqlExecutor.restClient
    val gqlClient get() = SpotifyGqlExecutor.gqlClient

    fun log(level: String, message: String) { logger?.invoke(level, message) }

    suspend fun parseErrorResponseBody(response: HttpResponse): String =
        SpotifyGqlExecutor.parseErrorResponseBody(response)

    suspend fun graphqlPost(
        operationName: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject {
        val token = accessToken ?: throw Spotify.SpotifyException(401, "Not authenticated").also {
            log("E", "GQL $operationName — no token")
        }

        val hashCandidates = SpotifyGqlPayloadBuilder.resolveHashCandidates(operationName)
        for ((hashIdx, sha256Hash) in hashCandidates.withIndex()) {
            val body = SpotifyGqlPayloadBuilder.buildGqlBody(operationName, sha256Hash, variables)
            val result = SpotifyGqlExecutor.executeGqlWithRetries(operationName, token, body)
            if (result.isPersistedQueryNotFound) {
                if (hashIdx < hashCandidates.lastIndex) {
                    log("W", "GQL $operationName hash rejected, trying previous_hash")
                    continue
                }
                log("E", "GQL $operationName all known hashes rejected, triggering remote refresh")
                onHashExpired?.invoke(operationName)
                throw Spotify.SpotifyException(412, "PersistedQueryNotFound for $operationName — hash may have rotated")
            }
            return result.json!!
        }
        throw Spotify.SpotifyException(412, "No valid hash found for $operationName")
    }

    suspend inline fun <reified T> authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGqlExecutor.authenticatedGet(endpoint, failFastOn429, block)

    suspend inline fun <reified T> restGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = authenticatedGet(endpoint, failFastOn429, block)

    suspend inline fun <reified T> restPost(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGqlExecutor.restPost(endpoint, body, block)

    suspend inline fun <reified T> restPut(
        endpoint: String,
        body: Any? = null,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGqlExecutor.restPut(endpoint, body, block)

    suspend inline fun <reified T> restDelete(
        endpoint: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T = SpotifyGqlExecutor.restDelete(endpoint, block)
}
