/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import moe.rukamori.archivetune.betterlyrics.models.TTMLResponse

internal object BetterLyricsClient {
    private const val API_BASE_URL = "https://lyrics-api.boidu.dev/"
    const val TTML_LYRICS_PATH = "getLyrics"
    const val KUGOU_LYRICS_PATH = "kugou/getLyrics"
    const val PORTATO_LYRICS_PATH = "qq/getLyrics"
    private const val MAX_RESPONSE_UNWRAP_DEPTH = 2
    private const val MAX_TTML_ROOT_SCAN_LENGTH = 4096
    private val ttmlRootRegex = Regex("""<(?:[A-Za-z_][\w.-]*:)?tt(?:\s|>)""", RegexOption.IGNORE_CASE)

    private data class DecodedLyrics(
        val content: String,
        val score: Double?,
    )

    private val jsonFormat by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Volatile
    private var customClient: HttpClient? = null

    fun setClient(client: HttpClient) {
        synchronized(this) {
            customClient = client
        }
    }

    val client: HttpClient
        get() = customClient ?: error("HttpClient is not initialized. Inject via setClient() before use.")

    var logger: ((String) -> Unit)? = null

    suspend fun fetchLyrics(
        artist: String,
        title: String,
        album: String?,
        durationSeconds: Int,
        endpoints: List<String>,
    ): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanAlbum = album?.trim().orEmpty()

        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        for (endpoint in endpoints) {
            fetchLyricsFromEndpoint(
                endpoint = endpoint,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                durationSeconds = durationSeconds,
            )?.let { lyrics ->
                return lyrics
            }
        }

        return null
    }

    private fun resolveUrl(endpoint: String): String =
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint else "$API_BASE_URL${endpoint.removePrefix("/")}"

    private suspend fun fetchLyricsFromEndpoint(
        endpoint: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String? {
        logger?.invoke(buildRequestLog(endpoint, title, artist, album, durationSeconds))

        return try {
            val response: HttpResponse =
                client.get(resolveUrl(endpoint)) {
                    parameter("s", title)
                    parameter("a", artist)
                    if (album.isNotBlank()) parameter("al", album)
                    if (durationSeconds > 0) parameter("d", durationSeconds)
                }

            logger?.invoke("$endpoint response status: ${response.status}")

            val responseText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger?.invoke("$endpoint request failed with status: ${response.status}")
                return null
            }

            val decoded =
                try {
                    decodeLyrics(responseText)
                } catch (e: Exception) {
                    logger?.invoke("$endpoint parse error: ${e.message}")
                    null
                }

            decoded?.score?.let { score ->
                logger?.invoke("$endpoint match score: $score")
            }
            logger?.invoke("$endpoint lyrics length: ${decoded?.content?.length ?: 0}")
            decoded?.content?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger?.invoke("$endpoint error fetching lyrics: ${e.stackTraceToString()}")
            null
        }
    }

    private fun decodeLyrics(responseText: String): DecodedLyrics? {
        val raw = responseText.removePrefix("\uFEFF")
        if (isTtmlPayload(raw)) return DecodedLyrics(content = raw, score = null)

        val root = jsonFormat.parseToJsonElement(responseText)
        val response =
            runCatching {
                jsonFormat.decodeFromJsonElement(TTMLResponse.serializer(), root)
            }.getOrNull()
        if (response != null && isTtmlPayload(response.ttml)) {
            return DecodedLyrics(content = response.ttml, score = response.score)
        }

        val nested = decodeLyricsElement(root, depth = 0) ?: return null
        return nested.copy(score = response?.score ?: nested.score)
    }

    private fun decodeLyricsElement(
        element: JsonElement,
        depth: Int,
    ): DecodedLyrics? {
        if (depth > MAX_RESPONSE_UNWRAP_DEPTH) return null

        return when (element) {
            is JsonObject -> {
                val score = (element["score"] as? JsonPrimitive)?.doubleOrNull
                val payload =
                    element["ttml"]
                        ?: element["lyrics"]
                        ?: element["data"]
                        ?: element["result"]
                        ?: element["response"]
                        ?: return null
                val decoded = decodeLyricsElement(payload, depth + 1) ?: return null
                decoded.copy(score = score ?: decoded.score)
            }

            is JsonPrimitive -> {
                val content = element.contentOrNull ?: return null
                when {
                    isTtmlPayload(content) -> DecodedLyrics(content = content, score = null)
                    depth < MAX_RESPONSE_UNWRAP_DEPTH -> {
                        val nested = runCatching { jsonFormat.parseToJsonElement(content) }.getOrNull() ?: return null
                        decodeLyricsElement(nested, depth + 1)
                    }

                    else -> null
                }
            }

            else -> null
        }
    }

    private fun isTtmlPayload(value: String): Boolean =
        ttmlRootRegex.containsMatchIn(value.take(MAX_TTML_ROOT_SCAN_LENGTH))

    private fun buildRequestLog(
        endpoint: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String =
        buildString {
            append("Sending request to ")
            append(API_BASE_URL)
            append(endpoint)
            append(" (s=")
            append(title)
            append(", a=")
            append(artist)
            if (album.isNotBlank()) {
                append(", al=")
                append(album)
            }
            if (durationSeconds > 0) {
                append(", d=")
                append(durationSeconds)
            }
            append(")")
        }

    suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
