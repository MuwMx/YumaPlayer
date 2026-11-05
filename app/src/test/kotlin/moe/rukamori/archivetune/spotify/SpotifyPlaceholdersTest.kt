package moe.rukamori.archivetune.spotify

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SpotifyPlaceholdersTest {
    private val database = mockk<MusicDatabase>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(SpotifyPlaybackResolver)
        SpotifyPlaceholders.isOnline = { true }
    }

    @After
    fun tearDown() {
        SpotifyPlaceholders.isOnline = { true }
        unmockkAll()
    }

    private fun placeholder(id: String) = MediaMetadata(id = "spotify:$id", title = "Song $id", artists = emptyList(), duration = 180)

    private fun matched(youtubeId: String) = MediaMetadata(id = youtubeId, title = "Song", artists = emptyList(), duration = 180)

    @Test
    fun concurrentLookupsForOneTrackShareASingleSearch() =
        runBlocking {
            val track = placeholder("shared")
            SpotifyPlaceholders.register(listOf(track))
            coEvery { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) } coAnswers {
                delay(50)
                matched("yt-shared")
            }

            val results = (1..5).map { async { SpotifyPlaceholders.resolve(track.id, database) } }.awaitAll()

            assertEquals(List(5) { "yt-shared" }, results.map { it?.id })
            coVerify(exactly = 1) { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) }
        }

    @Test
    fun trackWithoutAnyMatchIsRememberedAsUnmatched() =
        runBlocking {
            val track = placeholder("nomatch")
            SpotifyPlaceholders.register(listOf(track))
            coEvery { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) } returns null

            assertNull(SpotifyPlaceholders.resolve(track.id, database))
            assertTrue(SpotifyPlaceholders.isKnownUnmatched(track.id))

            // Remembered: no second search for a track already known to be missing.
            assertNull(SpotifyPlaceholders.resolve(track.id, database))
            coVerify(exactly = 1) { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) }
        }

    @Test
    fun nothingIsRememberedAsUnmatchedWhileOffline() =
        runBlocking {
            SpotifyPlaceholders.isOnline = { false }
            val track = placeholder("offline")
            SpotifyPlaceholders.register(listOf(track))
            coEvery { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) } returns null

            assertNull(SpotifyPlaceholders.resolve(track.id, database))

            assertFalse(SpotifyPlaceholders.isKnownUnmatched(track.id))
        }

    @Test
    fun failedSearchIsNotRememberedAsUnmatched() =
        runBlocking {
            val track = placeholder("ratelimited")
            SpotifyPlaceholders.register(listOf(track))
            coEvery { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) } throws IOException("rate limited")

            try {
                SpotifyPlaceholders.resolve(track.id, database)
                org.junit.Assert.fail("expected an IOException")
            } catch (expected: IOException) {
                // The caller can retry later.
            }

            assertFalse(SpotifyPlaceholders.isKnownUnmatched(track.id))
        }

    @Test
    fun idsThatAreNotPlaceholdersAreReturnedUnchanged() =
        runBlocking {
            assertEquals("dQw4w9WgXcQ", SpotifyPlaceholders.youtubeIdFor("dQw4w9WgXcQ"))
            assertNull(SpotifyPlaceholders.resolve("dQw4w9WgXcQ", database))
        }

    @Test
    fun aCallerWaitingOnALookupIsNotCancelledWithTheCallerThatStartedIt() =
        runBlocking {
            val track = placeholder("cancelled-owner")
            SpotifyPlaceholders.register(listOf(track))
            val started = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { SpotifyPlaybackResolver.resolveToMetadata(any(), any(), any(), any()) } coAnswers {
                if (++calls == 1) {
                    started.complete(Unit)
                    awaitCancellation()
                }
                matched("yt-after-retry")
            }

            val owner = async { SpotifyPlaceholders.resolve(track.id, database) }
            started.await()
            val waiter = async { SpotifyPlaceholders.resolve(track.id, database) }
            delay(50)
            owner.cancel()

            assertEquals("yt-after-retry", withTimeout(5_000) { waiter.await() }?.id)
        }
}
