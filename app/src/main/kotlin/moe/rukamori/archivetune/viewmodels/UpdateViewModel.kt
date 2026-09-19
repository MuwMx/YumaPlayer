package moe.rukamori.archivetune.viewmodels

import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.constants.DismissedUpdateVersionKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.domain.repository.UpdateRepository
import moe.rukamori.archivetune.models.AppUpdateInfo
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdate)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private suspend fun mapToUpdateState(info: AppUpdateInfo?): UpdateState {
        if (info == null) return UpdateState.NoUpdate
        val dismissedVersion = App.instance.dataStore.getAsync(DismissedUpdateVersionKey)
        val isDismissed = info.versionName == dismissedVersion
        return if (info.isCritical) {
            UpdateState.CriticalUpdate(
                versionName = info.versionName,
                updateUrl = info.updateUrl,
                changelog = info.changelog,
                imageUrl = info.imageUrl,
                isOverlayDismissed = isDismissed,
            )
        } else {
            UpdateState.SoftUpdate(
                versionName = info.versionName,
                updateUrl = info.updateUrl,
                changelog = info.changelog,
                imageUrl = info.imageUrl,
                isOverlayDismissed = isDismissed,
            )
        }
    }

    fun checkUpdates(channel: UpdateChannel) {
        updateRepository.checkForUpdates(channel)
            .onEach { info ->
                _updateState.value = mapToUpdateState(info)
            }
            .launchIn(viewModelScope)
    }

    fun forceCheck(channel: UpdateChannel) {
        updateRepository.forceCheckForUpdates(channel)
            .onEach { info ->
                _updateState.value = mapToUpdateState(info)
            }
            .launchIn(viewModelScope)
    }

    fun dismissUpdate() {
        val currentState = _updateState.value
        val dismissedVersion = when (currentState) {
            is UpdateState.SoftUpdate -> currentState.versionName
            is UpdateState.CriticalUpdate -> currentState.versionName
            else -> null
        }
        if (dismissedVersion != null) {
            viewModelScope.launch {
                App.instance.dataStore.edit { prefs ->
                    prefs[DismissedUpdateVersionKey] = dismissedVersion
                }
            }
        }
        _updateState.value = when (currentState) {
            is UpdateState.SoftUpdate -> currentState.copy(isOverlayDismissed = true)
            is UpdateState.CriticalUpdate -> currentState.copy(isOverlayDismissed = true)
            else -> currentState
        }
    }
}