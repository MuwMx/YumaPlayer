/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.request.parameter

internal object PaxsenixSpotifyBackend {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        PaxsenixApi.resultOf {
            val trackId =
                PaxsenixSearch.searchTrackId(
                    path = "spotify/search",
                    title = title,
                    artist = artist,
                    durationMs = PaxsenixApi.resolveDurationMs(durationSeconds),
                ) ?: throw IllegalStateException("Spotify lyrics unavailable")
            PaxsenixParser.parseLyrics(
                PaxsenixApi.apiBody("lyrics/spotify") {
                    parameter("id", trackId)
                },
            ) ?: throw IllegalStateException("Spotify lyrics unavailable")
        }
}
