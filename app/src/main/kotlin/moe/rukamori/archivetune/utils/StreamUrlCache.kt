/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object StreamUrlCache {
    private const val logTag = "StreamUrlCache"
    const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    const val DEFAULT_STREAM_EXPIRE_SECONDS = 300
    const val STREAM_URL_EXPIRY_SAFETY_MS = 60_000L
    const val PO_TOKEN_VALIDITY_MS = 10 * 60 * 1000L

    data class CachedStreamUrl(
        val url: String,
        val expiresAtMs: Long,
        val authFingerprint: String,
    )

    data class CachedPoToken(
        val playerToken: String,
        val sessionToken: String,
        val expiresAtMs: Long,
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()
    private val poTokenCache = ConcurrentHashMap<String, CachedPoToken>()

    @Volatile
    var lastSuccessfulClientKey: String? = null

    fun extractExpireTimestampMsFromUrl(url: String): Long? {
        val expireTimestamp =
            url
                .toHttpUrlOrNull()
                ?.queryParameter("expire")
                ?.toLongOrNull()
                ?: return null
        return expireTimestamp * 1000L
    }

    fun extractExpireSecondsFromUrl(url: String): Int? {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return null
        val remaining = (expiresAtMs - System.currentTimeMillis()) / 1000L
        return remaining.toInt().takeIf { it > 0 }
    }

    fun resolveExpireSeconds(
        apiExpire: Int?,
        streamUrl: String?,
    ): Int {
        apiExpire?.let { return it }
        streamUrl?.let { url ->
            extractExpireSecondsFromUrl(url)?.let { fromUrl ->
                Timber.tag(logTag).w("Using expire time extracted from stream URL: ${fromUrl}s")
                return fromUrl
            }
        }
        Timber.tag(logTag).w("No expire time available from API or URL, using default: ${DEFAULT_STREAM_EXPIRE_SECONDS}s")
        return DEFAULT_STREAM_EXPIRE_SECONDS
    }

    fun getCachedPoToken(sessionId: String): CachedPoToken? {
        val cached = poTokenCache[sessionId] ?: return null
        if (cached.expiresAtMs < System.currentTimeMillis()) {
            poTokenCache.remove(sessionId)
            return null
        }
        return cached
    }

    fun cachePoToken(sessionId: String, playerToken: String, sessionToken: String) {
        poTokenCache[sessionId] = CachedPoToken(
            playerToken = playerToken,
            sessionToken = sessionToken,
            expiresAtMs = System.currentTimeMillis() + PO_TOKEN_VALIDITY_MS,
        )
    }

    fun getCachedStreamUrl(cacheKey: String): CachedStreamUrl? = streamUrlCache[cacheKey]

    fun putCachedStreamUrl(
        cacheKey: String,
        url: String,
        expiresInSeconds: Int,
        authFingerprint: String,
    ) {
        streamUrlCache[cacheKey] = CachedStreamUrl(
            url = url,
            expiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L),
            authFingerprint = authFingerprint,
        )
    }

    fun clearPlaybackAuthCaches() {
        streamUrlCache.clear()
        failedStreamClientsUntil.clear()
        poTokenCache.clear()
        lastSuccessfulClientKey = null
    }

    fun buildStreamCacheKey(
        videoId: String,
        itag: Int,
        client: YouTubeClient,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:$itag:${StreamClientUtils.buildClientKey(client)}"

    fun invalidateCachedStreamUrls(videoId: String) {
        val marker = ":$videoId:"
        streamUrlCache.keys.removeIf { it.contains(marker) }
    }

    fun isExpiredOrNearExpiredStreamUrl(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return false
        return expiresAtMs <= nowMs + STREAM_URL_EXPIRY_SAFETY_MS
    }

    fun markStreamClientFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        if (httpStatusCode !in setOf(403, 404, 410, 416)) return
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        failedStreamClientsUntil[buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
    }

    fun markPreferredClientFailed(
        videoId: String,
        client: PlayerStreamClient,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        markStreamClientFailed(videoId, client.name, httpStatusCode, authFingerprint)
    }

    fun isStreamClientTemporarilyBlocked(
        videoId: String,
        clientKey: String?,
        authFingerprint: String,
    ): Boolean {
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false
        val key = buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)
        val until = failedStreamClientsUntil[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(key)
            return false
        }
        return true
    }

    private fun normalizeStreamClientKey(clientKey: String?): String = StreamClientUtils.normalizeClientKey(clientKey)

    fun buildFailedClientKey(
        videoId: String,
        clientKey: String,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:${normalizeStreamClientKey(clientKey)}"
}
