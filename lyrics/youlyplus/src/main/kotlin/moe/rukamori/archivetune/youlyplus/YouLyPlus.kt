/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.youlyplus

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.youlyplus.models.YouLyPlusLine
import moe.rukamori.archivetune.youlyplus.models.YouLyPlusLyricsResponse
import moe.rukamori.archivetune.youlyplus.models.YouLyPlusTtmlResponse
import java.util.Locale

object YouLyPlus {
    private const val TTML_PATH = "v1/ttml/get"
    private const val LYRICS_PATH = "v2/lyrics/get"

    private val baseUrls =
        listOf(
            "https://lyricsplus.binimum.org/",
            "https://lyricsplus.prjktla.my.id/",
            "https://lyricsplus.prjktla.workers.dev/",
            "https://lyricsplus.atomix.one/",
            "https://lyricsplus-seven.vercel.app/",
        )

    private val jsonFormat by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20000
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 20000
            }

            defaultRequest {
                headers.append("Accept", "application/json")
                headers.append("User-Agent", "ArchiveTune")
            }

            expectSuccess = false
        }
    }

    var logger: ((String) -> Unit)? = null

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
    ): Result<String> {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanAlbum = album?.trim().orEmpty()

        if (cleanTitle.isBlank() || cleanArtist.isBlank()) {
            return Result.failure(IllegalArgumentException("Song title and artist are required"))
        }

        return try {
            val lyrics =
                fetchTtml(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
                    ?: fetchLyricsAsLrc(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
                    ?: throw IllegalStateException("Lyrics unavailable")
            Result.success(lyrics)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
        callback: (String) -> Unit,
    ) {
        getLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
        ).onSuccess(callback)
    }

    private suspend fun fetchTtml(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String? =
        fetchFromMirrors(TTML_PATH, title, artist, album, durationSeconds) { body ->
            val trimmed = body.trim()
            when {
                trimmed.startsWith("<") -> trimmed
                else -> jsonFormat.decodeFromString<YouLyPlusTtmlResponse>(body).ttml?.trim()
            }?.takeIf { it.isNotBlank() && it.startsWith("<") }
        }

    private suspend fun fetchLyricsAsLrc(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String? =
        fetchFromMirrors(LYRICS_PATH, title, artist, album, durationSeconds) { body ->
            val response = jsonFormat.decodeFromString<YouLyPlusLyricsResponse>(body)
            response.toLyricsText()
        }

    private suspend fun fetchFromMirrors(
        path: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        decode: (String) -> String?,
    ): String? {
        for (baseUrl in baseUrls) {
            currentCoroutineContext().ensureActive()
            val endpoint = baseUrl + path
            logger?.invoke("Fetching YouLyPlus lyrics from $endpoint")

            try {
                val response =
                    client.get(endpoint) {
                        parameter("title", title)
                        parameter("artist", artist)
                        if (album.isNotBlank()) parameter("album", album)
                        if (durationSeconds > 0) parameter("duration", durationSeconds)
                    }
                val body = response.bodyAsText()
                logger?.invoke("YouLyPlus $path response status: ${response.status}")

                if (!response.status.isSuccess()) {
                    continue
                }

                val lyrics = decode(body)
                if (!lyrics.isNullOrBlank()) {
                    logger?.invoke("YouLyPlus $path lyrics length: ${lyrics.length}")
                    return lyrics
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger?.invoke("YouLyPlus $path fetch error from $baseUrl: ${e.message}")
            }
        }

        return null
    }

    private fun YouLyPlusLyricsResponse.toLyricsText(): String? {
        if (lyrics.isEmpty()) return null

        val timedLines = lyrics.filter { it.time != null }
        if (timedLines.isNotEmpty()) {
            return timedLines
                .joinToString("\n") { line ->
                    buildString {
                        append(formatLrcTimestamp(line.time ?: 0L, bracketed = true))
                        val syllables = line.syllabus.orEmpty().filter { !it.text.isNullOrBlank() && it.time != null }
                        if (type.equals("Word", ignoreCase = true) && syllables.isNotEmpty()) {
                            syllables.forEach { syllable ->
                                append(formatLrcTimestamp(syllable.time ?: 0L, bracketed = false))
                                append(syllable.text.orEmpty())
                            }
                        } else {
                            append(line.text.orEmpty())
                        }
                    }
                }.takeIf { it.isNotBlank() }
        }

        return lyrics
            .mapNotNull(YouLyPlusLine::text)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .takeIf(String::isNotBlank)
    }

    private fun formatLrcTimestamp(
        timeMs: Long,
        bracketed: Boolean,
    ): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val minutes = safeTime / 60000L
        val seconds = (safeTime % 60000L) / 1000L
        val millis = safeTime % 1000L
        val timestamp = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        return if (bracketed) "[$timestamp]" else "<$timestamp>"
    }
}
