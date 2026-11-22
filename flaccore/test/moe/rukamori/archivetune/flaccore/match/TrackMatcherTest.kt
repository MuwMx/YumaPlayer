/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.match

import moe.rukamori.archivetune.flaccore.model.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun `isrc match yields perfect score 1_0`() {
        val query = TrackQuery(artist = "Taylor Swift", title = "Cardigan", isrc = "USUG12002345")
        val candidate = CatalogTrack(id = "1", title = "Different Title", artist = "Unknown", isrc = "USUG12002345")
        val ranked = TrackMatcher.ranked(query, listOf(candidate))
        assertEquals(1, ranked.size)
        assertEquals(1.0f, ranked[0].score, 0.001f)
    }

    @Test
    fun `version words mismatch incurs penalty`() {
        val query = TrackQuery(artist = "Daft Punk", title = "Get Lucky", durationMs = 248000)
        val origCandidate = CatalogTrack(id = "1", title = "Get Lucky", artist = "Daft Punk", durationMs = 248000)
        val remixCandidate = CatalogTrack(id = "2", title = "Get Lucky (Remix)", artist = "Daft Punk", durationMs = 248000)

        val scoreOrig = TrackMatcher.score(query, origCandidate)
        val scoreRemix = TrackMatcher.score(query, remixCandidate)
        assertTrue(scoreOrig > scoreRemix)
        assertTrue(scoreOrig - scoreRemix >= 0.25f)
    }

    @Test
    fun `ranked filters out low confidence and sorts descending`() {
        val query = TrackQuery(artist = "The Weeknd", title = "Blinding Lights", durationMs = 200000)
        val good = CatalogTrack(id = "1", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200000)
        val slightlyOffDuration = CatalogTrack(id = "2", title = "Blinding Lights", artist = "The Weeknd", durationMs = 205000)
        val bad = CatalogTrack(id = "3", title = "Completely Other Song", artist = "Another Artist", durationMs = 120000)

        val ranked = TrackMatcher.ranked(query, listOf(bad, slightlyOffDuration, good))
        assertEquals(2, ranked.size)
        assertEquals("1", ranked[0].track.id)
        assertEquals("2", ranked[1].track.id)
        assertTrue(ranked[0].score >= ranked[1].score)
    }

    @Test
    fun `core strips common decorations`() {
        val raw = "Song Title (feat. Drake) - Remastered 2020"
        val core = TrackMatcher.core(raw)
        assertEquals("song title", core)
    }
}
