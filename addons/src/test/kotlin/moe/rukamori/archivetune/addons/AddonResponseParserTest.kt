package moe.rukamori.archivetune.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonResponseParserTest {

    @Test
    fun jsString_escapesProperly() {
        val input = "Hello \"World\"\nNew line\tTab \\ Backslash"
        val escaped = AddonResponseParser.jsString(input)
        assertTrue(escaped.startsWith("\"") && escaped.endsWith("\""))
        assertTrue(escaped.contains("\\\""))
        assertTrue(escaped.contains("\\n"))
    }

    @Test
    fun buildContextArg_formatsJsonObject() {
        val settings = mapOf("quality" to "LOSSLESS", "token" to "xyz")
        val result = AddonResponseParser.buildContextArg(settings)
        assertTrue(result.contains("\"settings\""))
        assertTrue(result.contains("\"quality\":{\"value\":\"LOSSLESS\"}"))
        assertTrue(result.contains("\"token\":{\"value\":\"xyz\"}"))
    }

    @Test
    fun parseTracks_parsesStreamUrlAndDuration() {
        val json = """
            {
                "tracks": [
                    {
                        "id": "t1",
                        "title": "Song One",
                        "artist": "Artist One",
                        "duration": 215.5,
                        "streamURL": "https://stream.example.com/t1.mp3"
                    }
                ]
            }
        """.trimIndent()
        val tracks = AddonResponseParser.parseTracks(json)
        assertEquals(1, tracks.size)
        val t = tracks[0]
        assertEquals("t1", t.id)
        assertEquals("Song One", t.title)
        assertEquals(215500L, t.durationMs)
        assertEquals("https://stream.example.com/t1.mp3", t.streamUrl)
    }

    @Test
    fun parseStream_parsesNestedTrackAndInfersCodec() {
        val json = """
            {
                "streamUrl": "https://audio.example.com/stream?id=123",
                "track": {
                    "mimeType": "audio/flac",
                    "audioQuality": "LOSSLESS",
                    "bitrate": 1411200,
                    "sampleRate": 44100,
                    "bitDepth": 16
                }
            }
        """.trimIndent()
        val stream = AddonResponseParser.parseStream(json, "addon.test")
        assertNotNull(stream)
        assertEquals("https://audio.example.com/stream?id=123", stream?.url)
        assertEquals("flac", stream?.codec)
        assertEquals(44100, stream?.sampleRateHz)
        assertEquals(16, stream?.bitsPerSample)
        assertEquals(1411, stream?.bitrateKbps)
        assertEquals("addon.test", stream?.origin)
    }

    @Test
    fun parseStream_handlesRawUrl() {
        val raw = "  https://example.com/direct.flac  "
        val stream = AddonResponseParser.parseStream(raw, "addon.test")
        assertNotNull(stream)
        assertEquals("https://example.com/direct.flac", stream?.url)
        assertEquals("addon.test", stream?.origin)
    }
}
