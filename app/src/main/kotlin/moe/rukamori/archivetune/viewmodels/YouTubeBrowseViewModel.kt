/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface YouTubeBrowseUiState {
    data object Loading : YouTubeBrowseUiState
    data class Success(val result: BrowseResult) : YouTubeBrowseUiState
    data object Empty : YouTubeBrowseUiState
    data class Error(@StringRes val messageResId: Int) : YouTubeBrowseUiState
}

@HiltViewModel
class YouTubeBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val rawBrowseId = savedStateHandle.get<String>("browseId")?.takeIf { it.isNotBlank() }
        private val rawParams = savedStateHandle.get<String>("params")?.takeIf { it.isNotBlank() && it != "null" }

        private val _uiState = MutableStateFlow<YouTubeBrowseUiState>(YouTubeBrowseUiState.Loading)
        val uiState: StateFlow<YouTubeBrowseUiState> = _uiState.asStateFlow()

        private val _result = MutableStateFlow<BrowseResult?>(null)
        val result: StateFlow<BrowseResult?> = _result.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        private fun load() {
            val browseId = rawBrowseId
            if (browseId == null) {
                reportException(IllegalArgumentException("YouTubeBrowse: missing or blank browseId"))
                _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                return
            }

            loadJob?.cancel()
            _uiState.value = YouTubeBrowseUiState.Loading

            loadJob =
                viewModelScope.launch {
                    YouTube
                        .browse(browseId, rawParams)
                        .onSuccess { rawResult ->
                            try {
                                val hideVideo = context.dataStore.get(HideVideoKey, false)
                                val filtered =
                                    rawResult
                                        .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                        .filterVideo(hideVideo)
                                        .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                                _result.value = filtered
                                _uiState.value =
                                    if (filtered.items.isEmpty() || filtered.items.all { it.items.isEmpty() }) {
                                        YouTubeBrowseUiState.Empty
                                    } else {
                                        YouTubeBrowseUiState.Success(filtered)
                                    }
                            } catch (throwable: Throwable) {
                                if (throwable is CancellationException) throw throwable
                                reportException(throwable)
                                _result.value = null
                                _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                            }
                        }.onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            reportException(throwable)
                            _result.value = null
                            _uiState.value = YouTubeBrowseUiState.Error(R.string.error_unknown)
                        }
                }
        }
    }
