/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.library

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.LikeBody
import moe.rukamori.archivetune.innertube.models.body.SubscribeBody
import moe.rukamori.archivetune.innertube.network.InnerTubeHttp
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState

internal class InnerTubeLibraryRatings(private val http: InnerTubeHttp) {
    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val response =
            http.client.post("like/like") {
                with(http) {
                    ytClient(client, setLogin = true, authState = authState)
                }
                setBody(
                    LikeBody(
                        context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                        target = LikeBody.Target.VideoTarget(videoId),
                    ),
                )
            }
        with(http) {
            response.ensureNoInnertubeError("likeVideo($videoId)")
        }
    }

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val response =
            http.client.post("like/removelike") {
                with(http) {
                    ytClient(client, setLogin = true, authState = authState)
                }
                setBody(
                    LikeBody(
                        context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                        target = LikeBody.Target.VideoTarget(videoId),
                    ),
                )
            }
        with(http) {
            response.ensureNoInnertubeError("unlikeVideo($videoId)")
        }
    }

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("subscription/subscribe") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("subscription/unsubscribe") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                SubscribeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    channelIds = listOf(channelId),
                ),
            )
        }
    }

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("like/like") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                LikeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("like/removelike") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                LikeBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    target = LikeBody.Target.PlaylistTarget(playlistId),
                ),
            )
        }
    }
}
