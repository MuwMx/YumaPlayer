/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig

internal fun LastFmSettingsViewModel.handleOpenServiceEditor() {
    val model = currentModel() ?: return
    serviceEditor.value =
        LastFmServiceEditorUiModel(
            visible = true,
            provider = model.provider,
            customEndpoint =
                model.customEndpoint.ifBlank {
                    if (model.provider == LastFmProvider.CUSTOM) model.resolvedEndpoint else ""
                },
            apiKeyOverride = model.apiKeyOverride,
            secretOverride = model.secretOverride,
        )
}

internal fun LastFmSettingsViewModel.handleDismissServiceEditor() {
    if (!serviceEditor.value.isSaving) {
        serviceEditor.value = LastFmServiceEditorUiModel()
    }
}

internal fun LastFmSettingsViewModel.handleUpdateServiceProvider(provider: LastFmProvider) {
    serviceEditor.update { editor ->
        editor.copy(provider = provider, errorMessageResId = null)
    }
}

internal fun LastFmSettingsViewModel.handleUpdateCustomEndpoint(value: String) {
    serviceEditor.update { editor ->
        editor.copy(customEndpoint = value, errorMessageResId = null)
    }
}

internal fun LastFmSettingsViewModel.handleUpdateApiKeyOverride(value: String) {
    serviceEditor.update { editor ->
        editor.copy(apiKeyOverride = value, errorMessageResId = null)
    }
}

internal fun LastFmSettingsViewModel.handleUpdateSecretOverride(value: String) {
    serviceEditor.update { editor ->
        editor.copy(secretOverride = value, errorMessageResId = null)
    }
}

internal fun LastFmSettingsViewModel.handleSaveServiceEditor() {
    val editor = serviceEditor.value
    if (editor.isSaving) return
    if (editor.provider == LastFmProvider.CUSTOM &&
        LastFmServiceConfig.normalizeEndpointOrNull(editor.customEndpoint) == null
    ) {
        serviceEditor.update { it.copy(errorMessageResId = R.string.lastfm_endpoint_invalid) }
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        serviceEditor.update {
            it.copy(isSaving = true, errorMessageResId = null)
        }
        val saved =
            saveServiceConfig(
                provider = editor.provider,
                customEndpoint = editor.customEndpoint,
                apiKeyOverride = editor.apiKeyOverride,
                secretOverride = editor.secretOverride,
            )
        if (saved == null) {
            serviceEditor.update {
                it.copy(
                    isSaving = false,
                    errorMessageResId = R.string.lastfm_endpoint_invalid,
                )
            }
        } else {
            serviceEditor.value = LastFmServiceEditorUiModel()
        }
    }
}

internal fun LastFmSettingsViewModel.handleOpenTimingEditor(setting: LastFmTimingSetting) {
    val model = currentModel() ?: return
    timingEditor.value =
        LastFmTimingEditorUiModel(
            setting = setting,
            minTrackDurationSeconds = model.minTrackDurationSeconds,
            scrobbleDelayPercent = model.scrobbleDelayPercent,
            scrobbleDelaySeconds = model.scrobbleDelaySeconds,
        )
}

internal fun LastFmSettingsViewModel.handleDismissTimingEditor() {
    timingEditor.value = LastFmTimingEditorUiModel()
}

internal fun LastFmSettingsViewModel.handleUpdateTimingMinTrackDuration(value: Int) {
    timingEditor.update { editor ->
        editor.copy(minTrackDurationSeconds = value.coerceIn(10, 60))
    }
}

internal fun LastFmSettingsViewModel.handleUpdateTimingDelayPercent(value: Float) {
    timingEditor.update { editor ->
        editor.copy(scrobbleDelayPercent = value.coerceIn(0.3f, 0.95f))
    }
}

internal fun LastFmSettingsViewModel.handleUpdateTimingDelaySeconds(value: Int) {
    timingEditor.update { editor ->
        editor.copy(scrobbleDelaySeconds = value.coerceIn(30, 360))
    }
}

internal fun LastFmSettingsViewModel.handleSaveTimingEditor() {
    val editor = timingEditor.value
    when (editor.setting) {
        LastFmTimingSetting.MIN_TRACK_DURATION -> setMinTrackDurationSeconds(editor.minTrackDurationSeconds)
        LastFmTimingSetting.DELAY_PERCENT -> setScrobbleDelayPercent(editor.scrobbleDelayPercent)
        LastFmTimingSetting.DELAY_SECONDS -> setScrobbleDelaySeconds(editor.scrobbleDelaySeconds)
        null -> return
    }
    timingEditor.value = LastFmTimingEditorUiModel()
}
