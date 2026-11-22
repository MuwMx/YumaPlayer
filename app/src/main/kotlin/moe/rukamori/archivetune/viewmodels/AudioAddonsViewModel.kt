package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.addons.AddonDescriptor
import moe.rukamori.archivetune.addons.AddonManager
import moe.rukamori.archivetune.addons.AddonRepository
import javax.inject.Inject

sealed interface AddonProbeUiState {
    data object Idle : AddonProbeUiState
    data object Checking : AddonProbeUiState
    data class Online(val latencyMs: Long, val tracksFound: Int) : AddonProbeUiState
    data class Error(val message: String) : AddonProbeUiState
}

data class AudioAddonsUiState(
    val repositories: List<AddonRepository> = emptyList(),
    val addons: List<AddonDescriptor> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val probeStates: Map<String, AddonProbeUiState> = emptyMap(),
)

@HiltViewModel
class AudioAddonsViewModel @Inject constructor(
    private val addonManager: AddonManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioAddonsUiState())
    val uiState: StateFlow<AudioAddonsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val addons = addonManager.refreshRepositories()
                val repos = addonManager.listRepositories()
                _uiState.update { current ->
                    current.copy(
                        repositories = repos,
                        addons = addons,
                        isLoading = false,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to refresh") }
            }
        }
    }

    fun addRepository(url: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = addonManager.addRepository(url)
            res.fold(
                onSuccess = {
                    val repos = addonManager.listRepositories()
                    val addons = addonManager.getActiveAddons()
                    _uiState.update { current ->
                        current.copy(
                            repositories = repos,
                            addons = addons,
                            isLoading = false,
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to add repository",
                        )
                    }
                },
            )
        }
    }

    fun setRepositoryEnabled(url: String, enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                addonManager.setRepositoryEnabled(url, enabled)
                val repos = addonManager.listRepositories()
                val addons = addonManager.getActiveAddons()
                _uiState.update { current ->
                    current.copy(
                        repositories = repos,
                        addons = addons,
                        isLoading = false,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun removeRepository(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                addonManager.removeRepository(url)
                val repos = addonManager.listRepositories()
                val addons = addonManager.getActiveAddons()
                _uiState.update { current ->
                    current.copy(
                        repositories = repos,
                        addons = addons,
                        isLoading = false,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun testAddon(addonId: String) {
        viewModelScope.launch {
            _uiState.update { current ->
                val updatedProbes = current.probeStates.toMutableMap()
                updatedProbes[addonId] = AddonProbeUiState.Checking
                current.copy(probeStates = updatedProbes)
            }
            val result = addonManager.testAddon(addonId)
            val probeState = if (result.ok) {
                AddonProbeUiState.Online(result.latencyMs, result.tracksFound)
            } else {
                AddonProbeUiState.Error(result.message)
            }
            _uiState.update { current ->
                val updatedProbes = current.probeStates.toMutableMap()
                updatedProbes[addonId] = probeState
                current.copy(probeStates = updatedProbes)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
