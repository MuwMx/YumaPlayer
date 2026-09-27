package moe.rukamori.archivetune.ui.state

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.playback.smart.TrackAnalysisResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlayerUiStateTest {

    @Before
    fun setup() {
        mockkStatic(android.graphics.Color::class)
        every { android.graphics.Color.parseColor(any()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test default values`() {
        val state = PlayerUiState()
        assertEquals("Yuma", state.title)
        assertEquals("Playback...", state.artist)
        assertEquals(emptyList<LyricsEntry>(), state.lyricsList)
        assertNull(state.lyricsRomanizationPrefs)
    }

    @Test
    fun `test copy with lyricsList`() {
        val state = PlayerUiState()
        val lyrics = listOf(
            LyricsEntry(time = 1000L, text = "Line 1"),
            LyricsEntry(time = 2000L, text = "Line 2")
        )
        val newState = state.copy(lyricsList = lyrics)
        
        assertEquals(2, newState.lyricsList.size)
        assertEquals("Line 1", newState.lyricsList[0].text)
        assertEquals(1000L, newState.lyricsList[0].time)
    }

    @Test
    fun `test default trackAnalysis is null`() {
        val state = PlayerUiState()
        assertNull(state.trackAnalysis)
    }

    @Test
    fun `test copy with trackAnalysis`() {
        val state = PlayerUiState()
        val analysis = TrackAnalysisResult(
            bpm = 128.0,
            mixInTime = 12.0,
            mixOutTime = 180.0,
            contentEndTime = 195.0
        )
        val newState = state.copy(trackAnalysis = analysis)
        assertNotNull(newState.trackAnalysis)
        assertEquals(128.0, newState.trackAnalysis?.bpm ?: 0.0, 0.001)
        assertEquals(180.0, newState.trackAnalysis?.mixOutTime ?: 0.0, 0.001)
    }
}
