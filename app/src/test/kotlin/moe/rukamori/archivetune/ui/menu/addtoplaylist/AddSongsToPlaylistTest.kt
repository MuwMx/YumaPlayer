package moe.rukamori.archivetune.ui.menu.addtoplaylist

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
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
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
        every { database.playlistSongMaps("local", 0) } returns emptyList()
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
    fun aSongTheFailedBatchAddedIsSavedWithItsRemoteSetVideoId() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a" to "r1")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 1) { YouTube.addToPlaylist(any(), any()) }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "r1", "b" to "s2")) }
        }

    @Test
    fun aBatchThatConfirmsFewerSongsThanSentDoesNotDropTheRest() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", listOf("a", "b"), any(), any()) } returns Result.success(listOf("s1"))
            remotePlaylistHas("a" to "s1")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "s1", "b" to "s2")) }
        }

    @Test
    fun aBatchResultWithoutASetVideoIdGoesThroughTheFallback() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", listOf("a", "b"), any(), any()) } returns Result.success(listOf("s1", null))
            remotePlaylistHas("a" to "s1", "b" to "r2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 0) { YouTube.addToPlaylist(any(), any()) }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "s1", "b" to "r2")) }
        }

    @Test
    fun aSongThatWasOnThePlaylistBeforeIsNotTakenAsAddedByAFailedBatch() =
        runBlocking {
            localPlaylistHas("a" to "old")
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a" to "old")
            coEvery { YouTube.addToPlaylist("PL1", "a") } returns Result.success("s1")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 1) { YouTube.addToPlaylist("PL1", "a") }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "s1", "b" to "s2")) }
        }

    @Test
    fun aFailedBatchCreditsOnlyTheExtraCopyItAddedWithThatCopysId() =
        runBlocking {
            localPlaylistHas("a" to "old")
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a" to "old", "a" to "new")
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(2, added)
            coVerify(exactly = 0) { YouTube.addToPlaylist("PL1", "a") }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "new", "b" to "s2")) }
        }

    @Test
    fun aCopyStoredWithoutASetVideoIdIsNotCreditedTwice() =
        runBlocking {
            localPlaylistHas("a" to null)
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas("a" to "old", "a" to "new")

            val added = addSongsToPlaylistSafely(playlist, listOf("a"), isLoggedIn = true, database)

            assertEquals(1, added)
            coVerify(exactly = 0) { YouTube.addToPlaylist(any(), any()) }
            verify { database.addSongEntriesToPlaylist(playlist, listOf("a" to "new")) }
        }

    @Test
    fun aSongThatIsNotAddedIsNotSaved() =
        runBlocking {
            coEvery { YouTube.addSongsToPlaylist("PL1", any(), any(), any()) } returns Result.failure(IllegalStateException())
            remotePlaylistHas()
            coEvery { YouTube.addToPlaylist("PL1", "a") } returns Result.failure(IllegalStateException())
            coEvery { YouTube.addToPlaylist("PL1", "b") } returns Result.success("s2")

            val added = addSongsToPlaylistSafely(playlist, listOf("a", "b"), isLoggedIn = true, database)

            assertEquals(1, added)
            verify { database.addSongEntriesToPlaylist(playlist, listOf("b" to "s2")) }
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

    private fun localPlaylistHas(vararg entries: Pair<String, String?>) {
        every { database.playlistSongMaps("local", 0) } returns
            entries.mapIndexed { position, (songId, setVideoId) ->
                PlaylistSongMap(playlistId = "local", songId = songId, position = position, setVideoId = setVideoId)
            }
    }

    private fun remotePlaylistHas(vararg entries: Pair<String, String?>) {
        coEvery { YouTube.playlist("PL1") } returns
            Result.success(
                PlaylistPage(
                    playlist = mockk(relaxed = true),
                    songs = entries.map { (songId, setVideoId) -> song(songId, setVideoId) },
                    songsContinuation = null,
                    continuation = null,
                ),
            )
    }

    private fun song(
        id: String,
        setVideoId: String?,
    ) = SongItem(id = id, title = id, artists = emptyList(), thumbnail = "", setVideoId = setVideoId)
}
