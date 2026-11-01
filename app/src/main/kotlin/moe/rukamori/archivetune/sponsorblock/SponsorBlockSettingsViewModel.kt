/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.sponsorblock

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sponsorblock.ObserveSponsorBlockSettingsUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockApiUrlUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockCategoriesUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockEnabledUseCase
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategory
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettings
import moe.rukamori.archivetune.sponsorblock.ValidateSponsorBlockApiUrlUseCase
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface SponsorBlockSettingsScreenState {
    data object Loading : SponsorBlockSettingsScreenState

    data class Success(
        val data: SponsorBlockSettingsUiData,
    ) : SponsorBlockSettingsScreenState

    data object Empty : SponsorBlockSettingsScreenState

    data class Error(
        @StringRes val messageRes: Int,
    ) : SponsorBlockSettingsScreenState
}

@Immutable
data class SponsorBlockCategoryUiModel(
    val category: SponsorBlockCategory,
    @StringRes val labelRes: Int,
)

@Immutable
data class SponsorBlockSettingsUiData(
    val enabled: Boolean,
    val categoryOptions: List<SponsorBlockCategoryUiModel>,
    val selectedCategoryOptions: List<SponsorBlockCategoryUiModel>,
    val draftCategoryOptions: List<SponsorBlockCategoryUiModel>,
    val isCategorySheetVisible: Boolean,
    val apiUrl: String,
    val apiUrlDraft: String,
    val isApiUrlDraftValid: Boolean,
    val isApiUrlEditorVisible: Boolean,
)

