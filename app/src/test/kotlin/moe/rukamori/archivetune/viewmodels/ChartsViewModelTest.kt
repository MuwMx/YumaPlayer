package moe.rukamori.archivetune.viewmodels

import io.mockk.coEvery
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
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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
    fun initialState_isLoading() {
        coEvery { YouTube.getChartsPage() } returns Result.success(ChartsPage(emptyList(), null))
        val viewModel = ChartsViewModel()
        assertTrue(viewModel.uiState.value is ChartsUiState.Loading)
    }

    @Test
    fun loadCharts_successWithItems_transitionsToSuccess() = runTest(testDispatcher) {
        val dummySong =
            SongItem(
                id = "song1",
                title = "Test Song",
                artists = emptyList(),
                thumbnail = "https://example.com/thumb.jpg",
            )
        val section =
            ChartsPage.ChartSection(
                title = "Top Songs",
                items = listOf(dummySong),
                chartType = ChartsPage.ChartType.TOP,
            )
        val dummyPage = ChartsPage(sections = listOf(section), continuation = null)
        coEvery { YouTube.getChartsPage() } returns Result.success(dummyPage)

        val viewModel = ChartsViewModel()
        viewModel.loadCharts()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ChartsUiState.Success)
        val success = viewModel.uiState.value as ChartsUiState.Success
        assertEquals(1, success.page.sections.size)
        assertEquals("Top Songs", success.page.sections[0].title)
    }

    @Test
    fun loadCharts_emptySections_transitionsToEmpty() = runTest(testDispatcher) {
        coEvery { YouTube.getChartsPage() } returns Result.success(ChartsPage(emptyList(), null))

        val viewModel = ChartsViewModel()
        viewModel.loadCharts()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ChartsUiState.Empty)
    }

    @Test
    fun loadCharts_errorAndRetry_transitionsErrorThenSuccess() = runTest(testDispatcher) {
        coEvery { YouTube.getChartsPage() } returns Result.failure(RuntimeException("Network error"))

        val viewModel = ChartsViewModel()
        viewModel.loadCharts()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ChartsUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as ChartsUiState.Error).messageResId)

        val dummySong =
            SongItem(
                id = "recovered_song",
                title = "Recovered Song",
                artists = emptyList(),
                thumbnail = "https://example.com/thumb.jpg",
            )
        val section =
            ChartsPage.ChartSection(
                title = "Top Songs",
                items = listOf(dummySong),
                chartType = ChartsPage.ChartType.TOP,
            )
        coEvery { YouTube.getChartsPage() } returns Result.success(ChartsPage(listOf(section), null))

        viewModel.retry()
        assertTrue(viewModel.uiState.value is ChartsUiState.Loading)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ChartsUiState.Success)
    }
}
