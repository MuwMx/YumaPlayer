package moe.rukamori.archivetune.spotify.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TokenOverlapPickTest {
    private val scorer = SpotifySearchScorer(TrackMatcher())

    private data class Candidate(
        val title: String,
        val artists: List<String>,
        val durationMs: Long,
    )

    private val track =
        TrackQuery(
            artist = "Artist",
            title = "Song",
            durationMs = 200_000,
        )

    @Test
    fun `token overlap pick rejects a same-titled song by a different artist`() {
        // Regression: "Godspeed" by Frank Ocean resolved to someone else's
        // "Godspeed" because the title-only search accepted any artist.
        val godspeed =
            TrackQuery(
                artist = "Frank Ocean",
                title = "Godspeed",
                durationMs = 177_000,
            )
        val wrong =
            Candidate(
                title = "Godspeed",
                artists = listOf("James Blake"),
                durationMs = 178_000,
            )
        val right =
            Candidate(
                title = "Frank Ocean - Godspeed",
                artists = listOf("blonded"),
                durationMs = 178_000,
            )

        val onlyWrong =
            scorer.pickByTokenOverlap(
                track = godspeed,
                candidates = listOf(wrong),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNull(onlyWrong?.accepted)

        val withRight =
            scorer.pickByTokenOverlap(
                track = godspeed,
                candidates = listOf(wrong, right),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertEquals(right, withRight?.accepted)
    }

    @Test
    fun `token overlap pick accepts a close-duration uploader variant when strict gates reject`() {
        // Uploaded by a channel that is not the artist, but the title names
        // the artist, so it is the same song.
        val candidate =
            Candidate(
                title = "Artist - Song",
                artists = listOf("Some Uploader"),
                durationMs = 201_000,
            )

        val strict =
            scorer.pickGeneric(
                track = track,
                candidates = listOf(candidate),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNull(strict.accepted)

        val fallback =
            scorer.pickByTokenOverlap(
                track = track,
                candidates = listOf(candidate),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNotNull(fallback?.accepted)
        assertEquals("Artist - Song", fallback?.accepted?.title)
    }

    @Test
    fun `token overlap pick still rejects version uploads when the source is the original`() {
        val candidate =
            Candidate(
                title = "Song (Sped Up)",
                artists = listOf("Artist"),
                durationMs = 200_000,
            )

        val fallback =
            scorer.pickByTokenOverlap(
                track = track,
                candidates = listOf(candidate),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNull(fallback?.accepted)
    }

    @Test
    fun `token overlap pick does not accept a different song just because the artist matches`() {
        val candidate =
            Candidate(
                title = "Magnolia",
                artists = listOf("Playboi Carti"),
                durationMs = 200_000,
            )
        val track =
            TrackQuery(
                artist = "Playboi Carti",
                title = "Immortal",
                durationMs = 200_000,
            )

        val fallback =
            scorer.pickByTokenOverlap(
                track = track,
                candidates = listOf(candidate),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNull(fallback?.accepted)
    }

    @Test
    fun `token overlap pick does not accept an acoustic version for the studio original`() {
        val candidate =
            Candidate(
                title = "Cherry Waves (Acoustic)",
                artists = listOf("Deftones"),
                durationMs = 200_000,
            )
        val track =
            TrackQuery(
                artist = "Deftones",
                title = "Cherry Waves",
                durationMs = 200_000,
            )

        val fallback =
            scorer.pickByTokenOverlap(
                track = track,
                candidates = listOf(candidate),
                titleOf = { it.title },
                artistsOf = { it.artists },
                durationMsOf = { it.durationMs },
            )
        assertNull(fallback?.accepted)
    }
}
