package moe.rukamori.archivetune.playlistcopy

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaylistCopierTest {
    private val database = mockk<MusicDatabase>(relaxed = true)
    private val spotifyReader = mockk<SpotifyPlaylistReader>()
    private val personalReader = mockk<PersonalPlaylistReader>()
    private val youTubeReader = mockk<YouTubePlaylistReader>()
    private val writer = mockk<PlaylistCopyWriter>()
    private var searches = 0
    private var resolve: suspend (SpotifyTrack) -> MediaMetadata? = { null }

    private val copier =
        PlaylistCopier(database, spotifyReader, personalReader, youTubeReader, writer) { track ->
            searches++
            resolve(track)
        }

    private val spotifySource = PlaylistCopySource.Spotify(playlistId = "sp", name = "Road trip", thumbnailUrl = "cover")

    private fun spotifyTrack(id: String) =
        SpotifyTrack(id = id, name = "Song $id", artists = listOf(SpotifySimpleArtist(name = "Artist")), durationMs = 180_000)

    private fun youtube(id: String) = MediaMetadata(id = id, title = "Song", artists = emptyList(), duration = 180)

    private fun existingPlaylist(browseId: String?) =
        Playlist(
            playlist = PlaylistEntity(id = "target", name = "Target", browseId = browseId),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    @Test
    fun spotifyToPersonalWritesPlaceholdersWithoutSearchingYouTube() =
        runBlocking {
            coEvery { spotifyReader.read("sp") } returns listOf(spotifyTrack("1"), spotifyTrack("2"))
            val written = slot<List<CopyTrack>>()
            coEvery { writer.createPersonal("Road trip", "sp", "cover", capture(written)) } returns PlaylistWrite("copy", 2)

            val outcome =
                copier.copy(
                    PlaylistCopyRequest(spotifySource, PlaylistCopyDestination.NewPersonal("Road trip", sourceSpotifyId = "sp")),
                )

            assertEquals(listOf("spotify:1", "spotify:2"), written.captured.map { it.songId })
            assertEquals(0, searches)
            assertEquals(PlaylistCopyResult("copy", added = 2, failed = emptyList()), (outcome as PlaylistCopyState.Success).result)
        }

    @Test
    fun spotifyToYouTubeMatchesEverySongAndReportsTheOnesItCannotMatch() =
        runBlocking {
            coEvery { spotifyReader.read("sp") } returns listOf(spotifyTrack("1"), spotifyTrack("2"), spotifyTrack("3"))
            resolve = { track -> youtube("yt-${track.id}").takeIf { track.id != "2" } }
            val written = slot<List<CopyTrack>>()
            coEvery { writer.createYouTube("Road trip", "cover", capture(written)) } returns PlaylistWrite("copy", 2)

            val outcome =
                copier.copy(
                    PlaylistCopyRequest(spotifySource, PlaylistCopyDestination.NewYouTube("Road trip")),
                )

            assertEquals(listOf("yt-1", "yt-3"), written.captured.map { it.songId })
            val result = (outcome as PlaylistCopyState.Success).result
            assertEquals(2, result.added)
            assertEquals(listOf("Song 2"), result.failed.map { it.title })
        }

    @Test
    fun nothingIsWrittenWhenNoSongCouldBeMatched() =
        runBlocking {
            coEvery { spotifyReader.read("sp") } returns listOf(spotifyTrack("1"))
            resolve = { throw IOException("rate limited") }

            val outcome =
                copier.copy(PlaylistCopyRequest(spotifySource, PlaylistCopyDestination.NewYouTube("Road trip")))

            val failure = outcome as PlaylistCopyState.Failure
            assertEquals(listOf(CopyFailureReason.SEARCH_FAILED), failure.failed.map { it.reason })
            coVerify(exactly = 0) { writer.createYouTube(any(), any(), any()) }
        }

    @Test
    fun localFilesAreNotUploadedToYouTube() =
        runBlocking {
            val local = CopyTrack(songId = "LA_1", metadata = youtube("LA_1"), isLocalFile = true)
            val remote = CopyTrack(songId = "yt1", metadata = youtube("yt1"))
            coEvery { personalReader.read("local") } returns listOf(local, remote)
            coEvery { database.getPlaylistById("target") } returns existingPlaylist(browseId = "PL1")
            val written = slot<List<CopyTrack>>()
            coEvery { writer.appendYouTube("target", capture(written)) } returns 1

            val outcome =
                copier.copy(
                    PlaylistCopyRequest(
                        PlaylistCopySource.Personal("local", "Local"),
                        PlaylistCopyDestination.Existing("target"),
                    ),
                )

            assertEquals(listOf("yt1"), written.captured.map { it.songId })
            assertEquals(listOf(CopyFailureReason.LOCAL_FILE), (outcome as PlaylistCopyState.Success).result.failed.map { it.reason })
        }

    @Test
    fun copyingIntoAnExistingPersonalPlaylistReportsHowManySongsWereAdded() =
        runBlocking {
            val tracks = listOf(CopyTrack("a", youtube("a")), CopyTrack("b", youtube("b")), CopyTrack("c", youtube("c")))
            coEvery { youTubeReader.read("PL9") } returns tracks
            coEvery { database.getPlaylistById("target") } returns existingPlaylist(browseId = null)
            coEvery { writer.appendPersonal("target", tracks) } returns 1

            val result =
                (
                    copier.copy(
                        PlaylistCopyRequest(
                            PlaylistCopySource.YouTube("PL9", "Mix", thumbnailUrl = null),
                            PlaylistCopyDestination.Existing("target"),
                        ),
                    ) as PlaylistCopyState.Success
                ).result

            assertEquals(1, result.added)
            assertEquals("target", result.playlistId)
        }

    @Test
    fun placeholdersInAPersonalPlaylistAreMatchedBeforeTheyGoToYouTube() =
        runBlocking {
            val placeholder = CopyTrack("spotify:1", MediaMetadata(id = "spotify:1", title = "Song 1", artists = emptyList(), duration = 180))
            val plain = CopyTrack("yt-plain", youtube("yt-plain"))
            val unmatched = CopyTrack("spotify:2", MediaMetadata(id = "spotify:2", title = "Song 2", artists = emptyList(), duration = 180))
            coEvery { personalReader.read("local") } returns listOf(placeholder, plain, unmatched)
            resolve = { track -> youtube("yt-1").takeIf { track.id == "spotify:1" } }
            val written = slot<List<CopyTrack>>()
            coEvery { writer.createYouTube("Mix", null, capture(written)) } returns PlaylistWrite("copy", 2)

            val outcome =
                copier.copy(
                    PlaylistCopyRequest(PlaylistCopySource.Personal("local", "Local"), PlaylistCopyDestination.NewYouTube("Mix")),
                )

            assertEquals(listOf("yt-1", "yt-plain"), written.captured.map { it.songId })
            assertEquals(listOf("Song 2"), (outcome as PlaylistCopyState.Success).result.failed.map { it.title })
        }

    @Test
    fun placeholdersAreKeptWhenCopyingBetweenPersonalPlaylists() =
        runBlocking {
            val placeholder = CopyTrack("spotify:1", MediaMetadata(id = "spotify:1", title = "Song 1", artists = emptyList(), duration = 180))
            coEvery { personalReader.read("local") } returns listOf(placeholder)
            val written = slot<List<CopyTrack>>()
            coEvery { writer.createPersonal("Mix", null, null, capture(written)) } returns PlaylistWrite("copy", 1)

            copier.copy(PlaylistCopyRequest(PlaylistCopySource.Personal("local", "Local"), PlaylistCopyDestination.NewPersonal("Mix")))

            assertEquals(listOf("spotify:1"), written.captured.map { it.songId })
            assertEquals(0, searches)
        }

    @Test
    fun aMissingTargetPlaylistFailsTheCopy() =
        runBlocking {
            coEvery { database.getPlaylistById("gone") } returns null

            val outcome =
                copier.copy(
                    PlaylistCopyRequest(
                        PlaylistCopySource.Personal("local", "Local"),
                        PlaylistCopyDestination.Existing("gone"),
                    ),
                )

            assertTrue(outcome is PlaylistCopyState.Failure)
        }

    @Test
    fun reportsReadingThenWritingProgress() =
        runBlocking {
            coEvery { spotifyReader.read("sp") } returns listOf(spotifyTrack("1"))
            coEvery { writer.createPersonal(any(), any(), any(), any()) } returns PlaylistWrite("copy", 1)
            val stages = mutableListOf<PlaylistCopyStage>()

            copier.copy(PlaylistCopyRequest(spotifySource, PlaylistCopyDestination.NewPersonal("Road trip"))) {
                stages += it.stage
            }

            assertEquals(listOf(PlaylistCopyStage.READING, PlaylistCopyStage.WRITING), stages)
        }
}
