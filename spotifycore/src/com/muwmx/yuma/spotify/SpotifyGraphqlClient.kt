/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Looks up a hash Spotify accepts when it rejects every hash we know. */
    @Volatile
    var hashRefresher: SpotifyHashRefresher? = null

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

        // hashes learned in an earlier session are read from disk here, off the main thread, before the first request
        hashRefresher?.applyLearnedOnce()
        val hashCandidates = SpotifyGqlPayloadBuilder.resolveHashCandidates(operationName)
        for ((hashIdx, sha256Hash) in hashCandidates.withIndex()) {
            val body = SpotifyGqlPayloadBuilder.buildGqlBody(operationName, sha256Hash, variables)
            val result = SpotifyGqlExecutor.executeGqlWithRetries(operationName, token, body)
            if (result.isPersistedQueryNotFound) {
                if (hashIdx < hashCandidates.lastIndex) {
                    log("W", "GQL $operationName hash rejected, trying previous_hash")
                    continue
                }
                log("E", "GQL $operationName all known hashes rejected")
                onHashExpired?.invoke(operationName)
                return retryWithLiveHash(operationName, token, variables, hashCandidates)
            }
            return result.json!!
        }
        throw Spotify.SpotifyException(412, "No valid hash found for $operationName")
    }

    /**
     * Spotify rejected every hash we know, so ask what its web player uses now and try that once.
     * A hash that works replaces the rejected one for the rest of the session and later launches.
     */
    private suspend fun retryWithLiveHash(
        operationName: String,
        token: String,
        variables: JsonObject,
        rejected: List<String>,
    ): JsonObject {
        val refresher = hashRefresher
        val liveHash = refresher?.liveHash(operationName, rejected.toSet())
        if (refresher == null || liveHash == null) {
            throw Spotify.SpotifyException(412, "PersistedQueryNotFound for $operationName — hash may have rotated")
        }
        log("W", "GQL $operationName trying the web player's current hash")
        val body = SpotifyGqlPayloadBuilder.buildGqlBody(operationName, liveHash, variables)
        val result = SpotifyGqlExecutor.executeGqlWithRetries(operationName, token, body)
        if (result.isPersistedQueryNotFound) {
            throw Spotify.SpotifyException(412, "PersistedQueryNotFound for $operationName — the web player's hash was rejected too")
        }
        // Only an answer with data proves the hash works; anything else is not applied or remembered.
        val answer = result.json
        if (answer == null || !SpotifyHashRefresher.isWorkingAnswer(answer)) {
            throw Spotify.SpotifyException(412, "GQL $operationName gave no usable answer with the web player's hash")
        }
        log("I", "GQL $operationName recovered with the web player's current hash")
        SpotifyHashProvider.updateHashes(
            remoteHashes = mapOf(operationName to SpotifyHashProvider.RemoteHashEntry(hash = liveHash, previousHash = rejected.firstOrNull())),
            source = SpotifyHashProvider.HashSource.REMOTE,
        )
        withContext(Dispatchers.IO) { refresher.learn(operationName, liveHash) }
        return answer
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
