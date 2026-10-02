/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.scrobbling.LoginLastFmUseCase
import moe.rukamori.archivetune.scrobbling.LogoutLastFmUseCase
import moe.rukamori.archivetune.scrobbling.ObserveLastFmSettingsUseCase
import moe.rukamori.archivetune.scrobbling.SaveLastFmServiceConfigUseCase
import moe.rukamori.archivetune.scrobbling.UpdateLastFmScrobblingOptionsUseCase
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LastFmSettingsViewModel
    @Inject
    constructor(
        observeSettings: ObserveLastFmSettingsUseCase,
        private val loginLastFm: LoginLastFmUseCase,
        private val logoutLastFm: LogoutLastFmUseCase,
        internal val saveServiceConfig: SaveLastFmServiceConfigUseCase,
        private val updateOptions: UpdateLastFmScrobblingOptionsUseCase,
    ) : ViewModel() {
        private val loginDialog = MutableStateFlow(LastFmLoginDialogUiModel())
        internal val serviceEditor = MutableStateFlow(LastFmServiceEditorUiModel())
        internal val timingEditor = MutableStateFlow(LastFmTimingEditorUiModel())
        private var loginJob: Job? = null

        val state: StateFlow<LastFmSettingsScreenState> =
            combine(
                observeSettings(),
                loginDialog,
                serviceEditor,
                timingEditor,
            ) { settings, login, editor, timing ->
                LastFmSettingsStatePayload(
                    settings = settings,
                    loginDialog = login,
                    serviceEditor = editor,
                    timingEditor = timing,
                )
            }.map<LastFmSettingsStatePayload, LastFmSettingsScreenState> { payload ->
                LastFmSettingsScreenState.Success(payload.toUiModel())
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to load Last.fm settings")
                emit(LastFmSettingsScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LastFmSettingsScreenState.Loading,
            )

        fun openLoginDialog() {
            loginDialog.value = LastFmLoginDialogUiModel(visible = true)
        }

        fun dismissLoginDialog() {
            if (!loginDialog.value.isLoggingIn) {
                loginDialog.value = LastFmLoginDialogUiModel()
            }
        }

        fun updateLoginUsername(value: String) {
            loginDialog.update { dialog ->
                dialog.copy(username = value, errorMessageResId = null)
            }
        }

        fun updateLoginPassword(value: String) {
            loginDialog.update { dialog ->
                dialog.copy(password = value, errorMessageResId = null)
            }
        }

        fun login() {
            if (loginJob?.isActive == true) return
            val dialog = loginDialog.value
            val model = currentModel()
            if (dialog.username.isBlank() || dialog.password.isBlank()) {
                loginDialog.update { it.copy(errorMessageResId = R.string.lastfm_login_missing_credentials) }
                return
            }
            if (model?.canLogin != true) {
                loginDialog.update { it.copy(errorMessageResId = R.string.lastfm_service_not_configured) }
                return
            }

            loginJob =
                viewModelScope.launch(Dispatchers.IO) {
                    loginDialog.update {
                        it.copy(isLoggingIn = true, errorMessageResId = null)
                    }
                    loginLastFm(dialog.username, dialog.password)
                        .onSuccess {
                            loginDialog.value = LastFmLoginDialogUiModel()
                        }.onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            Timber.e(throwable, "Last.fm-compatible login failed")
                            loginDialog.update {
                                it.copy(
                                    isLoggingIn = false,
                                    errorMessageResId = throwable.loginErrorResId(),
                                )
                            }
                        }
                }
        }

        fun logout() {
            viewModelScope.launch(Dispatchers.IO) {
                logoutLastFm()
            }
        }

        fun openServiceEditor() = handleOpenServiceEditor()

        fun dismissServiceEditor() = handleDismissServiceEditor()

        fun updateServiceProvider(provider: LastFmProvider) = handleUpdateServiceProvider(provider)

        fun updateCustomEndpoint(value: String) = handleUpdateCustomEndpoint(value)

        fun updateApiKeyOverride(value: String) = handleUpdateApiKeyOverride(value)

        fun updateSecretOverride(value: String) = handleUpdateSecretOverride(value)

        fun saveServiceEditor() = handleSaveServiceEditor()

        fun setScrobblingEnabled(enabled: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobblingEnabled(enabled)
            }
        }

        fun setNowPlayingEnabled(enabled: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setNowPlayingEnabled(enabled)
            }
        }

        fun setMinTrackDurationSeconds(value: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setMinTrackDurationSeconds(value.coerceIn(10, 60))
            }
        }

        fun setScrobbleDelayPercent(value: Float) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobbleDelayPercent(value.coerceIn(0.3f, 0.95f))
            }
        }

        fun setScrobbleDelaySeconds(value: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobbleDelaySeconds(value.coerceIn(30, 360))
            }
        }

        fun openTimingEditor(setting: LastFmTimingSetting) = handleOpenTimingEditor(setting)

        fun dismissTimingEditor() = handleDismissTimingEditor()

        fun updateTimingMinTrackDuration(value: Int) = handleUpdateTimingMinTrackDuration(value)

        fun updateTimingDelayPercent(value: Float) = handleUpdateTimingDelayPercent(value)

        fun updateTimingDelaySeconds(value: Int) = handleUpdateTimingDelaySeconds(value)

        fun saveTimingEditor() = handleSaveTimingEditor()

        internal fun currentModel(): LastFmSettingsUiModel? = (state.value as? LastFmSettingsScreenState.Success)?.model
    }
