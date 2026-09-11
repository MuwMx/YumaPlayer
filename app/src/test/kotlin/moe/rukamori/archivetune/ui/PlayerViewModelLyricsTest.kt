package moe.rukamori.archivetune.ui

import android.app.Application
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import android.graphics.Color
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
import moe.rukamori.archivetune.ui.state.PlayerUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelLyricsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayerViewModel
    private lateinit var lyricsHelper: LyricsHelper
    private lateinit var database: MusicDatabase
    private lateinit var connectionHolder: PlayerConnectionHolder
    private val currentLyricsFlow = MutableStateFlow<LyricsEntity?>(null)
    private val mediaMetadataFlow = MutableStateFlow<moe.rukamori.archivetune.models.MediaMetadata?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0

        val application = mockk<Application>(relaxed = true)
        lyricsHelper = mockk(relaxed = true)
        database = mockk(relaxed = true)
        every { database.query(any()) } answers { firstArg<MusicDatabase.() -> Unit>().invoke(database) }
        every { database.replaceLyrics(any(), any(), any()) } returns Unit
        every { database.replaceLyrics(any(), any(), any(), any()) } returns Unit
        
        val playerConnection = mockk<PlayerConnection>(relaxed = true)
        every { playerConnection.database } returns database
        every { playerConnection.currentLyrics } returns currentLyricsFlow
        every { playerConnection.mediaMetadata } returns mediaMetadataFlow
        
        connectionHolder = PlayerConnectionHolder()
        connectionHolder.connection.value = playerConnection

        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.isBlurBackgroundEnabled() } returns false
        every { settingsRepository.isAutoDownloadLyricsEnabled() } returns false
        every { settingsRepository.isImmersiveEnabled() } returns false
        every { settingsRepository.isShowCodecInfoEnabled() } returns false
        every { settingsRepository.isAlbumCoverGlowEnabled() } returns false
        every { settingsRepository.lyricsRomanizationPrefsFlow } returns MutableStateFlow(moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences())

        viewModel = PlayerViewModel(
            application = application,
            connectionHolder = connectionHolder,
            settingsRepository = settingsRepository,
            lyricsHelper = lyricsHelper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Color::class)
    }

    private fun setUiState(state: PlayerUiState) {
        val field: Field = PlayerViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<PlayerUiState>
        stateFlow.value = state
    }

    @Test
    fun `fetchLyrics with DB hit emits cached lyrics and does not call lyricsHelper`() = runTest {
        // Arrange
        val trackUrl = "test_track_url"
        val title = "Test Title"
        val artist = "Test Artist"
        val cachedLyrics = "[00:01.00] Cached Line"
        val entity = LyricsEntity(
            id = trackUrl,
            lyrics = cachedLyrics,
            source = LyricsEntity.Source.REMOTE.value
        )
        
        setUiState(PlayerUiState(trackUrl = trackUrl, title = title, artist = artist, durationMs = 0L))
        mediaMetadataFlow.value = moe.rukamori.archivetune.models.MediaMetadata(
            id = trackUrl,
            title = title,
            artists = listOf(moe.rukamori.archivetune.models.MediaMetadata.Artist(id = null, name = artist)),
            duration = 0,
        )
        
        coEvery { database.getLyricsById(trackUrl) } returns entity
        currentLyricsFlow.value = entity

        // Act
        viewModel.fetchLyrics()
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.lyricsList.size)
            assertEquals("Cached Line", state.lyricsList[0].text)
            assertEquals(false, state.isLoadingLyrics)
            assertEquals(null, state.lyricsError)
        }
        
        coVerify(exactly = 0) { lyricsHelper.getLyrics(any()) }
    }

    @Test
    fun `fetchLyrics with DB miss calls lyricsHelper`() = runTest {
        // Arrange
        val trackUrl = "test_track_url_2"
        val title = "Test Title 2"
        val artist = "Test Artist 2"
        val remoteLyrics = "[00:02.00] Remote Line"
        val entity = LyricsEntity(
            id = trackUrl,
            lyrics = remoteLyrics,
            source = LyricsEntity.Source.REMOTE.value
        )
        
        setUiState(PlayerUiState(trackUrl = trackUrl, title = title, artist = artist, durationMs = 0L))
        mediaMetadataFlow.value = moe.rukamori.archivetune.models.MediaMetadata(
            id = trackUrl,
            title = title,
            artists = listOf(moe.rukamori.archivetune.models.MediaMetadata.Artist(id = null, name = artist)),
            duration = 0,
        )
        
        coEvery { database.getLyricsById(trackUrl) } returns null
        coEvery { lyricsHelper.getLyrics(any()) } returns remoteLyrics

        // Act
        viewModel.fetchLyrics()
        currentLyricsFlow.value = entity
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.lyricsList.size)
            assertEquals("Remote Line", state.lyricsList[0].text)
            assertEquals(false, state.isLoadingLyrics)
            assertEquals(null, state.lyricsError)
        }
        
        coVerify(exactly = 1) { lyricsHelper.getLyrics(any()) }
    }
}
