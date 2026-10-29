package moe.rukamori.archivetune.spotify.matching

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenSimilarityTest {
    private val matcher = TrackMatcher()

    @Test
    fun `identical titles are fully similar`() {
        assertEquals(1.0, matcher.tokenSimilarity("Video Games", "Video Games"), 0.0)
    }

    @Test
    fun `a title that starts with a decoration word is not cut`() {
        assertEquals(1.0, matcher.tokenSimilarity("Official髭男dism Pretender", "Official髭男dism Pretender"), 0.0)
        assertEquals(1.0, matcher.tokenSimilarity("Audio Slave", "Audio Slave"), 0.0)
    }

    @Test
    fun `trailing remaster and upload decorations are ignored`() {
        assertEquals(1.0, matcher.tokenSimilarity("Song - 2011 Remaster", "Song"), 0.0)
        assertEquals(1.0, matcher.tokenSimilarity("Song - Official Video", "Song"), 0.0)
        assertEquals(1.0, matcher.tokenSimilarity("Song Official Audio", "Song"), 0.0)
        assertEquals(1.0, matcher.tokenSimilarity("Song (Official Video)", "Song"), 0.0)
    }

    @Test
    fun `featured artists and brackets are ignored`() {
        assertEquals(1.0, matcher.tokenSimilarity("Song (feat. Someone)", "Song"), 0.0)
        assertEquals(1.0, matcher.tokenSimilarity("Song ft. Someone", "Song"), 0.0)
    }

    @Test
    fun `different titles share only some words`() {
        assertEquals(0.5, matcher.tokenSimilarity("Blue Moon", "Blue Sky"), 0.0)
    }
}
