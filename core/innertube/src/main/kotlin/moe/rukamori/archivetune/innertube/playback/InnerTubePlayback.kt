/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.innertube.models.Context
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.PlayerBody
import moe.rukamori.archivetune.innertube.network.InnerTubeHttp
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState

internal class InnerTubePlayback(private val http: InnerTubeHttp) {
    private companion object {
        const val PLAYBACK_TELEMETRY_VER = "2"
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val includeDataSyncId = setLogin && client.loginSupported && !authState.dataSyncId.isNullOrBlank()
        try {
            executePlayerRequest(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = setLogin,
                authState = authState,
                includeDataSyncId = includeDataSyncId,
                locale = locale,
            )
        } catch (failure: Throwable) {
            if (!shouldRetryPlayerRequestWithoutDataSyncId(failure, includeDataSyncId)) throw failure
            executePlayerRequest(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = false,
                authState = authState,
                includeDataSyncId = false,
                locale = locale,
            )
        }
    }

    private suspend fun executePlayerRequest(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String?,
        setLogin: Boolean,
        authState: PlaybackAuthState,
        includeDataSyncId: Boolean,
        locale: YouTubeLocale,
    ): HttpResponse =
        http.client.post(client.requestApiUrl("player")) {
            with(http) {
                ytClient(client, setLogin = setLogin, authState = authState)
            }
            setBody(
                PlayerBody(
                    context =
                        client
                            .toContext(
                                locale = locale,
                                visitorData = authState.visitorData,
                                dataSyncId = if (includeDataSyncId) authState.dataSyncId else null,
                            ).let {
                                if (client.isEmbedded) {
                                    it.copy(
                                        thirdParty =
                                            Context.ThirdParty(
                                                embedUrl = "https://www.youtube.com/watch?v=$videoId",
                                            ),
                                    )
                                } else {
                                    it
                                }
                            },
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext =
                        if (client.useSignatureTimestamp && signatureTimestamp != null) {
                            PlayerBody.PlaybackContext(
                                PlayerBody.PlaybackContext.ContentPlaybackContext(
                                    signatureTimestamp,
                                ),
                            )
                        } else {
                            null
                        },
                    serviceIntegrityDimensions =
                        poToken?.let {
                            PlayerBody.ServiceIntegrityDimensions(poToken = it)
                        },
                ),
            )
        }

    private fun shouldRetryPlayerRequestWithoutDataSyncId(
        failure: Throwable,
        includeDataSyncId: Boolean,
    ): Boolean {
        if (!includeDataSyncId) return false
        val clientError = failure as? ClientRequestException ?: return false
        if (clientError.response.status != HttpStatusCode.BadRequest) return false
        val message = clientError.message.orEmpty()
        if (!message.contains("/youtubei/v1/player", ignoreCase = true)) return false
        if (message.contains("Origin doesn't match Host", ignoreCase = true)) return false
        return message.contains("INVALID_ARGUMENT", ignoreCase = true) ||
            message.contains("invalid argument", ignoreCase = true)
    }

    private fun HttpRequestBuilder.appendPlaybackStatsClientParams(
        client: YouTubeClient,
        cpn: String,
        locale: YouTubeLocale,
    ) {
        parameter("ver", PLAYBACK_TELEMETRY_VER)
        parameter("c", client.clientName)
        parameter("cver", client.clientVersion)
        parameter("cpn", cpn)
        parameter("cplayer", "UNIPLAYER")
        parameter("cbr", "Chrome")
        parameter("cbrver", "141.0.0.0")
        parameter("cos", "Windows")
        parameter("cosver", "10.0")
        parameter("hl", locale.hl.replace('-', '_'))
        parameter("cr", locale.gl)
        parameter("prettyPrint", false)
    }

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.get(url) {
            with(http) {
                ytPlaybackTrackingClient(client, authState = authState)
            }
            appendPlaybackStatsClientParams(client, cpn, locale)

            if (playlistId != null) {
                parameter("list", playlistId)
                parameter("referrer", "https://music.youtube.com/playlist?list=$playlistId")
            }
        }
    }

    suspend fun pingAtr(
        url: String,
        cpn: String,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState,
    ): HttpResponse = http.withRetry {
        http.client.get(url) {
            with(http) {
                ytPlaybackTrackingClient(client, authState = authState)
            }
            parameter("cpn", cpn)
            parameter("prettyPrint", false)
        }
    }

    suspend fun pingWatchtime(
        url: String,
        cpn: String,
        seconds: Long,
        final: Boolean = false,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.get(url) {
            with(http) {
                ytPlaybackTrackingClient(client, authState = authState)
            }
            appendPlaybackStatsClientParams(client, cpn, locale)
            parameter("st", "0")
            parameter("et", seconds.toString())
            parameter("cmt", seconds.toString())
            parameter("state", if (final) "paused" else "playing")
            if (final) parameter("final", "1")
        }
    }
}
