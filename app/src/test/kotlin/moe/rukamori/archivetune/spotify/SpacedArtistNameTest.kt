package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.matching.SpotifySearchScorer
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import moe.rukamori.archivetune.spotify.matching.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedArtistNameTest {
    @Test
    fun letterSpacedNamesAreCollapsed() {
        assertEquals("Gateway ゲートウェイ", collapseSpacedLetters("G a t e w a y ゲートウェイ"))
        assertEquals("telepath テレパシー能力者", collapseSpacedLetters("t e l e p a t h テレパシー能力者"))
        assertEquals("Frank Ocean", collapseSpacedLetters("Frank Ocean"))
        assertEquals("A B", collapseSpacedLetters("A B"))
    }

    private data class Candidate(val title: String, val artists: List<String>, val durationMs: Long)

    @Test
    fun mixedScriptArtistDoesNotMatchAnotherArtistsSameTitle() {
        // Regression: Gateway's "忘れない" was matched to "忘れない" by Release
        // because any name containing Japanese counted as a script mismatch.
        val scorer = SpotifySearchScorer(TrackMatcher())
        val track =
            TrackQuery(
                artist = collapseSpacedLetters("G a t e w a y ゲートウェイ"),
                title = "忘れない",
                durationMs = 200_000,
            )
        val wrong = Candidate("忘れない", listOf("Release"), 201_000)
        val strict =
            scorer.pickGeneric(track, listOf(wrong), { it.title }, { it.artists }, { it.durationMs })
        val fallback =
            scorer.pickByTokenOverlap(track, listOf(wrong), { it.title }, { it.artists }, { it.durationMs })
        assertEquals(null, strict.accepted)
        assertEquals(null, fallback?.accepted)
    }

    @Test
    fun japaneseOnlyArtistStillMatchesItsRomanisedName() {
        val scorer = SpotifySearchScorer(TrackMatcher())
        assertTrue(
            scorer.hasArtistEvidence(
                trackArtists = listOf("須田景凪"),
                candidateTitle = "veil",
                candidateArtists = listOf("Keina Suda"),
            ),
        )
    }

    @Test
    fun collapsedNameCountsAsArtistEvidence() {
        val scorer = SpotifySearchScorer(TrackMatcher())
        assertTrue(
            scorer.hasArtistEvidence(
                trackArtists = listOf(collapseSpacedLetters("G a t e w a y ゲートウェイ")),
                candidateTitle = "恋人の動き",
                candidateArtists = listOf("Gateway"),
            ),
        )
    }
}
