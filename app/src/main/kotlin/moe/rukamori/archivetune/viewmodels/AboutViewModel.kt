package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.about.FetchAboutContributorsUseCase
import moe.rukamori.archivetune.about.FetchAboutDependencyLicensesUseCase
import moe.rukamori.archivetune.about.FetchAboutTranslationContributorsUseCase
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val fetchAboutContributors: FetchAboutContributorsUseCase,
    private val fetchTranslationContributors: FetchAboutTranslationContributorsUseCase,
    private val fetchDependencyLicenses: FetchAboutDependencyLicensesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AboutScreenState>(AboutScreenState.Loading)
    val state: StateFlow<AboutScreenState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AboutScreenEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var contributorsJob: Job? = null
    private var translationContributorsJob: Job? = null
    private var dependencyLicensesJob: Job? = null

    private var contributorsState: AboutContributorsUiState = AboutContributorsUiState.Loading
    private var translationContributorsState: AboutTranslationContributorsUiState =
        AboutTranslationContributorsUiState.Loading
    private var dependencyLicensesState: AboutDependencyLicensesUiState = AboutDependencyLicensesUiState.Loading

    private var isOverflowMenuExpanded = false
    private var activeDialog = AboutDialog.NONE

    init {
        updateState()
        loadContributors()
    }

    fun showOverflowMenu() { isOverflowMenuExpanded = true; updateState() }
    fun dismissOverflowMenu() { isOverflowMenuExpanded = false; updateState() }

    fun retryContributors() {
        loadContributors(force = true)
    }

    fun openTranslationContributors() {
        isOverflowMenuExpanded = false
        activeDialog = AboutDialog.TRANSLATION_CONTRIBUTORS
        updateState()
        loadTranslationContributors()
    }

    fun openDependencyLicenses() {
        isOverflowMenuExpanded = false
        activeDialog = AboutDialog.DEPENDENCY_LICENSES
        updateState()
        loadDependencyLicenses()
    }

    fun dismissDialog() { activeDialog = AboutDialog.NONE; updateState() }
    fun retryTranslationContributors() { loadTranslationContributors(force = true) }
    fun retryDependencyLicenses() { loadDependencyLicenses(force = true) }

    fun openUri(uri: String) {
        if (uri.isBlank()) return
        _effects.tryEmit(AboutScreenEffect.OpenUri(uri))
    }

    private fun loadContributors(force: Boolean = false) {
        if (!force && contributorsJob?.isActive == true) return
        contributorsJob?.cancel()
        contributorsState = AboutContributorsUiState.Loading
        updateState()
        contributorsJob = viewModelScope.launch(Dispatchers.IO) {
            contributorsState = try {
                fetchAboutContributors().fold(
                    onSuccess = { contributors ->
                        val contributorUiModels =
                            contributors
                                .take(MaxDisplayedContributors)
                                .toUiCollection()
                        if (contributorUiModels.isEmpty) {
                            AboutContributorsUiState.Empty
                        } else {
                            AboutContributorsUiState.Success(contributorUiModels)
                        }
                    },
                    onFailure = {
                        AboutContributorsUiState.Error(R.string.error_unknown)
                    }
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                AboutContributorsUiState.Error(R.string.error_unknown)
            }
            updateState()
        }
    }

    private fun loadTranslationContributors(force: Boolean = false) {
        if (!force && translationContributorsJob?.isActive == true) return
        if (!force && translationContributorsState is AboutTranslationContributorsUiState.Success) return
        translationContributorsJob?.cancel()
        translationContributorsState = AboutTranslationContributorsUiState.Loading
        updateState()
        translationContributorsJob = viewModelScope.launch(Dispatchers.IO) {
            translationContributorsState = try {
                fetchTranslationContributors().fold(
                    onSuccess = { contributors ->
                        val contributorUiModels = contributors.toUiCollection()
                        if (contributorUiModels.isEmpty) {
                            AboutTranslationContributorsUiState.Empty
                        } else {
                            AboutTranslationContributorsUiState.Success(contributorUiModels)
                        }
                    },
                    onFailure = {
                        AboutTranslationContributorsUiState.Error(R.string.error_unknown)
                    }
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                AboutTranslationContributorsUiState.Error(R.string.error_unknown)
            }
            updateState()
        }
    }

    private fun loadDependencyLicenses(force: Boolean = false) {
        if (!force && dependencyLicensesJob?.isActive == true) return
        if (!force && dependencyLicensesState is AboutDependencyLicensesUiState.Success) return
        dependencyLicensesJob?.cancel()
        dependencyLicensesState = AboutDependencyLicensesUiState.Loading
        updateState()
        dependencyLicensesJob = viewModelScope.launch(Dispatchers.IO) {
            dependencyLicensesState = try {
                fetchDependencyLicenses().fold(
                    onSuccess = { licenses ->
                        val licenseUiModels = licenses.toUiCollection()
                        if (licenseUiModels.isEmpty) AboutDependencyLicensesUiState.Empty
                        else AboutDependencyLicensesUiState.Success(licenseUiModels)
                    },
                    onFailure = { AboutDependencyLicensesUiState.Error(R.string.error_unknown) }
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                AboutDependencyLicensesUiState.Error(R.string.error_unknown)
            }
            updateState()
        }
    }

    private fun updateState() {
        _state.value = AboutScreenState.Success(
            buildAboutUiModel(
                contributorsState = contributorsState,
                dependencyLicensesState = dependencyLicensesState,
                translationContributorsState = translationContributorsState,
                isOverflowMenuExpanded = isOverflowMenuExpanded,
                activeDialog = activeDialog,
            )
        )
    }
}
