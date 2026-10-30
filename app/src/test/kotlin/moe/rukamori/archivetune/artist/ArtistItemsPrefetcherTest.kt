package moe.rukamori.archivetune.artist

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class ArtistItemsPrefetcherTest {
    private val albumsEndpoint = BrowseEndpoint(browseId = "MPADalbums", params = "p1")
    private val singlesEndpoint = BrowseEndpoint(browseId = "MPADsingles", params = "p2")
    private val page = ArtistItemsPage(title = "Albums", items = emptyList(), continuation = null, layout = ArtistItemsPageLayout.GRID)
    private val album = AlbumItem(browseId = "MPREb_1", playlistId = "OLAK1", title = "One", artists = null, thumbnail = "")

    private fun section(
        title: String,
        endpoint: BrowseEndpoint?,
    ) = ArtistSection(title = title, items = listOf(album), moreEndpoint = endpoint, layout = ArtistSectionLayout.GRID)

    private class Fixture(
        scope: TestScope,
        var clock: Long = 0L,
        val loads: MutableList<BrowseEndpoint> = mutableListOf(),
        var answer: suspend (BrowseEndpoint) -> Result<ArtistItemsPage>,
    ) {
        val prefetcher =
            ArtistItemsPrefetcher(scope.backgroundScope, { endpoint ->
                loads += endpoint
                answer(endpoint)
            }, { clock })
    }

    @Test
    fun theAlbumsScreenGetsTheListTheArtistPageStarted() =
        runTest {
            val f = Fixture(this, answer = { Result.success(page) })

            f.prefetcher.prefetchAlbums(listOf(section("Albums", albumsEndpoint), section("Singles & EPs", singlesEndpoint)))
            runCurrent()

            // the Albums screen rebuilds the endpoint from its route, without the page context
            assertSame(page, f.prefetcher.prefetched(BrowseEndpoint(browseId = "MPADalbums", params = "p1")))
            assertEquals(listOf(albumsEndpoint), f.loads)
        }

    @Test
    fun anAlbumsScreenOpenedMidLoadWaitsForThatLoadInsteadOfStartingAnother() =
        runTest {
            val gate = CompletableDeferred<Result<ArtistItemsPage>>()
            val f = Fixture(this, answer = { gate.await() })

            f.prefetcher.prefetch(albumsEndpoint)
            val shown = async { f.prefetcher.prefetched(albumsEndpoint) }
            runCurrent()
            gate.complete(Result.success(page))

            assertSame(page, shown.await())
            assertEquals(1, f.loads.size)
        }

    @Test
    fun aFailedPrefetchLeavesTheAlbumsScreenToLoadTheListItself() =
        runTest {
            val f = Fixture(this, answer = { Result.failure(IOException("403")) })

            f.prefetcher.prefetch(albumsEndpoint)

            assertNull(f.prefetcher.prefetched(albumsEndpoint))
            // the failure is not kept: the next artist page visit tries again
            f.prefetcher.prefetch(albumsEndpoint)
            runCurrent()
            assertEquals(2, f.loads.size)
        }

    @Test
    fun anOldListIsNotShownAndIsLoadedAgain() =
        runTest {
            val f = Fixture(this, answer = { Result.success(page) })

            f.prefetcher.prefetch(albumsEndpoint)
            runCurrent()
            f.clock = ArtistItemsPrefetcher.MAX_AGE_MS

            assertNull(f.prefetcher.prefetched(albumsEndpoint))
            f.prefetcher.prefetch(albumsEndpoint)
            runCurrent()
            assertEquals(2, f.loads.size)
        }

    @Test
    fun anArtistWhoseAlbumsAllFitOnThePageCostsNoExtraRequest() =
        runTest {
            val f = Fixture(this, answer = { Result.success(page) })

            f.prefetcher.prefetchAlbums(listOf(section("Albums", null)))
            runCurrent()

            assertEquals(emptyList<BrowseEndpoint>(), f.loads)
        }

    @Test
    fun aBlankParamsMatchesTheRouteThatDropsIt() =
        runTest {
            val f = Fixture(this, answer = { Result.success(page) })

            f.prefetcher.prefetch(BrowseEndpoint(browseId = "MPADalbums", params = ""))

            assertSame(page, f.prefetcher.prefetched(BrowseEndpoint(browseId = "MPADalbums", params = null)))
        }
}
