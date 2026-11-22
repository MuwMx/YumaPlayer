package moe.rukamori.archivetune.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPageTargetsTest {

    @Test
    fun `isYouTubeArtistId validates channel prefixes and excludes placeholders`() {
        assertTrue(SpotifyPageTargets.isYouTubeArtistId("UC1234567890abcdef"))
        assertTrue(SpotifyPageTargets.isYouTubeArtistId("UC_xyz-123"))
        assertFalse(SpotifyPageTargets.isYouTubeArtistId(null))
        assertFalse(SpotifyPageTargets.isYouTubeArtistId(""))
        assertFalse(SpotifyPageTargets.isYouTubeArtistId("4Z8W4fKeB5Yx6u27549"))
        assertFalse(SpotifyPageTargets.isYouTubeArtistId("spotify:artist:4Z8W4fKeB5Yx6u27549"))
        assertFalse(SpotifyPageTargets.isYouTubeArtistId("UC12345:spotify"))
    }

    @Test
    fun `isYouTubeAlbumId validates MPREb and OLAK5uy browse prefixes`() {
        assertTrue(SpotifyPageTargets.isYouTubeAlbumId("MPREb_abcdef12345"))
        assertTrue(SpotifyPageTargets.isYouTubeAlbumId("OLAK5uy_987654321"))
        assertFalse(SpotifyPageTargets.isYouTubeAlbumId(null))
        assertFalse(SpotifyPageTargets.isYouTubeAlbumId(""))
        assertFalse(SpotifyPageTargets.isYouTubeAlbumId("6PFPvvyWwhZsSpnMFGBxuk"))
        assertFalse(SpotifyPageTargets.isYouTubeAlbumId("spotify:album:6PFPvvyWwhZsSpnMFGBxuk"))
        assertFalse(SpotifyPageTargets.isYouTubeAlbumId("MPREb_abc:spotify"))
    }

    @Test
    fun `normalizeArtistName strips YouTube channel suffixes`() {
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen - Topic"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen Topic"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen VEVO"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen Official"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen Official Artist Channel"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen Music"))
        assertEquals("queen", SpotifyPageTargets.normalizeArtistName("Queen"))
        assertEquals("music", SpotifyPageTargets.normalizeArtistName("Music"))
    }

    @Test
    fun `sameArtist matches across YouTube channel suffix differences`() {
        assertTrue(SpotifyPageTargets.sameArtist("Queen - Topic", "Queen"))
        assertTrue(SpotifyPageTargets.sameArtist("Radiohead Official", "Radiohead"))
        assertTrue(SpotifyPageTargets.sameArtist("Pink Floyd VEVO", "Pink Floyd"))
        assertFalse(SpotifyPageTargets.sameArtist("Queen", "King"))
    }

    @Test
    fun `normalizeAlbumName strips edition and parenthetical markers`() {
        assertEquals("abbeyroad", SpotifyPageTargets.normalizeAlbumName("Abbey Road (Remastered)"))
        assertEquals("abbeyroad", SpotifyPageTargets.normalizeAlbumName("Abbey Road (Deluxe Edition)"))
        assertEquals("abbeyroad", SpotifyPageTargets.normalizeAlbumName("Abbey Road [2019 Mix]"))
        assertEquals("thriller", SpotifyPageTargets.normalizeAlbumName("Thriller - Single"))
        assertEquals("discovery", SpotifyPageTargets.normalizeAlbumName("Discovery - EP"))
        assertEquals("nevermind", SpotifyPageTargets.normalizeAlbumName("Nevermind (30th Anniversary Super Deluxe)"))
        assertEquals("albumtitle", SpotifyPageTargets.normalizeAlbumName("Album Title (Bonus Track Version)"))
        assertEquals("albumtitle", SpotifyPageTargets.normalizeAlbumName("Album Title (Explicit)"))
    }

    @Test
    fun `sameAlbum matches equivalent albums with edition variations`() {
        assertTrue(SpotifyPageTargets.sameAlbum("Abbey Road (Remastered)", "Abbey Road"))
        assertTrue(SpotifyPageTargets.sameAlbum("Thriller", "Thriller - Single"))
        assertTrue(SpotifyPageTargets.sameAlbum("Discovery - EP", "Discovery"))
        assertTrue(SpotifyPageTargets.sameAlbum("Album (Deluxe Edition)", "Album"))
        assertFalse(SpotifyPageTargets.sameAlbum("Abbey Road", "Let It Be"))
    }

    @Test
    fun `isCleanTitleMatch matches prefix and normalized base title`() {
        assertTrue(SpotifyPageTargets.isCleanTitleMatch("Abbey Road (Super Deluxe Edition)", "Abbey Road"))
        assertTrue(SpotifyPageTargets.isCleanTitleMatch("Nevermind", "Nevermind - 30th Anniversary Edition"))
        assertFalse(SpotifyPageTargets.isCleanTitleMatch("OK Computer", "Kid A"))
        assertFalse(SpotifyPageTargets.isCleanTitleMatch("A", "Abbey Road"))
    }
}
