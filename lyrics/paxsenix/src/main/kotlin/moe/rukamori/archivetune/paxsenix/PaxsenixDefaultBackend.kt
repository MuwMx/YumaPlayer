/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.request.parameter

internal object PaxsenixDefaultBackend {
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> =
        PaxsenixApi.resultOf {
            PaxsenixParser.parseLyrics(
                PaxsenixApi.apiBody("lyrics/lrcget") {
                    parameter("q", "$title $artist")
                },
            ) ?: throw IllegalStateException("Lyrics unavailable from Paxsenix for $title")
        }
}
