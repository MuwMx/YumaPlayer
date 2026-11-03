/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.canvas.models.matchesSongIdentity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ArchiveTuneCanvas {
    private const val BASE_URL = "https://artwork-archivetune.koiiverse.cloud/"
    private const val FALLBACK_URL = "https://artwork.boidu.dev/"
    private const val PROVIDER_TIMEOUT_MS = 20_000L
    private const val CACHE_TTL_MS = 60_000L

    @Volatile
    private var bearerToken: String? = null

    fun initialize(
        bearerToken: String?,
        tidalToken: String? = null,
    ) {
        this.bearerToken = bearerToken?.trim()?.takeIf { it.isNotEmpty() }
        TidalCanvasProvider.initialize(tidalToken)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            defaultRequest {
                url(BASE_URL)
            }
            expectSuccess = false
        }
    }

    private val fallbackClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            defaultRequest {
                url(FALLBACK_URL)
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    internal suspend fun executeProviderAttempt(
        provider: CanvasSource,
        requireVertical: Boolean,
        timeoutMs: Long = PROVIDER_TIMEOUT_MS,
        block: suspend () -> CanvasArtwork?,
    ): CanvasArtwork? {
        if (provider == CanvasSource.TIDAL && requireVertical) return null
        return try {
            withTimeoutOrNull(timeoutMs) {
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    internal fun CanvasArtwork.hasUsableCanvasAnimation(requireVertical: Boolean): Boolean =
        if (requireVertical) {
            !preferredVerticalAnimationUrl.isNullOrBlank()
        } else {
            !preferredAnimationUrl.isNullOrBlank()
        }

    internal fun acceptSongCandidate(
        candidate: CanvasArtwork?,
        provider: CanvasSource,
        song: String,
        artist: String,
        requireVertical: Boolean,
    ): CanvasArtwork? {
        if (candidate == null) return null
        if (!candidate.hasUsableCanvasAnimation(requireVertical)) return null
        if ((provider == CanvasSource.BETTER_LYRICS || provider == CanvasSource.APPLE_MUSIC) &&
            !candidate.matchesSongIdentity(song, artist)
        ) {
            return null
        }
        return candidate.copy(source = provider)
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        storefront: String = "us",
        source: CanvasSource = CanvasSource.ALL,
        requireVertical: Boolean = false,
    ): CanvasArtwork? {
        val key = cacheKey("sa", song, artist, storefront, source.name, requireVertical.toString())
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val providersToTry =
            when (source) {
                CanvasSource.BETTER_LYRICS -> listOf(CanvasSource.BETTER_LYRICS)
                CanvasSource.APPLE_MUSIC -> listOf(CanvasSource.APPLE_MUSIC)
                CanvasSource.TIDAL -> listOf(CanvasSource.TIDAL)
                CanvasSource.SPOTIFY -> emptyList()
                CanvasSource.ALL -> listOf(CanvasSource.BETTER_LYRICS, CanvasSource.APPLE_MUSIC, CanvasSource.TIDAL)
            }

        for (provider in providersToTry) {
            val result =
                executeProviderAttempt(provider, requireVertical) {
                    when (provider) {
                        CanvasSource.BETTER_LYRICS -> fetchBetterLyricsSong(song, artist, storefront)
                        CanvasSource.APPLE_MUSIC -> AppleMusicProvider.getBySongArtist(song, artist, null, storefront)
                        CanvasSource.TIDAL -> TidalCanvasProvider.getBySongArtist(song, artist, storefront)
                        CanvasSource.SPOTIFY, CanvasSource.ALL -> null
                    }
                }

            val attributed = acceptSongCandidate(result, provider, song, artist, requireVertical)
            if (attributed != null) {
                if (cache.size >= 128) cache.clear()
                cache[key] = CacheEntry(attributed, System.currentTimeMillis() + CACHE_TTL_MS)
                return attributed
            }
        }

        return null
    }

    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
        source: CanvasSource = CanvasSource.ALL,
        requireVertical: Boolean = false,
    ): CanvasArtwork? {
        val key = cacheKey("id", albumId, storefront, source.name, requireVertical.toString())
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val providersToTry =
            when (source) {
                CanvasSource.BETTER_LYRICS -> listOf(CanvasSource.BETTER_LYRICS)
                CanvasSource.APPLE_MUSIC -> listOf(CanvasSource.APPLE_MUSIC)
                CanvasSource.ALL -> listOf(CanvasSource.BETTER_LYRICS, CanvasSource.APPLE_MUSIC)
                CanvasSource.TIDAL, CanvasSource.SPOTIFY -> emptyList()
            }

        for (provider in providersToTry) {
            val result =
                executeProviderAttempt(provider, requireVertical) {
                    when (provider) {
                        CanvasSource.BETTER_LYRICS -> fetchBetterLyricsAlbumId(albumId)
                        CanvasSource.APPLE_MUSIC -> AppleMusicProvider.getByAlbumId(albumId, storefront)
                        else -> null
                    }
                }

            if (result != null && result.hasUsableCanvasAnimation(requireVertical)) {
                val attributed = result.copy(source = provider)
                if (cache.size >= 128) cache.clear()
                cache[key] = CacheEntry(attributed, System.currentTimeMillis() + CACHE_TTL_MS)
                return attributed
            }
        }

        return null
    }

    suspend fun getByAlbumUrl(
        url: String,
        source: CanvasSource = CanvasSource.ALL,
        requireVertical: Boolean = false,
    ): CanvasArtwork? {
        val key = cacheKey("url", url, source.name, requireVertical.toString())
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val providersToTry =
            when (source) {
                CanvasSource.BETTER_LYRICS -> listOf(CanvasSource.BETTER_LYRICS)
                CanvasSource.APPLE_MUSIC -> listOf(CanvasSource.APPLE_MUSIC)
                CanvasSource.ALL -> listOf(CanvasSource.BETTER_LYRICS, CanvasSource.APPLE_MUSIC)
                CanvasSource.TIDAL, CanvasSource.SPOTIFY -> emptyList()
            }

        for (provider in providersToTry) {
            val result =
                executeProviderAttempt(provider, requireVertical) {
                    when (provider) {
                        CanvasSource.BETTER_LYRICS -> fetchBetterLyricsAlbumUrl(url)
                        CanvasSource.APPLE_MUSIC ->
                            parseAppleMusicAlbumUrl(url)?.let { (albumId, storefront) ->
                                AppleMusicProvider.getByAlbumId(albumId, storefront)
                            }
                        else -> null
                    }
                }

            if (result != null && result.hasUsableCanvasAnimation(requireVertical)) {
                val attributed = result.copy(source = provider)
                if (cache.size >= 128) cache.clear()
                cache[key] = CacheEntry(attributed, System.currentTimeMillis() + CACHE_TTL_MS)
                return attributed
            }
        }

        return null
    }

    suspend fun isHealthy(): Boolean =
        try {
            val response = client.get("health")
            response.status == HttpStatusCode.OK
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private suspend fun fetchBetterLyricsSong(
        song: String,
        artist: String,
        storefront: String,
    ): CanvasArtwork? {
        val primary =
            try {
                val response =
                    client.get {
                        parameter("s", song)
                        parameter("a", artist)
                        parameter("storefront", storefront)
                    }
                if (response.status == HttpStatusCode.OK) response.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        if (primary != null) return primary.copy(source = CanvasSource.BETTER_LYRICS)
        currentCoroutineContext().ensureActive()

        val fallback =
            try {
                val fallbackResponse =
                    fallbackClient.get {
                        parameter("s", song)
                        parameter("a", artist)
                        parameter("storefront", storefront)
                    }
                if (fallbackResponse.status == HttpStatusCode.OK) fallbackResponse.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        return fallback?.copy(source = CanvasSource.BETTER_LYRICS)
    }

    private suspend fun fetchBetterLyricsAlbumId(albumId: String): CanvasArtwork? {
        val primary =
            try {
                val response = client.get { parameter("id", albumId) }
                if (response.status == HttpStatusCode.OK) response.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        if (primary != null) return primary.copy(source = CanvasSource.BETTER_LYRICS)
        currentCoroutineContext().ensureActive()

        val fallback =
            try {
                val fallbackResponse = fallbackClient.get { parameter("id", albumId) }
                if (fallbackResponse.status == HttpStatusCode.OK) fallbackResponse.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        return fallback?.copy(source = CanvasSource.BETTER_LYRICS)
    }

    private suspend fun fetchBetterLyricsAlbumUrl(url: String): CanvasArtwork? {
        val primary =
            try {
                val response = client.get { parameter("url", url) }
                if (response.status == HttpStatusCode.OK) response.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        if (primary != null) return primary.copy(source = CanvasSource.BETTER_LYRICS)
        currentCoroutineContext().ensureActive()

        val fallback =
            try {
                val fallbackResponse = fallbackClient.get { parameter("url", url) }
                if (fallbackResponse.status == HttpStatusCode.OK) fallbackResponse.body<CanvasArtwork>() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        return fallback?.copy(source = CanvasSource.BETTER_LYRICS)
    }

    private fun parseAppleMusicAlbumUrl(url: String): Pair<String, String>? {
        if (!url.contains("music.apple.com")) return null
        val albumPart = url.substringAfter("/album/", "").substringBefore("?")
        val albumId = albumPart.substringAfterLast("/", "")
        if (albumId.isBlank() || !albumId.all { it.isDigit() }) return null
        val storefront = url.substringAfter("music.apple.com/").substringBefore("/")
        if (storefront.isBlank()) return null
        return albumId to storefront
    }

    private fun cacheKey(
        prefix: String,
        vararg parts: String,
    ): String {
        val normalized =
            parts
                .map { it.trim().lowercase(Locale.ROOT) }
                .joinToString("|")
        return "$prefix|$normalized"
    }
}
