/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.scrobbling.LastFmSettingsData

sealed interface LastFmSettingsScreenState {
    data object Loading : LastFmSettingsScreenState

    data class Success(
        val model: LastFmSettingsUiModel,
    ) : LastFmSettingsScreenState

    data object Empty : LastFmSettingsScreenState

    data class Error(
        val messageResId: Int,
    ) : LastFmSettingsScreenState
}

@Immutable
data class LastFmSettingsUiModel(
    val provider: LastFmProvider,
    val resolvedEndpoint: String,
    val customEndpoint: String,
    val apiKeyOverride: String,
    val secretOverride: String,
    val serviceConfigured: Boolean,
    val endpointValid: Boolean,
    val username: String,
    val isLoggedIn: Boolean,
    val scrobblingEnabled: Boolean,
    val nowPlayingEnabled: Boolean,
    val minTrackDurationSeconds: Int,
    val scrobbleDelayPercent: Float,
    val scrobbleDelaySeconds: Int,
    val loginDialog: LastFmLoginDialogUiModel,
    val serviceEditor: LastFmServiceEditorUiModel,
    val timingEditor: LastFmTimingEditorUiModel,
) {
    val canLogin: Boolean
        get() = serviceConfigured && endpointValid

    val canEnableScrobbling: Boolean
        get() = isLoggedIn && serviceConfigured && endpointValid
}

@Immutable
data class LastFmLoginDialogUiModel(
    val visible: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val errorMessageResId: Int? = null,
)

@Immutable
data class LastFmServiceEditorUiModel(
    val visible: Boolean = false,
    val provider: LastFmProvider = LastFmProvider.LASTFM,
    val customEndpoint: String = "",
    val apiKeyOverride: String = "",
    val secretOverride: String = "",
    val isSaving: Boolean = false,
    val errorMessageResId: Int? = null,
) {
    val showCustomEndpoint: Boolean
        get() = provider == LastFmProvider.CUSTOM

    val showApiCredentials: Boolean
        get() = provider != LastFmProvider.LASTFM
}

enum class LastFmTimingSetting {
    MIN_TRACK_DURATION,
    DELAY_PERCENT,
    DELAY_SECONDS,
}

@Immutable
data class LastFmTimingEditorUiModel(
    val setting: LastFmTimingSetting? = null,
    val minTrackDurationSeconds: Int = LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
    val scrobbleDelayPercent: Float = LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
    val scrobbleDelaySeconds: Int = LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
) {
    val visible: Boolean
        get() = setting != null
}

internal data class LastFmSettingsStatePayload(
    val settings: LastFmSettingsData,
    val loginDialog: LastFmLoginDialogUiModel,
    val serviceEditor: LastFmServiceEditorUiModel,
    val timingEditor: LastFmTimingEditorUiModel,
)

internal fun LastFmSettingsStatePayload.toUiModel(): LastFmSettingsUiModel =
    LastFmSettingsUiModel(
        provider = settings.serviceConfig.provider,
        resolvedEndpoint = settings.serviceConfig.endpoint,
        customEndpoint = settings.serviceConfig.customEndpoint,
        apiKeyOverride = settings.serviceConfig.apiKeyOverride,
        secretOverride = settings.serviceConfig.secretOverride,
        serviceConfigured = settings.serviceConfig.initialized,
        endpointValid = settings.serviceConfig.endpointValid,
        username = settings.username,
        isLoggedIn = settings.isLoggedIn,
        scrobblingEnabled = settings.scrobblingEnabled,
        nowPlayingEnabled = settings.nowPlayingEnabled,
        minTrackDurationSeconds = settings.minTrackDurationSeconds,
        scrobbleDelayPercent = settings.scrobbleDelayPercent,
        scrobbleDelaySeconds = settings.scrobbleDelaySeconds,
        loginDialog = this.loginDialog,
        serviceEditor = this.serviceEditor,
        timingEditor = this.timingEditor,
    )

internal fun Throwable.loginErrorResId(): Int =
    when (this) {
        is LastFM.LastFmException -> {
            when (code) {
                4 -> R.string.lastfm_login_invalid_credentials
                10 -> R.string.lastfm_login_invalid_api_key
                13 -> R.string.lastfm_login_authentication_error
                26 -> R.string.lastfm_login_api_key_suspended
                else -> R.string.lastfm_login_failed
            }
        }

        else -> {
            val message = message.orEmpty()
            if (
                message.contains("network", ignoreCase = true) ||
                message.contains("connect", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true)
            ) {
                R.string.lastfm_login_network_error
            } else {
                R.string.lastfm_login_failed
            }
        }
    }
