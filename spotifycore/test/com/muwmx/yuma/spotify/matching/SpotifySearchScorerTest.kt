package moe.rukamori.archivetune.spotify.matching

import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifySearchScorerTest {

    private val matcher = TrackMatcher()
    private val scorer = SpotifySearchScorer(matcher)

    @Test
    fun `pick accepts candidate within 5s duration tolerance`() {
        val query = TrackQuery(
            artist = "Artist",
            title = "Song",
            durationMs = 180_000L
        )

        val candidate = SpotifyTrack(
            id = "1",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist")),
            durationMs = 184_000 // 4s difference
        )

        val decision = scorer.pick(query, listOf(candidate))
        assertNotNull("Candidate should be accepted", decision.accepted)
        assertEquals("1", decision.accepted?.id)
    }

    @Test
    fun `pick rejects candidate exceeding 5s duration tolerance`() {
        val query = TrackQuery(
            artist = "Artist",
            title = "Song",
            durationMs = 180_000L
        )

        val candidate = SpotifyTrack(
            id = "1",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist")),
            durationMs = 186_000 // 6s difference
        )

        val decision = scorer.pick(query, listOf(candidate))
        assertNull("Candidate should be rejected due to >5s difference", decision.accepted)
        assertEquals("no candidate passed gates", decision.reason)
    }

    @Test
    fun `pick rejects candidate with version conflict`() {
        val query = TrackQuery(
            artist = "Artist",
            title = "Song",
            durationMs = 180_000L
        )

        val candidate = SpotifyTrack(
            id = "1",
            name = "Song (Radio Edit)",
            artists = listOf(SpotifySimpleArtist(name = "Artist")),
            durationMs = 180_000
        )

        val decision = scorer.pick(query, listOf(candidate))
        assertNull("Candidate should be rejected due to version conflict", decision.accepted)
    }

    @Test
    fun `pick abstains on ambiguous candidates`() {
        val query = TrackQuery(
            artist = "Artist",
            title = "Song",
            durationMs = 180_000L
        )

        val candidate1 = SpotifyTrack(
            id = "1",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist")),
            durationMs = 177_000
        )

        val candidate2 = SpotifyTrack(
            id = "2",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist Tribute")),
            durationMs = 177_000
        )

        val decision = scorer.pick(query, listOf(candidate1, candidate2))
        assertNull("Should abstain on ambiguous candidates", decision.accepted)
        assertEquals("ambiguous", decision.reason)
    }
}
