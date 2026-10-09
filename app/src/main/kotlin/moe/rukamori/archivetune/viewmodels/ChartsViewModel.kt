/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface ChartsUiState {
    data object Loading : ChartsUiState
    data class Success(val page: ChartsPage) : ChartsUiState
    data object Empty : ChartsUiState
    data class Error(@StringRes val messageResId: Int) : ChartsUiState
}

@HiltViewModel
class ChartsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<ChartsUiState>(ChartsUiState.Loading)
        val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

        private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
        val chartsPage: StateFlow<ChartsPage?> = _chartsPage.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private var loadJob: Job? = null

        fun retry() {
            loadCharts()
        }

        fun loadCharts() {
            loadJob?.cancel()
            _isLoading.value = true
            _error.value = null
            _uiState.value = ChartsUiState.Loading

            loadJob =
                viewModelScope.launch {
                    YouTube
                        .getChartsPage()
                        .onSuccess { page ->
                            _chartsPage.value = page
                            _uiState.value =
                                if (page.sections.isEmpty() || page.sections.all { it.items.isEmpty() }) {
                                    ChartsUiState.Empty
                                } else {
                                    ChartsUiState.Success(page)
                                }
                        }.onFailure { e ->
                            if (e is CancellationException) throw e
                            reportException(e)
                            _error.value = "Failed to load charts: ${e.message}"
                            _uiState.value = ChartsUiState.Error(R.string.error_unknown)
                        }

                    _isLoading.value = false
                }
        }

        fun loadMore() {
            viewModelScope.launch {
                _chartsPage.value?.continuation?.let { continuation ->
                    _isLoading.value = true
                    YouTube
                        .getChartsPage(continuation)
                        .onSuccess { newPage ->
                            val updatedPage =
                                _chartsPage.value?.copy(
                                    sections = _chartsPage.value?.sections.orEmpty() + newPage.sections,
                                    continuation = newPage.continuation,
                                )
                            _chartsPage.value = updatedPage
                            if (updatedPage != null) {
                                _uiState.value = ChartsUiState.Success(updatedPage)
                            }
                        }.onFailure { e ->
                            if (e is CancellationException) throw e
                            _error.value = "Failed to load more: ${e.message}"
                        }
                    _isLoading.value = false
                }
            }
        }
    }
