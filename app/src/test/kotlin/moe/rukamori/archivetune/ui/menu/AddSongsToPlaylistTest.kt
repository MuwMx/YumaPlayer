package moe.rukamori.archivetune.ui.menu

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AddSongsToPlaylistTest {
    private val database = mockk<MusicDatabase>(relaxed = true)
    private val playlist =
        Playlist(
            playlist = PlaylistEntity(id = "local", name = "Mine", browseId = "PL1"),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    @Before
    fun setUp() {
        mockkObject(YouTube)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun youTubePlaylistReceivesAllSongsInOneRequest() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", listOf("a", "b", "c"), any(), any()) } returns
                Result.success(listOf("s1", "s2", "s3"))

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b", "c"), isLoggedIn = true, database)

            assertEquals(3, added)
            coVerify(exactly = 0) { YouTube.addToPlaylist(any(), any()) }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "s1", "b" to "s2", "c" to "s3")) }
        }

    @Test
    fun failedBatchOnlyRetriesSongsThatAreNotOnThePlaylistYet() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            coEvery { YouTube.playlist("PL1") } returns
                Result.success(
                    PlaylistPage(
                        playlist = mockk(relaxed = true),
                        songs = listOf(song("a")),
                        songsContinuation = null,
                        continuation = null,
                    ),
                )
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 1) { YouTube.addToPlaylist(any(), any()) }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to null, "b" to "s2")) }
        }

    @Test
    fun aBatchThatConfirmsFewerSongsThanSentDoesNotDropTheRest() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", listOf("a", "b"), any(), any()) } returns Result.success(listOf("s1"))
            remotePlaylistHas("a")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to null, "b" to "s2")) }
        }

    @Test
    fun aSongThatWasOnThePlaylistBeforeIsNotTakenAsAddedByAFailedBatch() =
        runBlocking {
            // the user chose to add "a" again; the failed batch added nothing
            every { database.playlistDuplicates("local", listOf("a", "b")) } returns listOf("a")
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a")
            coEvery { YouTube.addToPlaylist("PL1", "a") } returns Result.success("s1")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 1) { YouTube.addToPlaylist("PL1", "a") }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "s1", "b" to "s2")) }
        }

    @Test
    fun aFailedBatchCreditsOnlyTheExtraCopiesItAdded() =
        runBlocking {
            // "a" was there once and the batch got a second copy in before failing on "b"
            every { database.playlistDuplicates("local", listOf("a", "b")) } returns listOf("a")
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a", "a")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 0) { YouTube.addToPlaylist("PL1", "a") }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to null, "b" to "s2")) }
        }

    @Test
    fun playlistWithoutYouTubeLinkIsWrittenLocally() =
        runBlocking {
            val local = playlist.copy(playlist = playlist.playlist.copy(browseId = null))

            val added = addSongsToPlaylistSafely(local, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 0) { YouTube.addSongsToPlaylist(any(), any(), any(), any()) }
            verify { database.addSongToPlaylist(local, listOf("a", "b")) }
        }

    private fun remotePlaylistHas(vararg songIds: String) {
        coEvery { YouTube.playlist("PL1") } returns
            Result.success(
                PlaylistPage(
                    playlist = mockk(relaxed = true),
                    songs = songIds.map(::song),
                    songsContinuation = null,
                    continuation = null,
                ),
            )
    }

    private fun song(id: String) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "")
}
