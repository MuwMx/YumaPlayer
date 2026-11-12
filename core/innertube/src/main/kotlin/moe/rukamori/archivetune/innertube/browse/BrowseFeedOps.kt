/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.pages.ExplorePage
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.innertube.pages.NewReleaseAlbumPage

internal object BrowseFeedOps {
    private const val BROWSE_ID_EXPLORE = "FEmusic_explore"
    private const val BROWSE_ID_NEW_RELEASE_ALBUMS = "FEmusic_new_releases_albums"
    private const val BROWSE_ID_MOODS_AND_GENRES = "FEmusic_moods_and_genres"

    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
    ): Result<HomePage> =
        runCatching {
            if (continuation != null) {
                return@runCatching homeContinuation(continuation).getOrThrow()
            }

            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", params = params, setLogin = true).body<BrowseResponse>()
            val nextContinuation =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.continuations
                    ?.getContinuation()
            val sectionListRender =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val sections =
                sectionListRender
                    ?.contents
                    .orEmpty()
                    .mapNotNull { it.toHomeSection() }
                    .toMutableList()
            val chips =
                sectionListRender?.header
                    ?.chipCloudRenderer
                    ?.chips
                    ?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
            HomePage(chips, sections, nextContinuation)
        }

    private fun SectionListRenderer.Content.toHomeSection(): HomePage.Section? =
        musicCarouselShelfRenderer?.let { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
            ?: musicShelfRenderer?.let { HomePage.Section.fromMusicShelfRenderer(it) }
            ?: musicCardShelfRenderer?.let { HomePage.Section.fromMusicCardShelfRenderer(it) }

    private suspend fun homeContinuation(continuation: String): Result<HomePage> =
        runCatching {
            val response =
                innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            val sections =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    .orEmpty()
                    .mapNotNull { it.toHomeSection() }
            val nextContinuation =
                if (sections.isEmpty()) {
                    null
                } else {
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation()
                }
            HomePage(
                chips = null,
                sections = sections,
                continuation = nextContinuation,
            )
        }

    suspend fun explore(): Result<ExplorePage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = BROWSE_ID_EXPLORE).body<BrowseResponse>()
            ExplorePage(
                newReleaseAlbums =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId ==
                                BROWSE_ID_NEW_RELEASE_ALBUMS
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        .orEmpty(),
                moodAndGenres =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId ==
                                BROWSE_ID_MOODS_AND_GENRES
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicNavigationButtonRenderer }
                        ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                        .orEmpty(),
            )
        }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> =
        runCatching {
            try {
                val directAlbums = newReleaseAlbumsFromBrowsePage()
                if (directAlbums.isNotEmpty()) return@runCatching directAlbums
            } catch (throwable: Throwable) {
                if (!throwable.isBrowsePageUnavailable()) throw throwable
            }
            explore().getOrThrow().newReleaseAlbums
        }

    private suspend fun newReleaseAlbumsFromBrowsePage(): List<AlbumItem> {
        val response = innerTube.browse(WEB_REMIX, browseId = BROWSE_ID_NEW_RELEASE_ALBUMS).body<BrowseResponse>()
        return response.newReleaseAlbumItems()
    }

    private fun BrowseResponse.newReleaseAlbumItems(): List<AlbumItem> {
        val contents =
            this.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?: this.contents
                    ?.sectionListRenderer
                    ?.contents
                ?: continuationContents
                    ?.sectionListContinuation
                    ?.contents
                ?: emptyList()

        return contents
            .asSequence()
            .flatMap { content ->
                sequence {
                    content.gridRenderer
                        ?.items
                        ?.asSequence()
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                    content.musicCarouselShelfRenderer
                        ?.contents
                        ?.asSequence()
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                    content.itemSectionRenderer
                        ?.contents
                        ?.asSequence()
                        ?.mapNotNull { it.gridRenderer }
                        ?.flatMap { it.items.asSequence() }
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                }
            }.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .toList()
    }

    private fun Throwable.isBrowsePageUnavailable(): Boolean {
        val exception = this as? ClientRequestException ?: return false
        return exception.response.status == HttpStatusCode.NotFound ||
            exception.response.status == HttpStatusCode.BadRequest
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = BROWSE_ID_MOODS_AND_GENRES).body<BrowseResponse>()
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents!!
                .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
        }
}
