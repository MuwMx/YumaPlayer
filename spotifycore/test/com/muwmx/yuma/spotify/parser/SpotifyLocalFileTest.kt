package moe.rukamori.archivetune.spotify.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLocalFileTest {
    @Test
    fun theUriIsReadIntoArtistAlbumTitleAndLength() {
        val file = SpotifyLocalFile.parse("spotify:local:Kendrick+Lamar:DAMN.:PRAYER.:329")!!

        assertEquals("Kendrick Lamar", file.artist)
        assertEquals("DAMN.", file.album)
        assertEquals("PRAYER.", file.title)
        assertEquals(329, file.durationSec)
        assertTrue(file.id.startsWith(SpotifyLocalFile.ID_PREFIX))
    }

    @Test
    fun localFilesOfTheSameLengthGetDifferentIds() {
        val prayer = SpotifyLocalFile.parse("spotify:local:Kendrick+Lamar:DAMN.:PRAYER.:329")!!
        val other = SpotifyLocalFile.parse("spotify:local:Someone+Else:Album:Song:329")!!

        assertNotEquals(prayer.id, other.id)
        assertEquals(prayer.id, SpotifyLocalFile.parse("spotify:local:Kendrick+Lamar:DAMN.:PRAYER.:329")!!.id)
    }

    @Test
    fun encodedCharactersAndEmptyPartsAreHandled() {
        val file = SpotifyLocalFile.parse("spotify:local:::%E3%83%8A%E3%82%A4%E3%83%88+%3A+Live:241")!!

        assertNull(file.artist)
        assertNull(file.album)
        assertEquals("ナイト : Live", file.title)
        assertEquals(241, file.durationSec)
    }

    @Test
    fun aRegularTrackUriIsNotALocalFile() {
        assertNull(SpotifyLocalFile.parse("spotify:track:0cQVqPuHQP4KEwc7ZUQmj6"))
    }
}
