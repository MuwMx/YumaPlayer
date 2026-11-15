package moe.rukamori.archivetune.ui.screens.playlist.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heart-resolution rules for unified playlist rows: union like state
 * for online rows, Spotify-side (likedSpotify) state for Spotify rows —
 * where the write paths use several id shapes (resolved YouTube id,
 * "spotify:<raw>" placeholder, match-table bridge).
 */
class PlaylistBadgeStateHolderTest {
    private fun holder(
        liked: Set<String> = emptySet(),
        inLibrary: Set<String> = emptySet(),
        spotifyLiked: Set<String> = emptySet(),
        matches: Map<String, String> = emptyMap(),
    ) = PlaylistBadgeStateHolder(liked, inLibrary, spotifyLiked, matches)

    @Test
    fun `union liked and in-library membership key on the song id`() {
        val h = holder(liked = setOf("a"), inLibrary = setOf("b"))
        assertTrue(h.isLiked("a"))
        assertFalse(h.isLiked("b"))
        assertTrue(h.isInLibrary("b"))
        assertFalse(h.isInLibrary("a"))
    }

    @Test
    fun `spotify heart shows for a placeholder-id like row`() {
        // A menu like on a placeholder row persists under "spotify:<raw>".
        val h = holder(spotifyLiked = setOf("spotify:raw1"))
        assertTrue(h.isSpotifyLiked("raw1"))
    }

    @Test
    fun `spotify heart shows for a spotify-track-prefixed like row`() {
        val h = holder(spotifyLiked = setOf("spotify:track:raw1"))
        assertTrue(h.isSpotifyLiked("raw1"))
    }

    @Test
    fun `spotify heart bridges the raw id to a liked resolved youtube id`() {
        // SpotifyLikedSync persists likedSpotify under the RESOLVED YouTube
        // id with a spotify_match row pointing at it.
        val h = holder(
            spotifyLiked = setOf("ytVideo1"),
            matches = mapOf("raw1" to "ytVideo1"),
        )
        assertTrue(h.isSpotifyLiked("raw1"))
    }

    @Test
    fun `spotify heart bridges prefixed match keys too`() {
        // SpotifyPlaybackResolver's matchKey and MusicDatabase.update use
        // the raw id, but tolerate the prefixed shape as well.
        val h = holder(
            spotifyLiked = setOf("ytVideo1"),
            matches = mapOf("spotify:track:raw1" to "ytVideo1"),
        )
        assertTrue(h.isSpotifyLiked("raw1"))
    }

    @Test
    fun `no heart when the matched youtube song is not spotify-liked`() {
        // A match alone must NOT imply a heart: playback resolution persists
        // matches for every played track.
        val h = holder(matches = mapOf("raw1" to "ytVideo1"))
        assertFalse(h.isSpotifyLiked("raw1"))
    }

    @Test
    fun `no heart without any like or match`() {
        val h = holder()
        assertFalse(h.isSpotifyLiked("raw1"))
        assertFalse(h.isSpotifyLiked(""))
    }

    @Test
    fun `live updates flow through the internal setters`() {
        val h = holder()
        assertFalse(h.isLiked("a"))
        h.likedIds = setOf("a")
        assertTrue(h.isLiked("a"))
        h.spotifyLikedIds = setOf("spotify:raw1")
        h.matchBySpotifyId = mapOf("raw1" to "yt1")
        assertTrue(h.isSpotifyLiked("raw1"))
    }
}
