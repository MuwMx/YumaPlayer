/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.scrobbling

import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.lastfm.models.Authentication
import javax.inject.Inject

class ObserveLastFmSettingsUseCase
    @Inject
    constructor(
        private val repository: LastFmSettingsRepository,
    ) {
        operator fun invoke(): Flow<LastFmSettingsData> = repository.observeSettings()
    }

class LoginLastFmUseCase
    @Inject
    constructor(
        private val repository: LastFmSettingsRepository,
    ) {
        suspend operator fun invoke(
            username: String,
            password: String,
        ): Result<Authentication> = repository.login(username = username, password = password)
    }

class LogoutLastFmUseCase
    @Inject
    constructor(
        private val repository: LastFmSettingsRepository,
    ) {
        suspend operator fun invoke() {
            repository.logout()
        }
    }

class SaveLastFmServiceConfigUseCase
    @Inject
    constructor(
        private val repository: LastFmSettingsRepository,
    ) {
        suspend operator fun invoke(
            provider: LastFmProvider,
            customEndpoint: String,
            apiKeyOverride: String,
            secretOverride: String,
        ): LastFmServiceConfig? =
            repository.saveServiceConfig(
                provider = provider,
                customEndpoint = customEndpoint,
                apiKeyOverride = apiKeyOverride,
                secretOverride = secretOverride,
            )
    }

class UpdateLastFmScrobblingOptionsUseCase
    @Inject
    constructor(
        private val repository: LastFmSettingsRepository,
    ) {
        suspend fun setScrobblingEnabled(enabled: Boolean) {
            repository.setScrobblingEnabled(enabled)
        }

        suspend fun setNowPlayingEnabled(enabled: Boolean) {
            repository.setNowPlayingEnabled(enabled)
        }

        suspend fun setMinTrackDurationSeconds(value: Int) {
            repository.setMinTrackDurationSeconds(value)
        }

        suspend fun setScrobbleDelayPercent(value: Float) {
            repository.setScrobbleDelayPercent(value)
        }

        suspend fun setScrobbleDelaySeconds(value: Int) {
            repository.setScrobbleDelaySeconds(value)
        }
    }
