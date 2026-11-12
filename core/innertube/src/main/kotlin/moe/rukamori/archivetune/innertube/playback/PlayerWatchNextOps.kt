/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.response.GetQueueResponse
import moe.rukamori.archivetune.innertube.models.response.NextResponse
import moe.rukamori.archivetune.innertube.pages.NextPage
import moe.rukamori.archivetune.innertube.pages.NextResult

internal object PlayerWatchNextOps {
    const val MAX_GET_QUEUE_SIZE = 1000

    suspend fun next(
        innerTube: InnerTube,
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult> = runCatching {
        val response =
            innerTube
                .next(
                    WEB_REMIX,
                    endpoint.videoId,
                    endpoint.playlistId,
                    endpoint.playlistSetVideoId,
                    endpoint.index,
                    endpoint.params,
                    continuation,
                ).body<NextResponse>()
        val playlistPanelRenderer =
            response.continuationContents?.playlistPanelContinuation
                ?: response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.get(0)
                    ?.tabRenderer
                    ?.content
                    ?.musicQueueRenderer
                    ?.content
                    ?.playlistPanelRenderer!!
        val title =
            response.contents.singleColumnMusicWatchNextResultsRenderer
                ?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer
                ?.tabs
                ?.get(0)
                ?.tabRenderer
                ?.content
                ?.musicQueueRenderer
                ?.header
                ?.musicQueueHeaderRenderer
                ?.subtitle
                ?.runs
                ?.firstOrNull()
                ?.text
        val items =
            playlistPanelRenderer.contents.mapNotNull { content ->
                content.playlistPanelVideoRenderer
                    ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                    ?.let { it to content.playlistPanelVideoRenderer.selected }
            }
        val songs = items.map { it.first }
        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

        if (followAutomixPreview) {
            playlistPanelRenderer.contents
                .lastOrNull()
                ?.automixPreviewVideoRenderer
                ?.content
                ?.automixPlaylistVideoRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.let { watchPlaylistEndpoint ->
                    return@runCatching next(innerTube, watchPlaylistEndpoint).getOrThrow().let { result ->
                        result.copy(
                            title = title,
                            items = songs + result.items,
                            lyricsEndpoint =
                                response.contents.singleColumnMusicWatchNextResultsRenderer
                                    ?.tabbedRenderer
                                    ?.watchNextTabbedResultsRenderer
                                    ?.tabs
                                    ?.getOrNull(1)
                                    ?.tabRenderer
                                    ?.endpoint
                                    ?.browseEndpoint,
                            relatedEndpoint =
                                response.contents.singleColumnMusicWatchNextResultsRenderer
                                    ?.tabbedRenderer
                                    ?.watchNextTabbedResultsRenderer
                                    ?.tabs
                                    ?.getOrNull(2)
                                    ?.tabRenderer
                                    ?.endpoint
                                    ?.browseEndpoint,
                            currentIndex = currentIndex,
                            endpoint = watchPlaylistEndpoint,
                        )
                    }
                }
        }
        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.getOrNull(1)
                    ?.tabRenderer
                    ?.endpoint
                    ?.browseEndpoint,
            relatedEndpoint =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.getOrNull(2)
                    ?.tabRenderer
                    ?.endpoint
                    ?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint,
        )
    }

    suspend fun queue(
        innerTube: InnerTube,
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE)
        }
        innerTube
            .getQueue(WEB_REMIX, videoIds, playlistId)
            .body<GetQueueResponse>()
            .queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }
}
