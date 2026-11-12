/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState
import kotlin.random.Random

internal object PlayerTrackingOps {
    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

    fun newCpn(): String =
        (1..16)
            .map {
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[
                    Random.Default.nextInt(0, 64),
                ]
            }.joinToString("")

    suspend fun registerPlayback(
        innerTube: InnerTube,
        playlistId: String? = null,
        playbackTracking: String,
        cpn: String = newCpn(),
        authState: PlaybackAuthState,
    ): Result<String> = runCatching {
        innerTube.registerPlayback(
            url = playbackTracking,
            playlistId = playlistId,
            cpn = cpn,
            authState = authState,
        )
        cpn
    }

    suspend fun pingAtr(
        innerTube: InnerTube,
        atrUrl: String,
        cpn: String,
        authState: PlaybackAuthState,
    ): Result<HttpResponse> = runCatching {
        innerTube.pingAtr(
            url = atrUrl,
            cpn = cpn,
            authState = authState,
        )
    }

    suspend fun pingWatchtime(
        innerTube: InnerTube,
        watchtimeUrl: String,
        cpn: String,
        seconds: Long,
        final: Boolean = false,
        authState: PlaybackAuthState,
    ): Result<HttpResponse> = runCatching {
        innerTube.pingWatchtime(
            url = watchtimeUrl,
            cpn = cpn,
            seconds = seconds,
            final = final,
            authState = authState,
        )
    }

    suspend fun visitorData(innerTube: InnerTube): Result<String> = runCatching {
        Json
            .parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray
            .first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }.jsonPrimitive.content
    }
}
