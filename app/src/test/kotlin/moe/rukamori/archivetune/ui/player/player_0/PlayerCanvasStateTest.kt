package moe.rukamori.archivetune.ui.player.player_0

import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCanvasStateTest {

    @Test
    fun primaryAndFallbackUrlsSelectedCorrectlyWhenBothDistinct() {
        val artwork = CanvasArtwork(
            name = "Test Track",
            artist = "Test Artist",
            animated = "https://canvas.test/master.m3u8",
            videoUrl = "https://canvas.test/video.mp4",
        )
        val state = PlayerCanvasState(isCanvasEnabled = true, artwork = artwork)

        assertEquals("https://canvas.test/master.m3u8", state.primaryUrl)
        assertEquals("https://canvas.test/video.mp4", state.fallbackUrl)
    }

    @Test
    fun fallbackUrlIsNullWhenVideoUrlMatchesPreferredAnimationUrl() {
        val artwork = CanvasArtwork(
            name = "Test Track",
            artist = "Test Artist",
            animated = null,
            videoUrl = "https://canvas.test/video.mp4",
        )
        val state = PlayerCanvasState(isCanvasEnabled = true, artwork = artwork)

        assertEquals("https://canvas.test/video.mp4", state.primaryUrl)
        assertNull(state.fallbackUrl)
    }

    @Test
    fun urlsAreNullWhenArtworkIsNull() {
        val state = PlayerCanvasState(isCanvasEnabled = true, artwork = null)

        assertNull(state.primaryUrl)
        assertNull(state.fallbackUrl)
    }

    @Test
    fun stateResetClearsUrls() {
        val artwork = CanvasArtwork(
            name = "Test Track",
            artist = "Test Artist",
            animated = "https://canvas.test/master.m3u8",
            videoUrl = "https://canvas.test/video.mp4",
        )
        val state = PlayerCanvasState(isCanvasEnabled = true, artwork = artwork)
        assertEquals("https://canvas.test/master.m3u8", state.primaryUrl)

        state.artwork = null
        assertNull(state.primaryUrl)
        assertNull(state.fallbackUrl)
    }

    @Test
    fun hasCanvasMetadataValidatesPresence() {
        assertTrue(hasCanvasMetadata("Track Name", "Artist Name"))
        assertTrue(hasCanvasMetadata("Track Name", null))
        assertTrue(hasCanvasMetadata("Track Name", "   "))
        assertTrue(hasCanvasMetadata(null, "Artist Name"))
        assertTrue(hasCanvasMetadata("   ", "Artist Name"))

        assertFalse(hasCanvasMetadata(null, null))
        assertFalse(hasCanvasMetadata("", ""))
        assertFalse(hasCanvasMetadata("   ", "   "))
        assertFalse(hasCanvasMetadata(null, "   "))
        assertFalse(hasCanvasMetadata("   ", null))
    }
}
