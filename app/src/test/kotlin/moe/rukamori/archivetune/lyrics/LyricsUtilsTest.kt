package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUtilsTest {

    @Test
    fun `parseLyrics parses LRC line-synced lyrics`() {
        val lrc = "[00:12.34] Hello world"
        val parsed = LrcParser.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello world", parsed[0].text)
    }

    @Test
    fun `parseLyrics parses YRC word-synced lyrics`() {
        val yrc = "[123,456](0,100)Hello"
        val parsed = LrcParser.parseLyrics(yrc)
        assertEquals(1, parsed.size)
        assertEquals(123L, parsed[0].time)
        assertEquals("Hello", parsed[0].text)
    }

    @Test
    fun `parseTtml parses TTML lyrics`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:00:12.340" end="00:00:15.000">Hello TTML</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val parsed = LrcParser.parseTtml(ttml)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello TTML", parsed[0].text)
    }

    @Test
    fun `parseLyrics merges duplicate timestamps with translations`() {
        val lrc = """
            [00:12.34] Hello world
            [00:12.34] Привет мир
        """.trimIndent()
        val parsed = LrcParser.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello world", parsed[0].text)
        assertEquals("Привет мир", parsed[0].providerTranslationText)
    }

    @Test
    fun `insertInstrumentalBreaks inserts breaks correctly`() {
        val entries = listOf(
            LyricsEntry(time = 10000L, text = "First line"),
            LyricsEntry(time = 20000L, text = "Last line")
        )
        val withBreaks = LrcParser.insertInstrumentalBreaks(entries, 30000L)
        
        // Intro break: 10000 - 1000 = 9000ms (> 5000ms threshold)
        // Outro break: 30000 - (20000 + 2500) = 7500ms (> 5000ms threshold)
        
        assertEquals(4, withBreaks.size)
        
        assertTrue(withBreaks[0].isInstrumental)
        assertEquals(1000L, withBreaks[0].time)
        assertEquals(9000L, withBreaks[0].durationMs)
        
        assertEquals("First line", withBreaks[1].text)
        assertEquals("Last line", withBreaks[2].text)
        
        assertTrue(withBreaks[3].isInstrumental)
        assertEquals(22500L, withBreaks[3].time)
        assertEquals(7500L, withBreaks[3].durationMs)
    }
}
