package moe.rukamori.archivetune.lrclib.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatchingTest {
    @Test
    fun `bestMatchingFor returns null on empty list`() {
        assertNull(emptyList<Track>().bestMatchingFor(duration = 180))
    }

    @Test
    fun `bestMatchingFor selects closest duration within 2 seconds`() {
        val far = Track(1, "Song", "Artist", 190.0, "plain", null)
        val close = Track(2, "Song", "Artist", 181.0, "plain", "[00:01.00] synced")
        val tracks = listOf(far, close)

        assertEquals(close, tracks.bestMatchingFor(duration = 180))
    }

    @Test
    fun `bestMatchingFor returns null when all tracks exceed 2 second delta`() {
        val tracks = listOf(Track(1, "Song", "Artist", 185.0, "plain", null))
        assertNull(tracks.bestMatchingFor(duration = 180))
    }

    @Test
    fun `bestMatchingFor with unknown duration prefers matching title and artist`() {
        val wrong = Track(1, "Other", "Someone", 200.0, "plain", null)
        val exact = Track(2, "Nightcall", "Kavinsky", 258.0, "plain", "[00:05.00] synced")

        val matched = listOf(wrong, exact).bestMatchingFor(
            duration = -1,
            trackName = "nightcall",
            artistName = "kavinsky",
        )

        assertEquals(exact, matched)
    }
}
