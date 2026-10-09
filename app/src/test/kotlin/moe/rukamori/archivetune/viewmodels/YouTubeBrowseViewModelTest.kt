package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeBrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val database: MusicDatabase = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(YouTube)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun init_withNullBrowseId_emitsErrorWithoutCrashing() {
        val savedStateHandle = SavedStateHandle(mapOf("browseId" to null))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
    }

    @Test
    fun init_withBlankBrowseId_emitsErrorWithoutCrashing() {
        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "   "))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
    }

    @Test
    fun browse_successWithItems_transitionsToSuccess() = runTest(testDispatcher) {
        val dummySong =
            SongItem(
                id = "song_b1",
                title = "Browse Song",
                artists = emptyList(),
                thumbnail = "https://example.com/thumb.jpg",
            )
        val dummyResult =
            BrowseResult(
                title = "Explore Charts",
                thumbnail = null,
                items = listOf(BrowseResult.Item(title = "Tracks", items = listOf(dummySong))),
            )
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
        val success = viewModel.uiState.value as YouTubeBrowseUiState.Success
        assertEquals("Explore Charts", success.result.title)
        assertEquals(1, success.result.items.size)
    }

    @Test
    fun browse_emptyItems_transitionsToEmpty() = runTest(testDispatcher) {
        val dummyResult =
            BrowseResult(
                title = "Empty Explore",
                thumbnail = null,
                items = emptyList(),
            )
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Empty)
    }

    @Test
    fun browse_errorAndRetry_transitionsErrorThenSuccess() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.failure(RuntimeException("Network failure"))

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)

        val dummySong =
            SongItem(
                id = "song_retry",
                title = "Retry Song",
                artists = emptyList(),
                thumbnail = "https://example.com/thumb.jpg",
            )
        val recoveredResult =
            BrowseResult(
                title = "Recovered Explore",
                thumbnail = null,
                items = listOf(BrowseResult.Item(title = "Tracks", items = listOf(dummySong))),
            )
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(recoveredResult)

        viewModel.retry()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Loading)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
    }

    @Test
    fun browse_postFilterException_doesNotStrandLoading() = runTest(testDispatcher) {
        val dummyResult =
            BrowseResult(
                title = "Crash PostFilter",
                thumbnail = null,
                items = emptyList(),
            )
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(dummyResult)
        coEvery { database.getBlockedArtistIds() } throws IllegalStateException("Database query failed")

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = YouTubeBrowseViewModel(context, database, savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
    }
}
