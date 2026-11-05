package moe.rukamori.archivetune.playlistcopy

import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PlaylistCopyWriterTest {
    private fun track(id: String) = CopyTrack(songId = id, metadata = null)

    @Test
    fun onlyTracksThatAreNotInThePlaylistYetAreAddedInOrder() {
        val added = tracksToAdd(setOf("b"), listOf(track("a"), track("b"), track("c")))

        assertEquals(listOf("a", "c"), added.map { it.songId })
    }

    @Test
    fun repeatsWithinTheCopiedTracksAreAddedOnce() {
        val added = tracksToAdd(emptySet(), listOf(track("a"), track("a"), track("b")))

        assertEquals(listOf("a", "b"), added.map { it.songId })
    }

    @Test
    fun youTubeCopyThatCannotBeWrittenLocallyIsRemovedFromYouTube() =
        runBlocking {
            mockkObject(YouTube)
            try {
                val database = mockk<MusicDatabase>(relaxed = true)
                coEvery { YouTube.createPlaylist("Mix") } returns Result.success("PL1")
                coEvery { YouTube.addSongsToPlaylist("PL1", listOf("a"), any(), any()) } returns Result.success(listOf("s1"))
                coEvery { YouTube.deletePlaylist("PL1") } returns Result.success(mockk<HttpResponse>())
                // The local write fails: the transaction block reads the new playlist back and finds nothing.
                coEvery { database.getPlaylistById(any()) } returns null
                coEvery { database.withTransaction<String>(any()) } coAnswers {
                    firstArg<suspend MusicDatabase.() -> String>().invoke(database)
                }

                try {
                    PlaylistCopyWriter(database).createYouTube("Mix", null, listOf(track("a")))
                    fail("expected the copy to fail")
                } catch (expected: IllegalStateException) {
                    // The playlist created on YouTube must not be left behind.
                }

                coVerify(exactly = 1) { YouTube.deletePlaylist("PL1") }
            } finally {
                unmockkAll()
            }
        }

    @Test
    fun youTubeCopyThatCannotAddItsSongsIsRemovedFromYouTube() =
        runBlocking {
            mockkObject(YouTube)
            try {
                val database = mockk<MusicDatabase>(relaxed = true)
                coEvery { YouTube.createPlaylist("Mix") } returns Result.success("PL1")
                coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IOException("offline"))
                coEvery { YouTube.deletePlaylist("PL1") } returns Result.success(mockk<HttpResponse>())

                try {
                    PlaylistCopyWriter(database).createYouTube("Mix", null, listOf(track("a")))
                    fail("expected the copy to fail")
                } catch (expected: IOException) {
                    // Nothing was written locally and the remote playlist is gone.
                }

                coVerify(exactly = 1) { YouTube.deletePlaylist("PL1") }
                coVerify(exactly = 0) { database.insert(any<moe.rukamori.archivetune.db.entities.PlaylistEntity>()) }
            } finally {
                unmockkAll()
            }
        }
}
