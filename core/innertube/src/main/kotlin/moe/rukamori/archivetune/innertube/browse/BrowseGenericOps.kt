/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.RelatedPage

internal object BrowseGenericOps {
    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
            val browseItems =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.mapNotNull { content ->
                        when {
                            content.gridRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                        content.gridRenderer.header
                                            ?.gridHeaderRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text,
                                    items =
                                        content.gridRenderer.items
                                            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                            .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                )
                            }

                            content.musicCarouselShelfRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                        content.musicCarouselShelfRenderer.header
                                            ?.musicCarouselShelfBasicHeaderRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text,
                                    items =
                                        content.musicCarouselShelfRenderer.contents
                                            .mapNotNull { item ->
                                                item.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                                                    ?: item.musicResponsiveListItemRenderer?.let(BrowseChartItemConverter::convertToChartItem)
                                            },
                                )
                            }

                            content.musicShelfRenderer != null -> {
                                val items =
                                    content.musicShelfRenderer.contents
                                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                        ?.mapNotNull(BrowseChartItemConverter::convertToChartItem)
                                        .orEmpty()
                                if (items.isNotEmpty()) {
                                    BrowseResult.Item(
                                        title =
                                            content.musicShelfRenderer.title
                                                ?.runs
                                                ?.firstOrNull()
                                                ?.text,
                                        items = items,
                                    )
                                } else {
                                    null
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    }.orEmpty()
            BrowseResult(
                title =
                    response.header
                        ?.musicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                thumbnail =
                    response.header
                        ?.musicImmersiveHeaderRenderer
                        ?.thumbnail
                        ?.musicThumbnailRenderer
                        ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicVisualHeaderRenderer
                            ?.foregroundThumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicDetailHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicEditablePlaylistDetailHeaderRenderer
                            ?.header
                            ?.musicDetailHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicEditablePlaylistDetailHeaderRenderer
                            ?.header
                            ?.musicResponsiveHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicHeaderRenderer
                            ?.thumbnail
                            ?.thumbnails
                            ?.lastOrNull()
                            ?.normalizedUrl
                        ?: response.header
                            ?.musicHeaderRenderer
                            ?.straplineThumbnail
                            ?.thumbnails
                            ?.lastOrNull()
                            ?.normalizedUrl
                        ?: browseItems
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnail }
                            .firstOrNull(),
                items = browseItems,
            )
        }
}
