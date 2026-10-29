package moe.rukamori.archivetune.playlistcopy

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.spotify.SpotifyAccountSession
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class SpotifyCopySyncTest {
    private val database = mockk<MusicDatabase>(relaxed = true)
    private val repository = mockk<SpotifyLibraryRepository>()
    private val writer = mockk<PlaylistCopyWriter>()
    private val sync = SpotifyCopySync(database, repository, writer)
    private val spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())

    init {
        every { repository.playlists } returns spotifyPlaylists
        coEvery { repository.restoreSession() } returns SpotifyAccountSession(isAuthenticated = true)
        coEvery { repository.playlistsWithoutCounts() } returns emptyList()
        every { database.playlistsByCreateDateAsc() } returns flowOf(emptyList())
    }

    private fun spotifyPlaylist(
        id: String,
        total: Int,
    ) = SpotifyPlaylist(id = id, name = "Playlist $id", tracks = SpotifyPlaylistTracksRef(total = total))

    private fun spotifyTrack(id: String) = SpotifyTrack(id = id, name = "Song $id")

    private fun copy(
        sourceId: String,
        remoteSongCount: Int,
        songCount: Int,
        browseId: String? = null,
    ) = Playlist(
        playlist =
            PlaylistEntity(
                id = "copy-$sourceId",
                name = "Playlist $sourceId",
                browseId = browseId,
                spotifySourceId = sourceId,
                remoteSongCount = remoteSongCount,
            ),
        songCount = songCount,
        songThumbnails = emptyList(),
    )

    @Test
    fun doesNothingWithoutASpotifySession() =
        runBlocking {
            coEvery { repository.restoreSession() } returns SpotifyAccountSession(isAuthenticated = false)

            assertNull(sync.sync())
            coVerify(exactly = 0) { writer.writeSpotifyCopies(any()) }
        }

    @Test
    fun createsACopyOfEveryPlaylistInOneWrite() =
        runBlocking {
            spotifyPlaylists.value = listOf(spotifyPlaylist("a", 2), spotifyPlaylist("b", 1))
            coEvery { repository.playlistTracks("a") } returns listOf(spotifyTrack("1"), spotifyTrack("2"))
            coEvery { repository.playlistTracks("b") } returns listOf(spotifyTrack("3"))
            val plans = slot<List<SpotifyCopyPlan>>()
            coEvery { writer.writeSpotifyCopies(capture(plans)) } returns SpotifyCopyWriteResult(created = 2, updated = 0, addedTracks = 3)

            val report = sync.sync()

            assertEquals(SpotifyCopySyncReport(playlists = 2, created = 2, updated = 0, failed = 0, addedTracks = 3), report)
            assertEquals(listOf("a", "b"), plans.captured.map { it.spotifyId })
            assertEquals(listOf("spotify:1", "spotify:2"), plans.captured[0].tracks.map { it.songId })
            assertEquals(listOf(null, null), plans.captured.map { it.existingCopyId })
            coVerify(exactly = 1) { writer.writeSpotifyCopies(any()) }
        }

    @Test
    fun leavesACopyAloneWhenItAlreadyHasEverySong() =
        runBlocking {
            spotifyPlaylists.value = listOf(spotifyPlaylist("a", 2), spotifyPlaylist("b", 3))
            every { database.playlistsByCreateDateAsc() } returns
                flowOf(listOf(copy("a", remoteSongCount = 2, songCount = 2), copy("b", remoteSongCount = 2, songCount = 2)))
            coEvery { repository.playlistTracks("b") } returns listOf(spotifyTrack("1"), spotifyTrack("2"), spotifyTrack("3"))
            val plans = slot<List<SpotifyCopyPlan>>()
            coEvery { writer.writeSpotifyCopies(capture(plans)) } returns SpotifyCopyWriteResult(0, 1, 1)

            sync.sync()

            assertEquals(listOf("b"), plans.captured.map { it.spotifyId })
            assertEquals("copy-b", plans.captured.single().existingCopyId)
            coVerify(exactly = 0) { repository.playlistTracks("a") }
        }

    @Test
    fun leavesACopyThatWasMovedToYouTubeAlone() =
        runBlocking {
            spotifyPlaylists.value = listOf(spotifyPlaylist("a", 5))
            every { database.playlistsByCreateDateAsc() } returns
                flowOf(listOf(copy("a", remoteSongCount = 2, songCount = 2, browseId = "PL1")))

            sync.sync()

            coVerify(exactly = 0) { repository.playlistTracks(any()) }
            coVerify(exactly = 0) { writer.writeSpotifyCopies(any()) }
        }

    @Test
    fun aPlaylistThatCannotBeReadDoesNotStopTheOthers() =
        runBlocking {
            spotifyPlaylists.value = listOf(spotifyPlaylist("a", 1), spotifyPlaylist("b", 1))
            coEvery { repository.playlistTracks("a") } throws IOException("offline")
            coEvery { repository.playlistTracks("b") } returns listOf(spotifyTrack("1"))
            val plans = slot<List<SpotifyCopyPlan>>()
            coEvery { writer.writeSpotifyCopies(capture(plans)) } returns SpotifyCopyWriteResult(1, 0, 1)

            val report = sync.sync()

            assertEquals(1, report?.failed)
            assertEquals(listOf("b"), plans.captured.map { it.spotifyId })
        }

    @Test
    fun theCopyRemembersSpotifysTrackCountEvenWhenLocalFilesAreLeftOut() =
        runBlocking {
            spotifyPlaylists.value = listOf(spotifyPlaylist("a", 3))
            coEvery { repository.playlistTracks("a") } returns listOf(spotifyTrack("1"), spotifyTrack("2"))
            val plans = slot<List<SpotifyCopyPlan>>()
            coEvery { writer.writeSpotifyCopies(capture(plans)) } returns SpotifyCopyWriteResult(1, 0, 2)

            sync.sync()

            assertEquals(3, plans.captured.single().remoteCount)
        }

    @Test
    fun aColdStartListsPlaylistsWithoutAskingForEachTrackCount() =
        runBlocking {
            coEvery { repository.playlistsWithoutCounts() } returns listOf(SpotifyPlaylist(id = "a", name = "Playlist a"))
            coEvery { repository.playlistTracks("a") } returns listOf(spotifyTrack("1"))
            val plans = slot<List<SpotifyCopyPlan>>()
            coEvery { writer.writeSpotifyCopies(capture(plans)) } returns SpotifyCopyWriteResult(1, 0, 1)

            sync.sync()

            // A playlist without a count cannot be known to be up to date, so it is read.
            assertEquals(listOf("a"), plans.captured.map { it.spotifyId })
            coVerify(exactly = 0) { repository.refreshPlaylists() }
        }
}
