/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.request.parameter

internal object PaxsenixMusixmatchBackend {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        PaxsenixApi.resultOf {
            PaxsenixParser.parseLyrics(
                PaxsenixApi.apiBody("lyrics/musixmatch") {
                    parameter("t", title)
                    parameter("a", artist)
                    parameter("d", durationSeconds.toString())
                },
            ) ?: throw IllegalStateException("Musixmatch lyrics unavailable")
        }
}
