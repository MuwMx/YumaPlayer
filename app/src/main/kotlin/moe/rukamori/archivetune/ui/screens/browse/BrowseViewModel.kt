/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val browseId: String? = savedStateHandle.get<String>("browseId")?.takeIf { it.isNotBlank() }
        private val params: String? = savedStateHandle.get<String>("params")?.takeIf { it.isNotBlank() }

        private val _uiState = MutableStateFlow<YouTubeBrowseUiState>(YouTubeBrowseUiState.Loading)
        val uiState: StateFlow<YouTubeBrowseUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        private fun load() {
            loadJob?.cancel()
            val id = browseId
            if (id == null) {
                _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                return
            }

            _uiState.value = YouTubeBrowseUiState.Loading

            loadJob = viewModelScope.launch {
                try {
                    YouTube
                        .browse(id, params)
                        .onSuccess { rawResult ->
                            ensureActive()
                            _uiState.value =
                                if (rawResult.items.any { it.items.isNotEmpty() }) {
                                    YouTubeBrowseUiState.Success(rawResult)
                                } else {
                                    YouTubeBrowseUiState.Empty
                                }
                        }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            ensureActive()
                            reportException(throwable)
                            _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                        }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    ensureActive()
                    reportException(throwable)
                    _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                }
            }
        }
    }
