package moe.rukamori.archivetune.ui

import android.app.Application
import app.cash.turbine.test
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

    @Test
    fun `test static lyrics resets currentLineIndex`() = runTest {
        viewModel.setLyricsVisible(true)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.uiState.test {
            val initial = awaitItem()
            
            // Emit synced lyrics
            val syncedLyrics = "[00:01.00] Line 1\n[00:02.00] Line 2\n[00:03.00] Line 3\n[00:04.00] Line 4"
            currentLyricsFlow.value = LyricsEntity(id = "1", lyrics = syncedLyrics, source = "REMOTE")
            testDispatcher.scheduler.advanceUntilIdle()
            
            val syncedState = awaitItem()
            assertEquals(true, syncedState.isSynced)
            
            // Simulate progress to line 3 (index 3)
            viewModel.onPlaybackProgress(4, 10)
            testDispatcher.scheduler.advanceUntilIdle()
            
            val progressState1 = awaitItem()
            val progressState2 = awaitItem()
            assertEquals(3, progressState2.currentLineIndex)

            // Emit static lyrics
            val staticLyrics = "Line 1\nLine 2\nLine 3"
            currentLyricsFlow.value = LyricsEntity(id = "2", lyrics = staticLyrics, source = "REMOTE")
            testDispatcher.scheduler.advanceUntilIdle()
            
            val staticState = awaitItem()
            assertEquals(false, staticState.isSynced)
            assertEquals(-1, staticState.currentLineIndex)
        }
    }
}