@HiltViewModel
class SponsorBlockSettingsViewModel
    @Inject
    constructor(
        private val observeSettings: ObserveSponsorBlockSettingsUseCase,
        private val setEnabled: SetSponsorBlockEnabledUseCase,
        private val setCategories: SetSponsorBlockCategoriesUseCase,
        private val setApiUrl: SetSponsorBlockApiUrlUseCase,
        private val validateApiUrl: ValidateSponsorBlockApiUrlUseCase,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow<SponsorBlockSettingsScreenState>(SponsorBlockSettingsScreenState.Loading)
        val uiState: StateFlow<SponsorBlockSettingsScreenState> = mutableUiState.asStateFlow()

        private var observeJob: Job? = null
        private var enabledUpdateJob: Job? = null
        private var categoriesUpdateJob: Job? = null
        private var apiUrlUpdateJob: Job? = null

        init {
            observe()
        }

        fun retry() {
            observeJob?.cancel()
            mutableUiState.value = SponsorBlockSettingsScreenState.Loading
            observe()
        }

        fun onEnabledChange(enabled: Boolean) {
            updateSuccess { it.copy(enabled = enabled) }
            enabledUpdateJob?.cancel()
            enabledUpdateJob = launchUpdate { setEnabled(enabled) }
        }

        fun onCategorySheetOpen() {
            updateSuccess {
                it.copy(draftCategoryOptions = it.selectedCategoryOptions, isCategorySheetVisible = true)
            }
        }

        fun onCategorySheetDismiss() {
            updateSuccess {
                it.copy(draftCategoryOptions = it.selectedCategoryOptions, isCategorySheetVisible = false)
            }
        }

        fun onCategoryCheckedChange(category: SponsorBlockCategory, checked: Boolean) {
            updateSuccess { data ->
                val draft = data.draftCategoryOptions.mapTo(mutableSetOf()) { it.category }
                if (checked) draft += category else draft -= category
                data.copy(draftCategoryOptions = data.categoryOptions.filter { it.category in draft })
            }
        }

        fun onCategoryOptionCheckedChange(option: SponsorBlockCategoryUiModel, checked: Boolean) {
            onCategoryCheckedChange(option.category, checked)
        }

        fun onCategorySelectionConfirm() {
            val data = currentData() ?: return
            val categories = data.draftCategoryOptions.mapTo(linkedSetOf()) { it.category }
            updateSuccess { it.copy(selectedCategoryOptions = it.draftCategoryOptions, isCategorySheetVisible = false) }
            categoriesUpdateJob?.cancel()
            categoriesUpdateJob = launchUpdate { setCategories(categories) }
        }

        fun onApiUrlEditorOpen() {
            updateSuccess { it.copy(apiUrlDraft = it.apiUrl, isApiUrlDraftValid = true, isApiUrlEditorVisible = true) }
        }

        fun onApiUrlEditorDismiss() {
            updateSuccess { it.copy(apiUrlDraft = it.apiUrl, isApiUrlDraftValid = true, isApiUrlEditorVisible = false) }
        }

        fun onApiUrlDraftChange(value: String) {
            updateSuccess { it.copy(apiUrlDraft = value, isApiUrlDraftValid = validateApiUrl(value)) }
        }

        fun onApiUrlConfirm() {
            val data = currentData() ?: return
            if (!data.isApiUrlDraftValid) return
            val apiUrlDraft = data.apiUrlDraft
            updateSuccess { it.copy(isApiUrlEditorVisible = false) }
            apiUrlUpdateJob?.cancel()
            apiUrlUpdateJob = launchUpdate { setApiUrl(apiUrlDraft) }
        }

        private fun observe() {
            if (observeJob?.isActive == true) return
            observeJob =
                viewModelScope.launch {
                    try {
                        observeSettings().collect { settings ->
                            mutableUiState.value =
                                SponsorBlockSettingsScreenState.Success(settings.toUiData(currentData()))
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        mutableUiState.value = SponsorBlockSettingsScreenState.Error(R.string.error_unknown)
                    }
                }
        }

        private fun launchUpdate(update: suspend () -> Unit): Job =
            viewModelScope.launch {
                try {
                    update()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    reportException(throwable)
                    mutableUiState.value = SponsorBlockSettingsScreenState.Error(R.string.error_unknown)
                }
            }

        private fun updateSuccess(transform: (SponsorBlockSettingsUiData) -> SponsorBlockSettingsUiData) {
            mutableUiState.update { state ->
                val success = state as? SponsorBlockSettingsScreenState.Success ?: return@update state
                success.copy(data = transform(success.data))
            }
        }

        private fun currentData(): SponsorBlockSettingsUiData? =
            (mutableUiState.value as? SponsorBlockSettingsScreenState.Success)?.data

        private fun SponsorBlockSettings.toUiData(existing: SponsorBlockSettingsUiData?): SponsorBlockSettingsUiData {
            val selected = CATEGORY_OPTIONS.filter { it.category in categories }
            val keepCategoryDraft = existing?.isCategorySheetVisible == true
            val keepApiUrlDraft = existing?.isApiUrlEditorVisible == true
            return SponsorBlockSettingsUiData(
                enabled = enabled,
                categoryOptions = CATEGORY_OPTIONS,
                selectedCategoryOptions = selected,
                draftCategoryOptions = if (keepCategoryDraft && existing != null) existing.draftCategoryOptions else selected,
                isCategorySheetVisible = keepCategoryDraft,
                apiUrl = apiUrl,
                apiUrlDraft = if (keepApiUrlDraft && existing != null) existing.apiUrlDraft else apiUrl,
                isApiUrlDraftValid = if (keepApiUrlDraft && existing != null) existing.isApiUrlDraftValid else true,
                isApiUrlEditorVisible = keepApiUrlDraft,
            )
        }

        companion object {
            val CATEGORY_OPTIONS: List<SponsorBlockCategoryUiModel> =
                listOf(
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.MUSIC_OFF_TOPIC, R.string.sponsor_block_music_off_topic),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.SPONSOR, R.string.sponsor_block_sponsors),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.INTRO, R.string.sponsor_block_intro),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.OUTRO, R.string.sponsor_block_outro),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.SELF_PROMOTION, R.string.sponsor_block_self_promotions),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.PREVIEW, R.string.sponsor_block_previews),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.FILLER, R.string.sponsor_block_fillers),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.INTERACTION, R.string.sponsor_block_subscription_reminders),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.HOOK, R.string.sponsor_block_hooks),
                )
        }
    }
