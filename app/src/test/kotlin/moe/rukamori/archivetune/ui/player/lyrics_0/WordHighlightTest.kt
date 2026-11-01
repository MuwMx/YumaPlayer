package moe.rukamori.archivetune.ui.player.lyrics_0

import org.junit.Test
import org.junit.Assert.*
import moe.rukamori.archivetune.lyrics.model.WordTimestamp

class WordHighlightTest {
    @Test
    fun testWordHighlightProgress() {
        val word = WordTimestamp("test", 1.0, 2.0)
        val wordStartMs = (word.startTime * 1000).toLong()
        val wordEndMs = (word.endTime * 1000).toLong()
        val wordDuration = wordEndMs - wordStartMs
        
        var currentMs = 500L
        var progress = when {
            currentMs >= wordEndMs -> 1f
            currentMs <= wordStartMs -> 0f
            else -> ((currentMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }
        assertEquals(0f, progress)
        
        currentMs = 1500L
        progress = when {
            currentMs >= wordEndMs -> 1f
            currentMs <= wordStartMs -> 0f
            else -> ((currentMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }
        assertEquals(0.5f, progress)
        
        currentMs = 2500L
        progress = when {
            currentMs >= wordEndMs -> 1f
            currentMs <= wordStartMs -> 0f
            else -> ((currentMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        }
        assertEquals(1f, progress)
    }
}
