/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.BrowseBody
import moe.rukamori.archivetune.innertube.models.body.GetQueueBody
import moe.rukamori.archivetune.innertube.models.body.GetSearchSuggestionsBody
import moe.rukamori.archivetune.innertube.models.body.GetTranscriptBody
import moe.rukamori.archivetune.innertube.models.body.NextBody
import moe.rukamori.archivetune.innertube.models.body.SearchBody
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal class InnerTubeBrowse(private val http: InnerTubeHttp) {
    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        useAccountContext: Boolean = true,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
        useLoginForBrowse: Boolean,
    ): HttpResponse = http.withRetry {
        http.client.post("search") {
            with(http) {
                ytClient(
                    client = client,
                    setLogin = useAccountContext && useLoginForBrowse,
                    authState = authState,
                    includeVisitorData = useAccountContext,
                )
            }
            setBody(
                SearchBody(
                    context =
                        client.toContext(
                            locale,
                            if (useAccountContext) authState.visitorData else null,
                            if (useAccountContext && useLoginForBrowse) authState.dataSyncId else null,
                        ),
                    query = query,
                    params = params,
                ),
            )
            parameter("continuation", continuation)
            parameter("ctoken", continuation)
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
        useLoginForBrowse: Boolean,
    ): HttpResponse = http.withRetry {
        http.client.post("browse") {
            with(http) {
                ytClient(
                    client = client,
                    setLogin = setLogin || useLoginForBrowse,
                    authState = authState,
                )
            }
            setBody(
                BrowseBody(
                    context =
                        client.toContext(
                            locale,
                            authState.visitorData,
                            if (setLogin || useLoginForBrowse) authState.dataSyncId else null,
                        ),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
        authState: PlaybackAuthState,
        queueLocale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("next") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(
                NextBody(
                    context = client.toContext(queueLocale, authState.visitorData, authState.dataSyncId),
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation,
                ),
            )
        }
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("music/get_search_suggestions") {
            with(http) {
                ytClient(client, authState = authState)
            }
            setBody(
                GetSearchSuggestionsBody(
                    context = client.toContext(locale, authState.visitorData, null),
                    input = input,
                ),
            )
        }
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("music/get_queue") {
            with(http) {
                ytClient(client, authState = authState)
            }
            setBody(
                GetQueueBody(
                    context = client.toContext(locale, authState.visitorData, null),
                    videoIds = videoIds,
                    playlistId = playlistId,
                ),
            )
        }
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("https://music.youtube.com/youtubei/v1/get_transcript") {
            parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
            headers {
                append("Content-Type", "application/json")
            }
            setBody(
                GetTranscriptBody(
                    context = client.toContext(locale, null, null),
                    params =
                        Base64.Default.encode(
                            "\n${11.toChar()}$videoId".encodeToByteArray(),
                        ),
                ),
            )
        }
    }

    suspend fun getSwJsData(): HttpResponse = http.withRetry {
        http.client.get("https://music.youtube.com/sw.js_data")
    }
}
