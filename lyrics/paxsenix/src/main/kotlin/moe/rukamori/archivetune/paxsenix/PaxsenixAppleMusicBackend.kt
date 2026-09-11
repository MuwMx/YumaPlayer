/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.request.parameter

internal object PaxsenixAppleMusicBackend {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        PaxsenixApi.resultOf {
            val songId =
                PaxsenixSearch.searchTrackId(
                    path = "apple-music/search",
                    title = title,
                    artist = artist,
                    durationMs = PaxsenixApi.resolveDurationMs(durationSeconds),
                ) ?: throw IllegalStateException("Apple Music lyrics unavailable")
            val raw =
                PaxsenixApi.apiBody("lyrics/applemusic") {
                    parameter("id", songId)
                }
            PaxsenixParser.parseLyrics(raw)
                ?: throw IllegalStateException("Apple Music lyrics unavailable")
        }
}
