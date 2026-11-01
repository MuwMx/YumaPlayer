package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.ui.screens.browse.BrowseViewModel
import moe.rukamori.archivetune.ui.screens.browse.YouTubeBrowseUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class BrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dummySong =
        SongItem(
            id = "song_b1",
            title = "Browse Song",
            artists = emptyList(),
            thumbnail = "https://example.com/thumb.jpg",
        )

    private val dummyResult =
        BrowseResult(
            title = "Explore Charts",
            thumbnail = null,
            items = listOf(BrowseResult.Item(title = "Tracks", items = listOf(dummySong))),
        )

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
    fun init_requestSuspended_emitsLoading() = runTest(testDispatcher) {
        val deferred = CompletableDeferred<Result<BrowseResult>>()
        coEvery { YouTube.browse("FEmusic_charts", null) } coAnswers { deferred.await() }

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        runCurrent()
        coVerify(exactly = 1) { YouTube.browse("FEmusic_charts", null) }
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Loading)

        deferred.complete(Result.success(dummyResult))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
    }

    @Test
    fun init_successWithItems_transitionsToSuccessWithRawResult() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FEmusic_charts", null) }
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
        val success = viewModel.uiState.value as YouTubeBrowseUiState.Success
        assertEquals("Explore Charts", success.result.title)
        assertEquals(dummyResult, success.result)
    }

    @Test
    fun init_emptyResult_transitionsToEmpty() = runTest(testDispatcher) {
        val emptyResult = BrowseResult(title = "Empty", thumbnail = null, items = emptyList())
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(emptyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Empty)
    }

    @Test
    fun init_nestedSectionsEmpty_transitionsToEmpty() = runTest(testDispatcher) {
        val emptySectionsResult = BrowseResult(
            title = "Empty Sections",
            thumbnail = null,
            items = listOf(
                BrowseResult.Item(title = "Section 1", items = emptyList()),
                BrowseResult.Item(title = "Section 2", items = emptyList()),
            ),
        )
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.success(emptySectionsResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Empty)
    }

    @Test
    fun init_resultFailure_emitsError() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FEmusic_charts", null) } returns Result.failure(RuntimeException("Network failure"))

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
    }

    @Test
    fun init_thrownBoundaryException_emitsError() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FEmusic_charts", null) } throws IllegalStateException("Crash in boundary")

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
    }

    @Test
    fun init_missingBrowseId_emitsErrorWithoutCallingApi() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(emptyMap())
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
        coVerify(exactly = 0) { YouTube.browse(any(), any()) }
    }

    @Test
    fun init_blankBrowseId_emitsErrorWithoutCallingApi() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "   "))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)
        assertEquals(R.string.error_unknown, (viewModel.uiState.value as YouTubeBrowseUiState.Error).messageResId)
        coVerify(exactly = 0) { YouTube.browse(any(), any()) }
    }

    @Test
    fun retry_afterError_transitionsErrorLoadingSuccessWithOriginalParams() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FEmusic_charts", "param_val") } returns Result.failure(RuntimeException("First fail"))

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts", "params" to "param_val"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Error)

        coEvery { YouTube.browse("FEmusic_charts", "param_val") } returns Result.success(dummyResult)
        viewModel.retry()

        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Loading)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
        coVerify(exactly = 2) { YouTube.browse("FEmusic_charts", "param_val") }
    }

    @Test
    fun retry_cancelsPreviousInFlightRequest_doesNotCorruptState() = runTest(testDispatcher) {
        val deferred1 = CompletableDeferred<Result<BrowseResult>>()
        val deferred2 = CompletableDeferred<Result<BrowseResult>>()
        var requestCount = 0

        coEvery { YouTube.browse("FEmusic_charts", null) } coAnswers {
            if (requestCount++ == 0) deferred1.await() else deferred2.await()
        }

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        runCurrent()
        coVerify(exactly = 1) { YouTube.browse("FEmusic_charts", null) }
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Loading)

        viewModel.retry()
        runCurrent()
        coVerify(exactly = 2) { YouTube.browse("FEmusic_charts", null) }
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Loading)

        deferred2.complete(Result.success(dummyResult))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)

        val staleResult = BrowseResult(title = "Stale", thumbnail = null, items = emptyList())
        deferred1.complete(Result.success(staleResult))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
        val success = viewModel.uiState.value as YouTubeBrowseUiState.Success
        assertEquals("Explore Charts", success.result.title)
    }

    @Test
    fun retry_cancelledNonCooperativeOldRequestThrowing_doesNotOverwriteNewerSuccess() = runTest(testDispatcher) {
        val deferred1 = CompletableDeferred<Unit>()
        val deferred2 = CompletableDeferred<Result<BrowseResult>>()
        var oldRequestThrew = false
        var requestCount = 0

        coEvery { YouTube.browse("FEmusic_charts", null) } coAnswers {
            if (requestCount++ == 0) {
                withContext(NonCancellable) {
                    deferred1.await()
                    oldRequestThrew = true
                    throw IllegalStateException("Non-cooperative crash from stale request")
                }
            } else {
                deferred2.await()
            }
        }

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FEmusic_charts"))
        val viewModel = BrowseViewModel(savedStateHandle)

        runCurrent()
        coVerify(exactly = 1) { YouTube.browse("FEmusic_charts", null) }

        viewModel.retry()
        runCurrent()
        coVerify(exactly = 2) { YouTube.browse("FEmusic_charts", null) }

        deferred2.complete(Result.success(dummyResult))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)

        deferred1.complete(Unit)
        advanceUntilIdle()

        assertTrue(oldRequestThrew)
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
        val success = viewModel.uiState.value as YouTubeBrowseUiState.Success
        assertEquals("Explore Charts", success.result.title)
    }

    @Test
    fun init_separateViewModels_isolatedEndpointsAndArgs() = runTest(testDispatcher) {
        val result1 = BrowseResult(title = "Title 1", thumbnail = null, items = listOf(BrowseResult.Item("1", listOf(dummySong))))
        val result2 = BrowseResult(title = "Title 2", thumbnail = null, items = listOf(BrowseResult.Item("2", listOf(dummySong))))

        coEvery { YouTube.browse("FE_1", "params_1") } returns Result.success(result1)
        coEvery { YouTube.browse("FE_2", "params_2") } returns Result.success(result2)

        val vm1 = BrowseViewModel(SavedStateHandle(mapOf("browseId" to "FE_1", "params" to "params_1")))
        val vm2 = BrowseViewModel(SavedStateHandle(mapOf("browseId" to "FE_2", "params" to "params_2")))

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FE_1", "params_1") }
        coVerify(exactly = 1) { YouTube.browse("FE_2", "params_2") }

        assertEquals("Title 1", (vm1.uiState.value as YouTubeBrowseUiState.Success).result.title)
        assertEquals("Title 2", (vm2.uiState.value as YouTubeBrowseUiState.Success).result.title)
    }

    @Test
    fun init_legacyNoParams_passesNullParams() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FE_legacy", null) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FE_legacy"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FE_legacy", null) }
        assertTrue(viewModel.uiState.value is YouTubeBrowseUiState.Success)
    }

    @Test
    fun init_blankParams_passesNullParams() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FE_blank", null) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FE_blank", "params" to "   "))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FE_blank", null) }
    }

    @Test
    fun init_literalNullStringParams_preservesValue() = runTest(testDispatcher) {
        coEvery { YouTube.browse("FE_null_str", "null") } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FE_null_str", "params" to "null"))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FE_null_str", "null") }
    }

    @Test
    fun init_opaquePercentAndPlusParams_passedExactly() = runTest(testDispatcher) {
        val opaque = "%2B123%2F456%25+foo"
        coEvery { YouTube.browse("FE_opaque", opaque) } returns Result.success(dummyResult)

        val savedStateHandle = SavedStateHandle(mapOf("browseId" to "FE_opaque", "params" to opaque))
        val viewModel = BrowseViewModel(savedStateHandle)

        advanceUntilIdle()
        coVerify(exactly = 1) { YouTube.browse("FE_opaque", opaque) }
    }
}
