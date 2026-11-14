package moe.rukamori.archivetune.playlist.source

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSourceFactoryTest {
    private val factory =
        PlaylistSourceFactory(
            context = mockk(relaxed = true),
            database = mockk(relaxed = true),
            syncUtils = mockk(relaxed = true),
            downloadUtil = mockk(relaxed = true),
            spotifyRepository = mockk(relaxed = true),
            playerCache = mockk(relaxed = true),
            downloadCache = mockk(relaxed = true),
        )

    @Test
    fun `routes map to the right source type`() {
        assertTrue(factory.sourceFor("local_playlist", "p1") is RoomPlaylistSource)
        assertTrue(factory.sourceFor("online_playlist", "VLp1") is InnerTubePlaylistSource)
        assertTrue(factory.sourceFor("spotify_playlist", "sp1") is SpotifyPlaylistSource)
        assertTrue(factory.sourceFor("auto_playlist", "liked") is AutoPlaylistSource)
        assertTrue(factory.sourceFor("cache_playlist", "all") is CachePlaylistSource)
        assertTrue(factory.sourceFor("top_playlist", "10") is TopPlaylistSource)
    }

    @Test
    fun `unknown routes are rejected loudly`() {
        val result = runCatching { factory.sourceFor("album", "x") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `each source carries its route capabilities`() {
        assertEquals(PlaylistCapabilities.Room, factory.sourceFor("local_playlist", "p").capabilities)
        assertEquals(PlaylistCapabilities.InnerTube, factory.sourceFor("online_playlist", "p").capabilities)
        assertEquals(PlaylistCapabilities.Spotify, factory.sourceFor("spotify_playlist", "p").capabilities)
        assertEquals(PlaylistCapabilities.Auto, factory.sourceFor("auto_playlist", "p").capabilities)
        assertEquals(PlaylistCapabilities.ReadOnly, factory.sourceFor("cache_playlist", "p").capabilities)
        // Top is read-only but downloadable — its legacy hero has a download
        // button (TopPlaylistScreen.kt:334-354).
        assertEquals(PlaylistCapabilities.Top, factory.sourceFor("top_playlist", "p").capabilities)
    }
}
