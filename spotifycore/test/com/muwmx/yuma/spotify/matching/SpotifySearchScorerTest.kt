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
    fun `pick prefers the upload credited to the artist over a closer one by a similarly named act`() {
        val query = TrackQuery(artist = "Artist", title = "Song", durationMs = 180_000L)
        val tribute = SpotifyTrack(
            id = "tribute",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist Tribute")),
            durationMs = 180_000,
        )
        val original = SpotifyTrack(
            id = "original",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist - Topic")),
            durationMs = 177_000,
        )

        assertEquals("original", scorer.pick(query, listOf(tribute, original)).accepted?.id)
        assertEquals("original", scorer.pick(query, listOf(original, tribute)).accepted?.id)
    }

    @Test
    fun `token overlap prefers the upload credited to the artist`() {
        val query = TrackQuery(artist = "The Neighbourhood", title = "Sweater Weather", durationMs = 240_000L)
        val lyricVideo = SpotifyTrack(
            id = "lyrics",
            name = "The Neighbourhood - Sweater Weather (Lyrics)",
            artists = listOf(SpotifySimpleArtist(name = "main character music")),
            durationMs = 240_000,
        )
        val official = SpotifyTrack(
            id = "official",
            name = "Sweater Weather",
            artists = listOf(SpotifySimpleArtist(name = "The Neighbourhood - Topic")),
            durationMs = 240_000,
        )

        val picked =
            scorer.pickByTokenOverlap(
                query,
                listOf(lyricVideo, official),
                titleOf = { it.name },
                artistsOf = { it.artists.map { artist -> artist.name } },
                durationMsOf = { it.durationMs.toLong() },
            )
        assertEquals("official", picked?.accepted?.id)
    }

    @Test
    fun `pick rejects a piano version of the song`() {
        val query = TrackQuery(artist = "The Neighbourhood", title = "Sweater Weather", durationMs = 240_000L)
        val piano = SpotifyTrack(
            id = "piano",
            name = "Sweater Weather (Piano Version)",
            artists = listOf(SpotifySimpleArtist(name = "The Neighbourhood")),
            durationMs = 240_000,
        )

        assertNull(scorer.pick(query, listOf(piano)).accepted)
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
            artists = listOf(SpotifySimpleArtist(name = "Artist Tribute")),
            durationMs = 177_000
        )

        val candidate2 = SpotifyTrack(
            id = "2",
            name = "Song",
            artists = listOf(SpotifySimpleArtist(name = "Artist Orchestra")),
            durationMs = 177_000
        )

        val decision = scorer.pick(query, listOf(candidate1, candidate2))
        assertNull("Should abstain on ambiguous candidates", decision.accepted)
        assertEquals("ambiguous", decision.reason)
    }

    @Test
    fun `pick matches an artist written in another script when title and duration agree`() {
        val query = TrackQuery(artist = "Keina Suda", title = "Arcade", durationMs = 200_000L)
        val candidate = SpotifyTrack(
            id = "1",
            name = "Arcade",
            artists = listOf(SpotifySimpleArtist(name = "須田景凪")),
            durationMs = 200_000,
        )

        assertEquals("1", scorer.pick(query, listOf(candidate)).accepted?.id)
    }

    @Test
    fun `pick does not treat a name with Latin letters as a script mismatch`() {
        val query = TrackQuery(artist = "Gateway ゲートウェイ", title = "Arcade", durationMs = 200_000L)
        val candidate = SpotifyTrack(
            id = "1",
            name = "Arcade",
            artists = listOf(SpotifySimpleArtist(name = "Someone Else")),
            durationMs = 200_000,
        )

        assertNull(scorer.pick(query, listOf(candidate)).accepted)
    }

    @Test
    fun `a stored match with the same title and artist is plausible`() {
        assertEquals(
            true,
            scorer.isPlausibleMatch("Sweater Weather", listOf("The Neighbourhood"), 240, "Sweater Weather", listOf("The Neighbourhood - Topic"), 238),
        )
    }

    @Test
    fun `a stored match with a differently written title but the same length is plausible`() {
        assertEquals(true, scorer.isPlausibleMatch("SpeedStar", listOf("TOKYOPILL"), 150, "Speed Star", listOf("TOKYOPILL"), 150))
        assertEquals(true, scorer.isPlausibleMatch("Yoru no Himawari", listOf("Artist"), 200, "夜のひまわり", listOf("Artist"), 201))
    }

    @Test
    fun `a stored match for an unrelated video is not plausible`() {
        assertEquals(
            false,
            scorer.isPlausibleMatch(
                "ナイトクルージング", emptyList(), 241,
                "YEAT - VVV FT. PLAYBOI CARTI (MUSIC VIDEO OFFICIAL)", emptyList(), 165,
            ),
        )
        assertEquals(
            false,
            scorer.isPlausibleMatch("Song", listOf("Artist"), 200, "Another Song Entirely", listOf("Artist"), 90),
        )
    }

    @Test
    fun `a stored match without an artist is judged by its title`() {
        assertEquals(true, scorer.isPlausibleMatch("Divine Failure", listOf("Artist"), 120, "Divine Failure", emptyList(), null))
        assertEquals(
            true,
            scorer.isPlausibleMatch("They Tryna Be Cray", listOf("Playboi Carti"), 170, "Playboi Carti - They Tryna Be Cray [Featuring X] (@someone)", emptyList(), null),
        )
        assertEquals(false, scorer.isPlausibleMatch("Glock", listOf("Playboi Carti"), 130, "JoJo's Bizarre Adventure Opening 6 Full", emptyList(), null))
    }

    @Test
    fun `a stored match for another version of the song is not plausible`() {
        assertEquals(false, scorer.isPlausibleMatch("Song", listOf("Artist"), 200, "Song (Instrumental)", listOf("Artist"), 200))
        assertEquals(false, scorer.isPlausibleMatch("Song", listOf("Artist"), 200, "Artist - Song (sped up)", emptyList(), 160))
        assertEquals(true, scorer.isPlausibleMatch("Song - Instrumental", listOf("Artist"), 200, "Song (Instrumental)", listOf("Artist"), 200))
    }

    @Test
    fun `a karaoke upload is not taken for the original even with the same title and length`() {
        val karaokeTitle = "LOST IN PARADISE (原曲歌手:ALI) - LOST IN PARADISE No Guide melody Original by ALI (feat. AKLO)"
        assertEquals(false, scorer.isPlausibleMatch("LOST IN PARADISE", listOf("ALI", "AKLO"), 327, karaokeTitle, listOf("歌っちゃ王"), 327))

        val query = TrackQuery(artist = "ALI, AKLO", title = "LOST IN PARADISE", durationMs = 327_000L)
        val karaoke = SpotifyTrack(id = "karaoke", name = karaokeTitle, artists = listOf(SpotifySimpleArtist(name = "歌っちゃ王")), durationMs = 327_000)
        val official = SpotifyTrack(
            id = "official",
            name = "LOST IN PARADISE (Jujutsu Kaisen Ending Theme Song) (feat. AKLO)",
            artists = listOf(SpotifySimpleArtist(name = "ALI")),
            durationMs = 328_000,
        )
        val picked =
            scorer.pickByTokenOverlap(
                query,
                listOf(karaoke, official),
                titleOf = { it.name },
                artistsOf = { it.artists.map { artist -> artist.name } },
                durationMsOf = { it.durationMs.toLong() },
            )
        assertEquals("official", picked?.accepted?.id)
    }

    @Test
    fun `a stored match by a different artist is not plausible even with the same title`() {
        assertEquals(false, scorer.isPlausibleMatch("Song", listOf("Artist"), 200, "Song", listOf("Somebody Else"), 200))
    }
}
