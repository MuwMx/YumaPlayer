package moe.rukamori.archivetune.spotify.library

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SpotifyPlaylistsRepositoryTest {
    private val session = mockk<SpotifySessionRepository>(relaxed = true)
    private val repository = SpotifyPlaylistsRepository(session, mockk(relaxed = true))

    @Before
    fun setUp() {
        coEvery { session.spotifyCallWithTokenRetry<Any>(any()) } coAnswers { firstArg<suspend () -> Any>().invoke() }
        mockkObject(Spotify)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Serves a playlist of [total] tracks, returning at most [maxPageSize] items per request. */
    private fun servePlaylist(
        total: Int,
        maxPageSize: Int = Int.MAX_VALUE,
        localIndices: Set<Int> = emptySet(),
    ) {
        coEvery { Spotify.playlistTracks("p", any(), any()) } coAnswers {
            val limit = secondArg<Int>()
            val offset = thirdArg<Int>()
            val items =
                (offset until minOf(total, offset + minOf(limit, maxPageSize))).map { index ->
                    SpotifyPlaylistTrack(track = SpotifyTrack(id = "t$index", isLocal = index in localIndices))
                }
            Result.success(SpotifyPaging(items = items, total = total, offset = offset))
        }
    }

    private fun ids(total: Int) = (0 until total).map { "t$it" }

    @Test
    fun readsEveryPageInOrder() =
        runBlocking {
            servePlaylist(total = 1_250)

            assertEquals(ids(1_250), repository.playlistTracks("p").map { it.id })
        }

    @Test
    fun smallPlaylistNeedsOneRequest() =
        runBlocking {
            servePlaylist(total = 30)

            assertEquals(ids(30), repository.playlistTracks("p").map { it.id })
            coVerify(exactly = 1) { Spotify.playlistTracks("p", any(), any()) }
        }

    @Test
    fun emptyPlaylistReturnsNothing() =
        runBlocking {
            servePlaylist(total = 0)

            assertEquals(emptyList<SpotifyTrack>(), repository.playlistTracks("p"))
        }

    @Test
    fun shortPagesAreFilledWithoutLosingOrReorderingTracks() =
        runBlocking {
            servePlaylist(total = 1_250, maxPageSize = 100)

            assertEquals(ids(1_250), repository.playlistTracks("p").map { it.id })
        }

    @Test
    fun aPlaylistThatChangesWhileItIsReadIsReadAgain() =
        runBlocking {
            // the first read sees "t0" removed after its first page; the second read sees the new playlist only
            var reads = 0
            coEvery { Spotify.playlistTracks("p", any(), any()) } coAnswers {
                val limit = secondArg<Int>()
                val offset = thirdArg<Int>()
                if (offset == 0) reads++
                val playlist = if (reads == 1 && offset == 0) ids(1_250) else ids(1_250).drop(1)
                val items = playlist.drop(offset).take(limit).map { SpotifyPlaylistTrack(track = SpotifyTrack(id = it)) }
                Result.success(SpotifyPaging(items = items, total = playlist.size, offset = offset))
            }

            assertEquals(ids(1_250).drop(1), repository.playlistTracks("p").map { it.id })
            assertEquals(2, reads)
        }

    @Test
    fun aPlaylistThatKeepsChangingIsReadOnePageAfterTheOther() =
        runBlocking {
            // every later page reports a different total, so parallel pages never fit together
            coEvery { Spotify.playlistTracks("p", any(), any()) } coAnswers {
                val limit = secondArg<Int>()
                val offset = thirdArg<Int>()
                val items = ids(700).drop(offset).take(limit).map { SpotifyPlaylistTrack(track = SpotifyTrack(id = it)) }
                val total = if (offset == 0 || limit == 100) 700 else 701
                Result.success(SpotifyPaging(items = items, total = total, offset = offset))
            }

            assertEquals(ids(700), repository.playlistTracks("p").map { it.id })
            coVerify { Spotify.playlistTracks("p", 100, 600) }
        }

    @Test
    fun localTracksAreLeftOut() =
        runBlocking {
            servePlaylist(total = 700, localIndices = setOf(3, 600))

            assertEquals(ids(700) - setOf("t3", "t600"), repository.playlistTracks("p").map { it.id })
        }
}
