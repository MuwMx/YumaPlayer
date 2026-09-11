/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import moe.rukamori.archivetune.paxsenix.models.AppleMusicLyricsResponse
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats
import java.util.Locale
import kotlin.math.abs

object PaxsenixLyrics {
    private const val BASE_URL = "https://api.paxsenix.org/"
    private const val STATS_URL = "https://lyrics.paxsenix.org/api/stats"

    @Volatile
    private var apiKey: String = ""

    var userAgent: String = "ArchiveTune"
        private set

    fun setUserAgent(
        appName: String,
        versionName: String,
    ) {
        userAgent = "$appName/$versionName"
    }

    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey.trim()
    }

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.UserAgent, userAgent)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }

            expectSuccess = false
        }
    }

    private suspend fun apiGet(
        path: String,
        request: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val currentApiKey = apiKey
        check(currentApiKey.isNotEmpty()) { "Paxsenix API key is not configured" }

        return client.get(path) {
            header(HttpHeaders.Authorization, "Bearer $currentApiKey")
            request()
        }
    }

    private suspend fun apiBody(
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

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun resolveDurationMs(durationSeconds: Int): Long =
        durationSeconds.coerceAtLeast(0) * 1_000L

    private val lyricsContentKeys =
        listOf(
            "lyrics",
            "lrc",
            "content",
            "lines",
            "text",
            "plainLyrics",
            "syncedLyrics",
            "line",
            "lyric",
        )

    private fun cleanJsonLyrics(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val payload = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return trimmed
        return extractLyrics(payload)
    }

    private fun parseLyrics(raw: String): String? {
        val timedLyrics = runCatching { json.decodeFromString<AppleMusicLyricsResponse>(raw) }.getOrNull()
        return timedLyrics
            ?.takeIf { it.content.isNotEmpty() }
            ?.let(::convertAppleMusicToLrc)
            ?.takeIf(String::isNotBlank)
            ?: cleanJsonLyrics(raw)
    }

    private fun extractLyrics(element: JsonElement): String? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive -> {
                if (!element.isString) {
                    null
                } else {
                    val value = element.content.trim()
                    if (value.isEmpty()) {
                        null
                    } else {
                        val nestedPayload = runCatching { json.parseToJsonElement(value) }.getOrNull()
                        if (nestedPayload != null && nestedPayload !is JsonPrimitive) {
                            extractLyrics(nestedPayload)
                        } else {
                            value
                        }
                    }
                }
            }

            is JsonArray ->
                element
                    .mapNotNull(::extractLyrics)
                    .joinToString("\n")
                    .trim()
                    .takeIf(String::isNotEmpty)

            is JsonObject -> {
                if (element.isErrorPayload()) {
                    null
                } else {
                    lyricsContentKeys
                        .asSequence()
                        .mapNotNull { key -> element[key]?.let(::extractLyrics) }
                        .firstOrNull()
                        ?: (element["metadata"] as? JsonObject)?.let { metadata ->
                            lyricsContentKeys
                                .asSequence()
                                .mapNotNull { key -> metadata[key]?.let(::extractLyrics) }
                                .firstOrNull()
                        }
                        ?: element["words"]?.let { words ->
                            when (words) {
                                is JsonArray ->
                                    words
                                        .mapNotNull(::extractLyrics)
                                        .joinToString(" ")
                                        .trim()
                                        .takeIf(String::isNotEmpty)

                                else -> extractLyrics(words)
                            }
                        }
                }
            }
        }

    private fun JsonObject.isErrorPayload(): Boolean {
        if ((this["isError"] as? JsonPrimitive)?.booleanOrNull == true) return true
        if ((this["ok"] as? JsonPrimitive)?.booleanOrNull == false) return true

        return when (val error = this["error"]) {
            null, JsonNull -> false
            is JsonPrimitive -> error.booleanOrNull ?: error.content.trim().isNotEmpty()
            is JsonArray -> error.isNotEmpty()
            is JsonObject -> error.isNotEmpty()
        }
    }

    private suspend fun searchTrackId(
        path: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): String? {
        val raw =
            apiBody(path) {
                parameter("q", "$title $artist")
            }
        val payload = json.parseToJsonElement(raw)
        val candidates = buildList { payload.collectTrackCandidates(this) }
        return candidates
            .map { candidate -> candidate to candidate.score(title, artist, durationMs) }
            .maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> score >= MINIMUM_MATCH_SCORE }
            ?.first
            ?.id
    }

    private fun JsonElement.collectTrackCandidates(destination: MutableList<TrackCandidate>) {
        when (this) {
            is JsonArray -> forEach { it.collectTrackCandidates(destination) }
            is JsonObject -> {
                toTrackCandidate()?.let(destination::add)
                values.forEach { it.collectTrackCandidates(destination) }
            }

            else -> Unit
        }
    }

    private fun JsonObject.toTrackCandidate(): TrackCandidate? {
        val attributes = this["attributes"] as? JsonObject
        val details = attributes ?: this
        val id = firstString(TRACK_ID_KEYS) ?: details.firstString(TRACK_ID_KEYS) ?: return null
        val title = details.firstString(TRACK_TITLE_KEYS) ?: return null
        val artist = details.firstString(TRACK_ARTIST_KEYS) ?: details.artistNames()
        val duration = details.firstLong(TRACK_DURATION_KEYS).toDurationMs()
        return TrackCandidate(id = id, title = title, artist = artist.orEmpty(), durationMs = duration)
    }

    private fun JsonObject.firstString(keys: List<String>): String? =
        keys
            .asSequence()
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)

    private fun JsonObject.firstLong(keys: List<String>): Long? =
        keys
            .asSequence()
            .mapNotNull { key -> (this[key] as? JsonPrimitive)?.longOrNull }
            .firstOrNull()

    private fun JsonObject.artistNames(): String? {
        val artists = this["artists"] ?: this["artist"] ?: return null
        return when (artists) {
            is JsonPrimitive -> artists.contentOrNull
            is JsonObject -> artists.firstString(listOf("name", "artistName", "title"))
            is JsonArray ->
                artists
                    .mapNotNull { artist ->
                        when (artist) {
                            is JsonPrimitive -> artist.contentOrNull
                            is JsonObject -> artist.firstString(listOf("name", "artistName", "title"))
                            else -> null
                        }
                    }.joinToString(", ")
                    .takeIf(String::isNotEmpty)

            else -> null
        }
    }

    private fun Long?.toDurationMs(): Long =
        when {
            this == null || this <= 0L -> 0L
            this < 10_000L -> this * 1_000L
            else -> this
        }

    private fun TrackCandidate.score(
        requestedTitle: String,
        requestedArtist: String,
        requestedDurationMs: Long,
    ): Int {
        var score = 0
        score += textMatchScore(title, requestedTitle, exactScore = 20, partialScore = 10)
        score += textMatchScore(artist, requestedArtist, exactScore = 15, partialScore = 5)
        if (requestedDurationMs > 0L && durationMs > 0L) {
            val difference = abs(durationMs - requestedDurationMs)
            score +=
                when {
                    difference < 3_000L -> 10
                    difference < 10_000L -> 5
                    else -> 0
                }
        }
        return score
    }

    private fun textMatchScore(
        candidate: String,
        requested: String,
        exactScore: Int,
        partialScore: Int,
    ): Int {
        if (candidate.isBlank() || requested.isBlank()) return 0
        return when {
            candidate.equals(requested, ignoreCase = true) -> exactScore
            candidate.contains(requested, ignoreCase = true) ||
                requested.contains(candidate, ignoreCase = true) -> partialScore
            else -> 0
        }
    }

    suspend fun getAppleMusicLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        resultOf {
            val songId =
                searchTrackId(
                    path = "apple-music/search",
                    title = title,
                    artist = artist,
                    durationMs = resolveDurationMs(durationSeconds),
                ) ?: throw IllegalStateException("Apple Music lyrics unavailable")
            val raw =
                apiBody("lyrics/applemusic") {
                    parameter("id", songId)
                }
            parseLyrics(raw)
                ?: throw IllegalStateException("Apple Music lyrics unavailable")
        }

    suspend fun getSpotifyLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        resultOf {
            val trackId =
                searchTrackId(
                    path = "spotify/search",
                    title = title,
                    artist = artist,
                    durationMs = resolveDurationMs(durationSeconds),
                ) ?: throw IllegalStateException("Spotify lyrics unavailable")
            parseLyrics(
                apiBody("lyrics/spotify") {
                    parameter("id", trackId)
                },
            ) ?: throw IllegalStateException("Spotify lyrics unavailable")
        }

    suspend fun getMusixmatchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        resultOf {
            parseLyrics(
                apiBody("lyrics/musixmatch") {
                    parameter("t", title)
                    parameter("a", artist)
                    parameter("d", durationSeconds.toString())
                },
            ) ?: throw IllegalStateException("Musixmatch lyrics unavailable")
        }

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        resultOf {
            parseLyrics(
                apiBody("lyrics/lrcget") {
                    parameter("q", "$title $artist")
                },
            ) ?: throw IllegalStateException("Lyrics unavailable from Paxsenix for $title")
        }

    private fun convertAppleMusicToLrc(response: AppleMusicLyricsResponse): String =
        response.content.joinToString("\n") { line ->
            val minutes = line.timestamp / 1_000 / 60
            val seconds = (line.timestamp / 1_000) % 60
            val hundredths = (line.timestamp % 1_000) / 10
            val time = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
            val text = line.text.joinToString(" ") { it.text.trim() }
            "$time$text"
        }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess(callback)
    }

    suspend fun getStats(): Result<PaxsenixStats> =
        resultOf {
            val response = client.get(STATS_URL)
            check(response.status.value in 200..299) {
                "Paxsenix stats request failed with HTTP ${response.status.value}"
            }
            response.body<PaxsenixStats>()
        }

    private data class TrackCandidate(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    private const val MINIMUM_MATCH_SCORE = 10
    private val TRACK_ID_KEYS = listOf("id", "trackId", "track_id", "realId")
    private val TRACK_TITLE_KEYS = listOf("name", "title", "trackName", "track_name")
    private val TRACK_ARTIST_KEYS = listOf("artistName", "artist_name")
    private val TRACK_DURATION_KEYS = listOf("durationInMillis", "durationMs", "duration_ms", "duration")
}
