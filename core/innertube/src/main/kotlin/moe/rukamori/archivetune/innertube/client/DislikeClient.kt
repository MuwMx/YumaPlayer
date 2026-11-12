/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.client

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import moe.rukamori.archivetune.innertube.models.ReturnYouTubeDislikeResponse
import moe.rukamori.archivetune.innertube.network.InnerTubeHttp

internal object DislikeClient {
    private const val VOTES_URL = "https://returnyoutubedislikeapi.com/Votes"

    suspend fun getDislikes(
        http: InnerTubeHttp,
        videoId: String,
    ): ReturnYouTubeDislikeResponse = http.withRetry {
        http.client.get("$VOTES_URL?videoId=$videoId") {
            contentType(ContentType.Application.Json)
        }.body<ReturnYouTubeDislikeResponse>()
    }
}
