/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.response.GetSearchSuggestionsResponse
import moe.rukamori.archivetune.innertube.models.response.SearchResponse
import moe.rukamori.archivetune.innertube.pages.SearchPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSuggestionPage
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage

object SearchClient {
    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> =
        runCatching {
            val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
            SearchSuggestions(
                queries =
                    response.contents
                        ?.getOrNull(0)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            content.searchSuggestionRenderer
                                ?.suggestion
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                        }.orEmpty(),
                recommendedItems =
                    response.contents
                        ?.getOrNull(1)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }.orEmpty(),
            )
        }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val topItems = mutableListOf<YTItem>()
            val summaries = mutableListOf<SearchSummary>()

            contents.forEach { content ->
                content.musicCardShelfRenderer?.let { renderer ->
                    topItems +=
                        listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(renderer))
                            .plus(
                                renderer.contents
                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                                    .orEmpty(),
                            )
                    return@forEach
                }

                content.itemSectionRenderer?.contents?.let { sectionContents ->
                    topItems +=
                        sectionContents.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }
                    summaries +=
                        sectionContents.mapNotNull { it.musicShelfRenderer?.toSearchSummary() }
                    return@forEach
                }

                content.musicShelfRenderer?.toSearchSummary()?.let(summaries::add)
            }

            SearchSummaryPage(
                summaries =
                    buildList {
                        topItems
                            .distinctBy { it.id }
                            .takeIf { it.isNotEmpty() }
                            ?.let { add(SearchSummary(title = "Top results", items = it)) }
                        addAll(summaries)
                    },
            )
        }

    suspend fun search(
        query: String,
        filter: SearchFilter,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        runCatching {
            val response =
                innerTube
                    .search(
                        client = WEB_REMIX,
                        query = query,
                        params = filter.value,
                        useAccountContext = useAccountContext,
                    ).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val shelves =
                contents.flatMap { content ->
                    buildList {
                        content.musicShelfRenderer?.let { add(it) }
                        content.itemSectionRenderer
                            ?.contents
                            ?.mapNotNull { it.musicShelfRenderer }
                            ?.let { addAll(it) }
                    }
                }
            val inlineItems =
                contents.flatMap { content ->
                    content.itemSectionRenderer
                        ?.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        .orEmpty()
                }
            SearchResult(
                items =
                    shelves
                        .flatMap { it.contents?.getItems().orEmpty() }
                        .plus(inlineItems)
                        .mapNotNull { SearchPage.toYTItem(it) }
                        .distinctBy { it.id },
                continuation =
                    shelves
                        .asSequence()
                        .mapNotNull { it.continuations?.getContinuation() ?: it.contents?.getContinuation() }
                        .firstOrNull(),
            )
        }

    suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        runCatching {
            val response =
                innerTube
                    .search(
                        client = WEB_REMIX,
                        continuation = continuation,
                        useAccountContext = useAccountContext,
                    ).body<SearchResponse>()
            val continuationPage = response.continuationContents?.musicShelfContinuation
            val items =
                continuationPage
                    ?.contents
                    ?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { renderer -> SearchPage.toYTItem(renderer) }
                    }
                    ?: emptyList()
            SearchResult(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        continuationPage?.continuations?.getContinuation()
                            ?: continuationPage
                                ?.contents
                                ?.firstOrNull { it.continuationItemRenderer != null }
                                ?.continuationItemRenderer
                                ?.continuationEndpoint
                                ?.continuationCommand
                                ?.token
                    },
            )
        }

    private fun MusicShelfRenderer.toSearchSummary(): SearchSummary? {
        val items =
            contents
                ?.getItems()
                ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                ?.distinctBy { it.id }
                .orEmpty()
        if (items.isEmpty()) return null

        val title =
            title
                ?.runs
                ?.joinToString(separator = "") { it.text }
                ?.takeIf { it.isNotBlank() }
                ?: "Other"

        return SearchSummary(title = title, items = items)
    }
}
