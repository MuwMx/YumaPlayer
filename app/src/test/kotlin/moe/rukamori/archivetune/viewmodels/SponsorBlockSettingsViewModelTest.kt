package moe.rukamori.archivetune.viewmodels

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.sponsorblock.DEFAULT_SPONSOR_BLOCK_API_URL
import moe.rukamori.archivetune.sponsorblock.ObserveSponsorBlockSettingsUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockApiUrlUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockCategoriesUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockEnabledUseCase
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategory
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettings
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettingsScreenState
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettingsViewModel
import moe.rukamori.archivetune.sponsorblock.ValidateSponsorBlockApiUrlUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorBlockSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val observeSettings = mockk<ObserveSponsorBlockSettingsUseCase>()
    private val setEnabled = mockk<SetSponsorBlockEnabledUseCase>(relaxed = true)
    private val setCategories = mockk<SetSponsorBlockCategoriesUseCase>(relaxed = true)
    private val setApiUrl = mockk<SetSponsorBlockApiUrlUseCase>(relaxed = true)
    private val validateApiUrl = mockk<ValidateSponsorBlockApiUrlUseCase>()

    private val settingsFlow = MutableStateFlow(
        SponsorBlockSettings(
            enabled = true,
            categories = setOf(SponsorBlockCategory.SPONSOR),
            apiUrl = DEFAULT_SPONSOR_BLOCK_API_URL,
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeSettings() } returns settingsFlow
        every { validateApiUrl(any()) } answers { firstArg<String>().startsWith("https://") }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SponsorBlockSettingsViewModel =
        SponsorBlockSettingsViewModel(
            observeSettings = observeSettings,
            setEnabled = setEnabled,
            setCategories = setCategories,
            setApiUrl = setApiUrl,
            validateApiUrl = validateApiUrl,
        )

    @Test
    fun initialState_loadsSettingsAndExposesNineCategories() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SponsorBlockSettingsScreenState.Success)
        val data = (state as SponsorBlockSettingsScreenState.Success).data
        assertTrue(data.enabled)
        assertEquals(9, data.categoryOptions.size)
        assertEquals(1, data.selectedCategoryOptions.size)
        assertEquals(SponsorBlockCategory.SPONSOR, data.selectedCategoryOptions.first().category)
    }

    @Test
    fun off_preservesCategoriesAndUrl() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEnabledChange(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SponsorBlockSettingsScreenState.Success)
        val data = (state as SponsorBlockSettingsScreenState.Success).data
        assertFalse(data.enabled)
        assertEquals(1, data.selectedCategoryOptions.size)
        assertEquals(DEFAULT_SPONSOR_BLOCK_API_URL, data.apiUrl)
        coVerify { setEnabled(false) }
    }

    @Test
    fun cancel_discardsCategoryDraft() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySheetOpen()
        viewModel.onCategoryCheckedChange(SponsorBlockCategory.INTRO, true)

        var data = (viewModel.uiState.value as SponsorBlockSettingsScreenState.Success).data
        assertTrue(data.isCategorySheetVisible)
        assertEquals(2, data.draftCategoryOptions.size)
        assertEquals(1, data.selectedCategoryOptions.size)

        viewModel.onCategorySheetDismiss()
        advanceUntilIdle()

        data = (viewModel.uiState.value as SponsorBlockSettingsScreenState.Success).data
        assertFalse(data.isCategorySheetVisible)
        assertEquals(1, data.draftCategoryOptions.size)
        assertEquals(1, data.selectedCategoryOptions.size)
        coVerify(exactly = 0) { setCategories(any()) }
    }

    @Test
    fun confirm_persistsCategorySelection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySheetOpen()
        viewModel.onCategoryCheckedChange(SponsorBlockCategory.INTRO, true)
        viewModel.onCategorySelectionConfirm()
        advanceUntilIdle()

        val data = (viewModel.uiState.value as SponsorBlockSettingsScreenState.Success).data
        assertFalse(data.isCategorySheetVisible)
        assertEquals(2, data.selectedCategoryOptions.size)
        coVerify { setCategories(setOf(SponsorBlockCategory.SPONSOR, SponsorBlockCategory.INTRO)) }
    }

    @Test
    fun reopen_restoresPersistedSelection() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySheetOpen()
        viewModel.onCategoryCheckedChange(SponsorBlockCategory.INTRO, true)
        viewModel.onCategorySheetDismiss()

        viewModel.onCategorySheetOpen()
        val data = (viewModel.uiState.value as SponsorBlockSettingsScreenState.Success).data
        assertEquals(1, data.draftCategoryOptions.size)
        assertEquals(SponsorBlockCategory.SPONSOR, data.draftCategoryOptions.first().category)
    }

    @Test
    fun apiUrlConfirm_savesLiveUrl() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val customUrl = "https://custom.sponsorblock.instance"
        viewModel.onApiUrlEditorOpen()
        viewModel.onApiUrlDraftChange(customUrl)
        viewModel.onApiUrlConfirm()
        advanceUntilIdle()

        val data = (viewModel.uiState.value as SponsorBlockSettingsScreenState.Success).data
        assertFalse(data.isApiUrlEditorVisible)
        coVerify { setApiUrl(customUrl) }
    }
}
