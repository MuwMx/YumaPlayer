/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.BrowseClient
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.MusicPlaylistShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.oddElements
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.pages.ArtistItemsContinuationPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.utils.parseTime

internal object BrowseArtistOps {
    private inline val innerTube: InnerTube get() = YouTube.innerTube
    private inline val traceLogger: ((String) -> Unit)? get() = BrowseClient.traceLogger

    suspend fun artist(browseId: String): Result<ArtistPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val immersiveHeader = response.header?.musicImmersiveHeaderRenderer
            val subscribeButtonRenderer = immersiveHeader?.subscriptionButton?.subscribeButtonRenderer

            val firstShelfItem =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicShelfRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveListItemRenderer

            ArtistPage(
                artist =
                    ArtistItem(
                        id = browseId,
                        title =
                            immersiveHeader?.title?.runs?.firstOrNull()?.text
                                ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
                                ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                        thumbnail =
                            immersiveHeader?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
                        channelId = subscribeButtonRenderer?.channelId,
                        playEndpoint =
                            firstShelfItem
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint,
                        shuffleEndpoint =
                            immersiveHeader?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
                                ?: firstShelfItem?.navigationEndpoint?.watchPlaylistEndpoint,
                        radioEndpoint =
                            immersiveHeader?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                        subscriberCountText =
                            subscribeButtonRenderer?.subscriberCountText?.runs?.firstOrNull()?.text
                                ?: subscribeButtonRenderer?.subscriberCountWithSubscribeText?.runs?.firstOrNull()?.text,
                        monthlyListenerCountText =
                            immersiveHeader?.monthlyListenerCount?.runs?.firstOrNull()?.text,
                    ),
                sections =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
                description =
                    immersiveHeader
                        ?.description
                        ?.runs
                        ?.joinToString(separator = "") { run -> run.text }
                        ?.takeIf { description -> description.isNotBlank() },
            )
        }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        runCatching {
            traceLogger?.invoke("BrowseClientRequest: browseId=${endpoint.browseId}, paramsPresent=${endpoint.params != null}, paramsLength=${endpoint.params?.length ?: 0}")
            val httpResponse = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params)
            val statusCode = httpResponse.status.value
            val response = httpResponse.body<BrowseResponse>()
            val contents = response.contents
            val hasSingleColumn = contents?.singleColumnBrowseResultsRenderer != null
            val hasTwoColumn = contents?.twoColumnBrowseResultsRenderer != null
            val hasSectionList = contents?.sectionListRenderer != null
            val singleColumnTabsCount = contents?.singleColumnBrowseResultsRenderer?.tabs?.size ?: 0
            val twoColumnTabsCount = contents?.twoColumnBrowseResultsRenderer?.tabs?.size ?: 0
            val sectionContents = response.artistItemsSectionContents()
            val sectionContentsCount = sectionContents.size

            val gridCount = sectionContents.count { it.findGridRenderer() != null }
            val playlistShelfCount = sectionContents.count { it.findMusicPlaylistShelfRenderer() != null }
            val musicShelfCount = sectionContents.count { it.findMusicShelfRenderer() != null }
            val carouselCount = sectionContents.count { it.musicCarouselShelfRenderer != null }
            val cardShelfCount = sectionContents.count { it.musicCardShelfRenderer != null }
            val itemSectionCount = sectionContents.count { it.itemSectionRenderer != null }

            val gridRenderer = sectionContents.firstNotNullOfOrNull { it.findGridRenderer() }
            if (gridRenderer != null) {
                val rawCount = gridRenderer.items.size
                val twoRowRendererCount = gridRenderer.items.count { it.musicTwoRowItemRenderer != null }
                val rejections = mutableMapOf<String, Int>()
                val items =
                    gridRenderer.items.mapNotNull { item ->
                        val renderer = item.musicTwoRowItemRenderer
                        if (renderer == null) {
                            rejections.merge("missing_two_row", 1, Int::plus)
                            null
                        } else {
                            val parsed = ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                            if (parsed == null) {
                                when {
                                    renderer.isAlbum -> rejections.merge("rejected_album", 1, Int::plus)
                                    renderer.isSong -> rejections.merge("rejected_song", 1, Int::plus)
                                    renderer.isPlaylist -> rejections.merge("rejected_playlist", 1, Int::plus)
                                    else -> rejections.merge("unsupported_type", 1, Int::plus)
                                }
                            }
                            parsed
                        }
                    }
                val mappedCount = items.size
                val continuation = gridRenderer.continuations?.getContinuation()
                val continuationPresent = continuation != null

                traceLogger?.invoke(
                    "BrowseClientResponse: status=$statusCode, singleCol=$hasSingleColumn, twoCol=$hasTwoColumn, secList=$hasSectionList, " +
                        "singleColTabs=$singleColumnTabsCount, twoColTabs=$twoColumnTabsCount, secContents=$sectionContentsCount, " +
                        "renderers(grid=$gridCount, plShelf=$playlistShelfCount, mShelf=$musicShelfCount, itemSec=$itemSectionCount, carousel=$carouselCount, cardShelf=$cardShelfCount), " +
                        "selected=GRID, rawItems=$rawCount, twoRowItems=$twoRowRendererCount, mappedItems=$mappedCount, rejections=$rejections, continuation=$continuationPresent"
                )

                ArtistItemsPage(
                    title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                    items = items,
                    continuation = continuation,
                    layout = ArtistItemsPageLayout.GRID,
                )
            } else {
                val musicPlaylistShelfRenderer = sectionContents.firstNotNullOfOrNull { it.findMusicPlaylistShelfRenderer() }
                val musicShelfRenderer = sectionContents.firstNotNullOfOrNull { it.findMusicShelfRenderer() }
                val shelfContents = musicPlaylistShelfRenderer?.contents ?: musicShelfRenderer?.contents.orEmpty()
                val rawShelfContentCount = shelfContents.size
                val listItems = shelfContents.getItems()
                val rawListItemCount = listItems.size
                val rejections = mutableMapOf<String, Int>()
                val items =
                    listItems.mapNotNull { item ->
                        val parsed = ArtistItemsPage.fromMusicResponsiveListItemRenderer(item)
                        if (parsed == null) {
                            when {
                                item.playlistItemData?.videoId == null -> rejections.merge("missing_videoId", 1, Int::plus)
                                item.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text == null -> rejections.merge("missing_title", 1, Int::plus)
                                item.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements() == null -> rejections.merge("missing_artists", 1, Int::plus)
                                item.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text?.parseTime() == null -> rejections.merge("missing_duration", 1, Int::plus)
                                item.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() == null -> rejections.merge("missing_thumbnail", 1, Int::plus)
                                item.flexColumns.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let { it.navigationEndpoint?.browseEndpoint?.browseId == null } == true -> rejections.merge("missing_album_browseId", 1, Int::plus)
                                else -> rejections.merge("other_reason", 1, Int::plus)
                            }
                        }
                        parsed
                    }
                val mappedCount = items.size
                val continuation =
                    shelfContents.getContinuation()
                        ?: musicPlaylistShelfRenderer?.continuations?.getContinuation()
                        ?: musicShelfRenderer?.continuations?.getContinuation()
                val continuationPresent = continuation != null

                traceLogger?.invoke(
                    "BrowseClientResponse: status=$statusCode, singleCol=$hasSingleColumn, twoCol=$hasTwoColumn, secList=$hasSectionList, " +
                        "singleColTabs=$singleColumnTabsCount, twoColTabs=$twoColumnTabsCount, secContents=$sectionContentsCount, " +
                        "renderers(grid=$gridCount, plShelf=$playlistShelfCount, mShelf=$musicShelfCount, itemSec=$itemSectionCount, carousel=$carouselCount, cardShelf=$cardShelfCount), " +
                        "selected=SHELF, rawShelfContents=$rawShelfContentCount, listItems=$rawListItemCount, mappedItems=$mappedCount, rejections=$rejections, continuation=$continuationPresent"
                )

                ArtistItemsPage(
                    title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: musicShelfRenderer?.title?.runs?.firstOrNull()?.text
                        ?: "",
                    items = items,
                    continuation = continuation,
                    layout = ArtistItemsPageLayout.LIST,
                )
            }
        }.onFailure { e ->
            traceLogger?.invoke("BrowseClientFailure: browseId=${endpoint.browseId}, error=${e::class.java.simpleName}: ${e.message}")
        }

    private fun BrowseResponse.artistItemsSectionContents(): List<SectionListRenderer.Content> =
        contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()

    private fun SectionListRenderer.Content.findGridRenderer(): GridRenderer? =
        gridRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.gridRenderer }

    private fun SectionListRenderer.Content.findMusicPlaylistShelfRenderer(): MusicPlaylistShelfRenderer? = musicPlaylistShelfRenderer

    private fun SectionListRenderer.Content.findMusicShelfRenderer(): MusicShelfRenderer? =
        musicShelfRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.musicShelfRenderer }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        runCatching {
            traceLogger?.invoke("BrowseClientContinuationRequest: continuationLen=${continuation.length}")
            val httpResponse = innerTube.browse(WEB_REMIX, continuation = continuation)
            val statusCode = httpResponse.status.value
            val response = httpResponse.body<BrowseResponse>()

            when {
                response.continuationContents?.gridContinuation != null -> {
                    val gridContinuation = response.continuationContents.gridContinuation
                    val rawCount = gridContinuation.items.size
                    val items =
                        gridContinuation.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer ->
                                ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                            }
                        }
                    traceLogger?.invoke("BrowseClientContinuationResponse: status=$statusCode, branch=GRID, rawItems=$rawCount, mappedItems=${items.size}, continuation=${gridContinuation.continuations?.getContinuation() != null}")
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else gridContinuation.continuations?.getContinuation(),
                    )
                }

                response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                    val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                    val rawCount = musicPlaylistShelfContinuation.contents.size
                    val items =
                        musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        }
                    traceLogger?.invoke("BrowseClientContinuationResponse: status=$statusCode, branch=PLAYLIST_SHELF, rawItems=$rawCount, mappedItems=${items.size}, continuation=${musicPlaylistShelfContinuation.continuations?.getContinuation() != null}")
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else musicPlaylistShelfContinuation.continuations?.getContinuation(),
                    )
                }

                else -> {
                    val continuationItems =
                        response.onResponseReceivedActions
                            ?.firstOrNull()
                            ?.appendContinuationItemsAction
                            ?.continuationItems
                    val rawCount = continuationItems?.size ?: 0
                    val items =
                        continuationItems?.getItems()?.mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        } ?: emptyList()
                    traceLogger?.invoke("BrowseClientContinuationResponse: status=$statusCode, branch=APPEND_ACTION, rawItems=$rawCount, mappedItems=${items.size}, continuation=${continuationItems?.getContinuation() != null}")
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else continuationItems?.getContinuation(),
                    )
                }
            }
        }.onFailure { e ->
            traceLogger?.invoke("BrowseClientContinuationFailure: continuationLen=${continuation.length}, error=${e::class.java.simpleName}: ${e.message}")
        }
}
