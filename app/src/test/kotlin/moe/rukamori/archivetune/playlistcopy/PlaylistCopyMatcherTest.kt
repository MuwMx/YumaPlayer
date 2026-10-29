package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class PlaylistCopyMatcherTest {
    private fun spotify(id: String) =
        SpotifyTrack(id = id, name = "Song $id", artists = listOf(SpotifySimpleArtist(name = "Artist")))

    private fun youtube(id: String) = MediaMetadata(id = id, title = "Song", artists = emptyList(), duration = 180)

    @Test
    fun keepsPlaylistOrderWhateverOrderTheSearchesFinishIn() =
        runBlocking {
            val tracks = (1..6).map { spotify("t$it") }

            val result =
                matchSpotifyTracks(tracks, resolve = { track ->
                    // Earlier tracks take longer, so they finish last.
                    delay((7 - track.id.removePrefix("t").toLong()) * 10)
                    youtube("yt-${track.id}")
                })

            assertEquals(tracks.map { "yt-${it.id}" }, result.matched.map { it.songId })
            assertTrue(result.failed.isEmpty())
        }

    @Test
    fun tracksWithoutAMatchAreReportedAndTheRestIsKept() =
        runBlocking {
            val tracks = listOf(spotify("a"), spotify("b"), spotify("c"))

            val result = matchSpotifyTracks(tracks, resolve = { track -> youtube("yt-a").takeIf { track.id != "b" } })

            assertEquals(listOf("yt-a", "yt-a"), result.matched.map { it.songId })
            assertEquals(listOf("Song b"), result.failed.map { it.title })
            assertEquals(listOf(CopyFailureReason.NO_MATCH), result.failed.map { it.reason })
        }

    @Test
    fun aFailedSearchIsNotReportedAsMissingFromYouTube() =
        runBlocking {
            val result =
                matchSpotifyTracks(listOf(spotify("a")), resolve = { throw IOException("rate limited") })

            assertEquals(listOf(CopyFailureReason.SEARCH_FAILED), result.failed.map { it.reason })
        }

    @Test
    fun otherErrorsAreNotHidden() =
        runBlocking {
            try {
                matchSpotifyTracks(listOf(spotify("a")), resolve = { throw IllegalStateException("bug") })
                fail("expected an IllegalStateException")
            } catch (expected: IllegalStateException) {
                assertEquals("bug", expected.message)
            }
        }

    @Test
    fun neverRunsMoreSearchesAtOnceThanAllowed() =
        runBlocking {
            val running = AtomicInteger()
            val peak = AtomicInteger()

            matchSpotifyTracks((1..12).map { spotify("t$it") }, concurrency = 3, resolve = {
                peak.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
                delay(20)
                running.decrementAndGet()
                youtube("yt")
            })

            assertEquals(3, peak.get())
        }

    @Test
    fun reportsProgressOncePerTrack() =
        runBlocking {
            val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())

            matchSpotifyTracks((1..4).map { spotify("t$it") }, resolve = { youtube("yt") }, onProgress = { seen += it })

            assertEquals(listOf(1, 2, 3, 4), seen.sorted())
        }
}
