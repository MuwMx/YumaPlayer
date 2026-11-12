/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.library.LibraryFilter
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.network.libraryContinuation
import moe.rukamori.archivetune.innertube.network.libraryItems
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.pages.LibraryContinuationPage
import moe.rukamori.archivetune.innertube.pages.LibraryPage

internal object BrowseLibraryOps {
    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun library(
        browseId: String,
        tabIndex: Int = 0,
    ): Result<LibraryPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = browseId,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

            val contents =
                if (tabs != null && tabIndex >= 0 && tabIndex < tabs.size) {
                    tabs[tabIndex]
                        .tabRenderer.content
                        ?.sectionListRenderer
                        ?.contents
                        .orEmpty()
                } else {
                    emptyList()
                }
            LibraryPage(
                items = contents.flatMap { it.libraryItems() },
                continuation = contents.firstNotNullOfOrNull { it.libraryContinuation() },
            )
        }

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val contents = response.continuationContents
            val sectionContents = contents?.sectionListContinuation?.contents.orEmpty()
            val sectionItems = sectionContents.flatMap { it.libraryItems() }
            val gridItems =
                contents
                    ?.gridContinuation
                    ?.items
                    .orEmpty()
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }
            val shelfItems =
                contents
                    ?.musicShelfContinuation
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
            val playlistShelfItems =
                contents
                    ?.musicPlaylistShelfContinuation
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
            val items = sectionItems + gridItems + shelfItems + playlistShelfItems
            LibraryContinuationPage(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        sectionContents.firstNotNullOfOrNull { it.libraryContinuation() }
                            ?: contents?.sectionListContinuation?.continuations?.getContinuation()
                            ?: contents?.gridContinuation?.continuations?.getContinuation()
                            ?: contents
                                ?.musicShelfContinuation
                                ?.contents
                                .orEmpty()
                                .getContinuation()
                            ?: contents?.musicShelfContinuation?.continuations?.getContinuation()
                            ?: contents
                                ?.musicPlaylistShelfContinuation
                                ?.contents
                                .orEmpty()
                                .getContinuation()
                            ?: contents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
                    },
            )
        }

    suspend fun libraryRecentActivity(): Result<LibraryPage> =
        runCatching {
            val continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value

            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val gridItems =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.firstOrNull()
                    ?.gridRenderer
                    ?.items

            if (gridItems == null) {
                return@runCatching LibraryPage(
                    items = emptyList(),
                    continuation = null,
                )
            }

            val items =
                gridItems
                    .mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    }.toMutableList()

            items.forEachIndexed { index, item ->
                if (item is ArtistItem) {
                    BrowseArtistOps.artist(item.id).getOrNull()?.artist?.let { fetchedArtist ->
                        items[index] = fetchedArtist.copy(thumbnail = item.thumbnail)
                    }
                }
            }

            LibraryPage(
                items = items,
                continuation = null,
            )
        }

    suspend fun musicHistory(): Result<HistoryPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_history",
                        setLogin = true,
                    ).body<BrowseResponse>()
            HistoryPage(
                sections =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.flatMap(HistoryPage::fromSectionListContent)
                        .orEmpty(),
            )
        }
}
