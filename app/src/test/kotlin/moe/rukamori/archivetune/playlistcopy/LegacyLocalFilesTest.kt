package moe.rukamori.archivetune.playlistcopy

import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyLocalFilesTest {
    private fun localFile(
        name: String,
        seconds: Int,
    ) = CopyTrack(
        songId = "spotify:local-$name",
        metadata = MediaMetadata(id = "spotify:local-$name", title = name, artists = emptyList(), duration = seconds),
    )

    @Test
    fun eachOldRowGetsTheNextLocalFileOfItsLength() {
        val files = listOf(localFile("a", 329), localFile("b", 241), localFile("c", 329))

        val assigned = assignLegacyLocalFiles(listOf("spotify:329", "spotify:241", "spotify:329"), files)

        assertEquals(listOf("spotify:local-a", "spotify:local-b", "spotify:local-c"), assigned.map { it?.songId })
    }

    @Test
    fun aRowWithNoLocalFileLeftGetsNothing() {
        val assigned = assignLegacyLocalFiles(listOf("spotify:329", "spotify:329"), listOf(localFile("a", 330)))

        assertEquals(listOf("spotify:local-a", null), assigned.map { it?.songId })
    }

    @Test
    fun regularTracksAreNeverUsed() {
        val track = CopyTrack(songId = "spotify:0cQVqPuHQP4KEwc7ZUQmj6", metadata = MediaMetadata(id = "x", title = "t", artists = emptyList(), duration = 329))

        assertEquals(listOf(null), assignLegacyLocalFiles(listOf("spotify:329"), listOf(track)))
    }
}
