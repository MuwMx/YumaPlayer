package moe.rukamori.archivetune.spotify

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SpotifyPlaybackResolverTest {
    @Before
    fun setUp() {
        mockkObject(YouTube)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun spotifyTrack(id: String) =
        SpotifyTrack(
            id = id,
            name = "Song $id",
            artists = listOf(SpotifySimpleArtist(name = "Artist")),
            durationMs = 200_000,
        )

    private fun song(
        id: String,
        title: String,
        artist: String,
        durationSec: Int,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist, id = null)),
        duration = durationSec,
        thumbnail = "",
    )

    private fun failureOfSummary() = Result.failure<moe.rukamori.archivetune.innertube.pages.SearchSummaryPage>(IOException("not used"))

    private fun failSearches() {
        coEvery { YouTube.search(any(), any()) } returns Result.failure(IOException("rate limited"))
        coEvery { YouTube.searchSummary(any()) } returns Result.failure(IOException("rate limited"))
    }

    @Test
    fun failedSearchesThrowWhenTheCallerWantsToTellThemFromNoMatch() =
        runBlocking {
            failSearches()

            try {
                SpotifyPlaybackResolver.resolveToMetadata(spotifyTrack("failing"), throwOnSearchFailure = true)
                fail("expected an IOException")
            } catch (expected: IOException) {
                // A failed search must not be mistaken for a song that is not on YouTube.
            }
        }

    @Test
    fun failedSearchesAreNoMatchByDefault() =
        runBlocking {
            failSearches()

            assertNull(SpotifyPlaybackResolver.resolveToMetadata(spotifyTrack("failing-default")))
        }

    @Test
    fun emptySearchResultsAreNoMatchEvenWhenTheCallerThrowsOnFailure() =
        runBlocking {
            coEvery { YouTube.search(any(), any()) } returns Result.success(SearchResult(items = emptyList(), continuation = null))
            coEvery { YouTube.searchSummary(any()) } returns
                Result.success(moe.rukamori.archivetune.innertube.pages.SearchSummaryPage(summaries = emptyList()))

            assertNull(
                SpotifyPlaybackResolver.resolveToMetadata(spotifyTrack("empty"), throwOnSearchFailure = true),
            )
        }

    @Test
    fun acceptsTheSongThatMatchesArtistTitleAndDuration() =
        runBlocking {
            val match = song("yt1", "Song matching", "Artist", 200)
            coEvery { YouTube.search(any(), any()) } returns Result.success(SearchResult(items = listOf(match), continuation = null))
            coEvery { YouTube.searchSummary(any()) } returns failureOfSummary()

            val resolved = SpotifyPlaybackResolver.resolveToMetadata(spotifyTrack("matching"))

            assertEquals("yt1", resolved?.id)
        }
}
