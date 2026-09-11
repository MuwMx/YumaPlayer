/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats

internal object PaxsenixStatsBackend {
    suspend fun getStats(): Result<PaxsenixStats> =
        PaxsenixApi.resultOf {
            val response = PaxsenixApi.client.get(PaxsenixApi.STATS_URL) {
                header(HttpHeaders.UserAgent, PaxsenixApi.userAgent)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
            }
            check(response.status.value in 200..299) {
                "Paxsenix stats request failed with HTTP ${response.status.value}"
            }
            response.body<PaxsenixStats>()
        }
}
