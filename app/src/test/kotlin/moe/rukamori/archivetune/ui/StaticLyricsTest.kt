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
import moe.rukamori.archivetune.lyrics.language.LyricsRomanizationPreferences
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.host.PlayerConnectionHolder
import moe.rukamori.archivetune.ui.state.PlayerUiState
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
    private val mediaMetadataFlow = MutableStateFlow<MediaMetadata?>(null)

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
        every { connection.mediaMetadata } returns mediaMetadataFlow
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
        every { settingsRepository.lyricsRomanizationPrefsFlow } returns MutableStateFlow(moe.rukamori.archivetune.lyrics.language.LyricsRomanizationPreferences())

        viewModel =
            PlayerViewModel(
                application = application,
                connectionHolder = connectionHolder,
                settingsRepository = settingsRepository,
                lyricsHelper = lyricsHelper,
                ioDispatcher = testDispatcher,
                defaultDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Color::class)
    }

    private fun playTrack(id: String) {
        mediaMetadataFlow.value = MediaMetadata(id = id, title = id, artists = emptyList(), duration = 10)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun showLyrics(entity: LyricsEntity): PlayerUiState {
        currentLyricsFlow.value = entity
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value
    }

    @Test
    fun `test static lyrics resets currentLineIndex`() = runTest {
        viewModel.setLyricsVisible(true)
        testDispatcher.scheduler.advanceUntilIdle()

        playTrack("1")
        val syncedLyrics = "[00:01.00] Line 1\n[00:02.00] Line 2\n[00:03.00] Line 3\n[00:04.00] Line 4"
        val syncedState = showLyrics(LyricsEntity(id = "1", lyrics = syncedLyrics, source = "REMOTE"))
        assertEquals(4, syncedState.lyricsList.size)
        assertEquals(true, syncedState.isSynced)

        viewModel.onPlaybackProgress(4, 10)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.currentLineIndex)

        playTrack("2")
        val staticState = showLyrics(LyricsEntity(id = "2", lyrics = "Line 1\nLine 2\nLine 3", source = "REMOTE"))
        assertEquals(3, staticState.lyricsList.size)
        assertEquals(false, staticState.isSynced)
        assertEquals(-1, staticState.currentLineIndex)
    }
}
