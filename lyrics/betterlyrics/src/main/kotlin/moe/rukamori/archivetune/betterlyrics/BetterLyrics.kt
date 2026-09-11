/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

import io.ktor.client.HttpClient

object BetterLyrics {
    var logger: ((String) -> Unit)?
        get() = BetterLyricsClient.logger
        set(value) {
            BetterLyricsClient.logger = value
        }

    fun setClient(client: HttpClient) {
        BetterLyricsClient.setClient(client)
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
    ): Result<String> =
        BetterLyricsClient.runSuspendCatching {
            require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }
            BetterLyricsClient.fetchLyrics(
                artist = artist,
                title = title,
                album = album,
                durationSeconds = durationSeconds,
                endpoints = listOf(BetterLyricsClient.TTML_LYRICS_PATH, BetterLyricsClient.KUGOU_LYRICS_PATH),
            ) ?: throw IllegalStateException("Lyrics unavailable")
        }

    suspend fun getPortatoLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
    ): Result<String> =
        BetterLyricsClient.runSuspendCatching {
            require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }
            BetterLyricsClient.fetchLyrics(
                artist = artist,
                title = title,
                album = album,
                durationSeconds = durationSeconds,
                endpoints = listOf(BetterLyricsClient.PORTATO_LYRICS_PATH),
            ) ?: throw IllegalStateException("Portato lyrics unavailable")
        }

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
        callback: (String) -> Unit,
    ) {
        val result =
            getLyrics(
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSeconds,
            )
        result.onSuccess { ttml ->
            callback(ttml)
        }
    }

    suspend fun getAllPortatoLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int = -1,
        callback: (String) -> Unit,
    ) {
        getPortatoLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
        ).onSuccess(callback)
    }
}
