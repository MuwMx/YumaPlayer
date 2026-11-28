package moe.rukamori.archivetune.moriextractor

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendExtractorResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes snake_case fields and prioritizes streamUrl over directUrl and audioUrl`() {
        val payload =
            """
            {
              "success": true,
              "title": "Test Track",
              "stream_url": "  https://proxy.example/stream  ",
              "direct_url": "https://direct.example/stream",
              "audio_url": "https://audio.example/stream",
              "audio_headers": {"User-Agent": "YumaTest"}
            }
            """.trimIndent()

        val parsed = json.decodeFromString<BackendExtractorResponse>(payload)
        assertTrue(parsed.success)
        assertEquals("https://proxy.example/stream", parsed.proxyPlayableUrl)
        assertEquals("https://proxy.example/stream", parsed.playableUrl)
        assertEquals("YumaTest", parsed.audioHeaders?.get("User-Agent"))
    }

    @Test
    fun `falls back to directUrl then audioUrl when streamUrl is blank`() {
        val withDirect = BackendExtractorResponse(
            success = true,
            streamUrl = "   ",
            directUrl = "https://direct.example/audio",
            audioUrl = "https://audio.example/audio",
        )
        assertEquals("https://direct.example/audio", withDirect.playableUrl)

        val withAudioOnly = BackendExtractorResponse(
            success = true,
            streamUrl = null,
            directUrl = "",
            audioUrl = " https://audio.example/only ",
        )
        assertNull(withAudioOnly.proxyPlayableUrl)
        assertEquals("https://audio.example/only", withAudioOnly.playableUrl)
    }
}
