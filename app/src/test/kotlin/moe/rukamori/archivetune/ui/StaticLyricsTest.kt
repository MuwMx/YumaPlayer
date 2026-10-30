package moe.rukamori.archivetune.ui

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.playback.PlayerConnectionHolder
import moe.rukamori.archivetune.data.repository.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import android.graphics.Color

@OptIn(ExperimentalCoroutinesApi::class)
class StaticLyricsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayerViewModel
    private lateinit var connectionHolder: PlayerConnectionHolder
    private lateinit var connection: PlayerConnection
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var lyricsHelper: LyricsHelper
    private lateinit var application: Application
    private lateinit var database: MusicDatabase

    private val currentLyricsFlow = MutableStateFlow<LyricsEntity?>(null)

    @Before
    fun setup() {
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0
        
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        lyricsHelper = mockk(relaxed = true)
        database = mockk(relaxed = true)

        connectionHolder = PlayerConnectionHolder()
        connectionHolder.connection.value = connection
        every { connection.currentLyrics } returns currentLyricsFlow
        every { connection.database } returns database
        every { connection.mediaMetadata } returns MutableStateFlow(null)
        every { connection.playbackState } returns MutableStateFlow(0)
        every { connection.currentSong } returns MutableStateFlow(null)
        every { connection.isPlaying } returns MutableStateFlow(false)
        every { connection.currentFormat } returns MutableStateFlow(null)
        every { connection.audioFormat } returns MutableStateFlow(null)
        every { connection.shuffleModeEnabled } returns MutableStateFlow(false)
        every { connection.repeatMode } returns MutableStateFlow(0)
        every { connection.queueWindows } returns MutableStateFlow(emptyList())
        every { connection.currentWindowIndex } returns MutableStateFlow(-1)
        every { connection.queueTitle } returns MutableStateFlow(null)
        every { connection.service.sleepTimer.remainingSeconds } returns MutableStateFlow(0L)
        every { settingsRepository.isBlurBackgroundEnabled() } returns false
        every { settingsRepository.isAutoDownloadLyricsEnabled() } returns false
        every { settingsRepository.isImmersiveEnabled() } returns false
        every { settingsRepository.isShowCodecInfoEnabled() } returns false
        every { settingsRepository.isAlbumCoverGlowEnabled() } returns false
        every { settingsRepository.isFirstLaunch() } returns false
        every { settingsRepository.lyricsRomanizationPrefsFlow } returns MutableStateFlow(moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences())

        viewModel = PlayerViewModel(application, connectionHolder, settingsRepository, lyricsHelper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Color::class)
    }

    /** Lyrics are only applied to the track they belong to, so the test moves the player to that track first. */
    private fun setTrack(trackUrl: String) {
        val field = PlayerViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(viewModel) as MutableStateFlow<PlayerUiState>
        state.value = state.value.copy(trackUrl = trackUrl)
    }

    /** The delegate parses lyrics on Dispatchers.Default, so the result arrives from another thread. */
    private fun awaitState(condition: (PlayerUiState) -> Boolean): PlayerUiState {
        repeat(100) {
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value
            if (condition(state)) return state
            Thread.sleep(20)
        }
        error("The player state never satisfied the condition: ${viewModel.uiState.value}")
    }

    @Test
    fun `test static lyrics resets currentLineIndex`() = runTest {
        viewModel.setLyricsVisible(true)
        testDispatcher.scheduler.advanceUntilIdle()

        setTrack("1")
        val syncedLyrics = "[00:01.00] Line 1\n[00:02.00] Line 2\n[00:03.00] Line 3\n[00:04.00] Line 4"
        currentLyricsFlow.value = LyricsEntity(id = "1", lyrics = syncedLyrics, source = "REMOTE")
        assertEquals(true, awaitState { it.lyricsList.size == 4 }.isSynced)

        // Simulate progress to line 3 (index 3)
        viewModel.onPlaybackProgress(4, 10)
        assertEquals(3, awaitState { it.currentLineIndex == 3 }.currentLineIndex)

        // Emit static lyrics for the next track
        setTrack("2")
        currentLyricsFlow.value = LyricsEntity(id = "2", lyrics = "Line 1\nLine 2\nLine 3", source = "REMOTE")
        val staticState = awaitState { it.lyricsList.size == 3 && !it.isSynced }
        assertEquals(-1, staticState.currentLineIndex)
    }
}
