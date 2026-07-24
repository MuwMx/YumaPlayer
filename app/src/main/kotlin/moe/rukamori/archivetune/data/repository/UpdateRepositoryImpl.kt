package moe.rukamori.archivetune.data.repository

import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.domain.repository.UpdateRepository
import moe.rukamori.archivetune.models.AppUpdateInfo
import moe.rukamori.archivetune.utils.MarkdownCleaner
import moe.rukamori.archivetune.utils.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepositoryImpl @Inject constructor() : UpdateRepository {

    override fun checkForUpdates(channel: UpdateChannel): Flow<AppUpdateInfo?> = flow {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            emit(null)
            return@flow
        }

        val result = when (channel) {
            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyReleaseInfo()
            else -> Updater.getLatestReleaseInfo()
        }

        result.onSuccess { release ->
            val currentVersion = BuildConfig.VERSION_NAME
            if (Updater.isUpdateAvailable(release.tagName, currentVersion)) {
                val downloadUrl = when (channel) {
                    UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyDownloadUrl()
                    else -> Updater.getLatestDownloadUrl()
                }

                val isCritical = release.body?.contains("[CRITICAL]", ignoreCase = true) == true

                emit(
                    AppUpdateInfo(
                        versionCode = 0,
                        versionName = release.tagName,
                        updateUrl = downloadUrl,
                        isCritical = isCritical,
                        changelog = MarkdownCleaner.clean(release.body)
                    )
                )
            } else {
                emit(null)
            }
        }.onFailure {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override fun forceCheckForUpdates(channel: UpdateChannel): Flow<AppUpdateInfo?> = flow {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            emit(null)
            return@flow
        }

        val result = when (channel) {
            UpdateChannel.DAILY_NIGHTLY -> Updater.getAllDailyNightlyReleases(forceRefresh = true)
            else -> Updater.getAllReleases(forceRefresh = true)
        }

        result.onSuccess { releases ->
            val latest = when (channel) {
                UpdateChannel.DAILY_NIGHTLY -> Updater.findLatestDailyNightlyRelease(releases)
                else -> Updater.findLatestRelease(releases)
            }

            if (latest != null) {
                val currentVersion = BuildConfig.VERSION_NAME
                if (Updater.isUpdateAvailable(latest.tagName, currentVersion)) {
                    val downloadUrl = when (channel) {
                        UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestDailyNightlyDownloadUrl()
                        else -> Updater.getLatestDownloadUrl()
                    }

                    val isCritical = latest.body?.contains("[CRITICAL]", ignoreCase = true) == true

                    emit(
                        AppUpdateInfo(
                            versionCode = 0,
                            versionName = latest.tagName,
                            updateUrl = downloadUrl,
                            isCritical = isCritical,
                            changelog = MarkdownCleaner.clean(latest.body)
                        )
                    )
                } else {
                    emit(null)
                }
            } else {
                emit(null)
            }
        }.onFailure {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)
}