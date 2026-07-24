package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.domain.repository.UpdateRepository
import moe.rukamori.archivetune.ui.state.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdate)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkUpdates(channel: UpdateChannel) {
        updateRepository.checkForUpdates(channel)
            .onEach { info ->
                _updateState.value = when {
                    info == null -> UpdateState.NoUpdate
                    info.isCritical -> UpdateState.CriticalUpdate(
                        versionName = info.versionName,
                        updateUrl = info.updateUrl,
                        changelog = info.changelog
                    )
                    else -> UpdateState.SoftUpdate(
                        versionName = info.versionName,
                        updateUrl = info.updateUrl,
                        changelog = info.changelog
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun forceCheck(channel: UpdateChannel) {
        updateRepository.forceCheckForUpdates(channel)
            .onEach { info ->
                _updateState.value = when {
                    info == null -> UpdateState.NoUpdate
                    info.isCritical -> UpdateState.CriticalUpdate(
                        versionName = info.versionName,
                        updateUrl = info.updateUrl,
                        changelog = info.changelog
                    )
                    else -> UpdateState.SoftUpdate(
                        versionName = info.versionName,
                        updateUrl = info.updateUrl,
                        changelog = info.changelog
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun dismissUpdate() {
        val currentState = _updateState.value
        _updateState.value = when (currentState) {
            is UpdateState.SoftUpdate -> currentState.copy(isOverlayDismissed = true)
            is UpdateState.CriticalUpdate -> currentState.copy(isOverlayDismissed = true)
            else -> currentState
        }
    }
}