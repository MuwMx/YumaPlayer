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
            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryReleaseInfo()
            else -> Updater.getLatestReleaseInfo()
        }

        result.onSuccess { release ->
            val versionName =
                if (channel == UpdateChannel.DAILY_NIGHTLY) {
                    Updater.getCanaryReleaseVersionName(release)
                } else {
                    Updater.getReleaseVersionName(release)
                }
            val currentVersion = BuildConfig.VERSION_NAME
            if (Updater.isUpdateAvailable(versionName, currentVersion)) {
                val downloadUrl = when (channel) {
                    UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryDownloadUrl()
                    else -> Updater.getLatestDownloadUrl()
                }

                val isCritical = release.body?.contains("[CRITICAL]", ignoreCase = true) == true

                emit(
                    AppUpdateInfo(
                        versionCode = 0,
                        versionName = versionName,
                        updateUrl = downloadUrl,
                        isCritical = isCritical,
                        changelog = MarkdownCleaner.clean(release.body),
                        imageUrl = release.imageUrl
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
            UpdateChannel.DAILY_NIGHTLY -> Updater.getAllCanaryReleases()
            else -> Updater.getAllReleases(forceRefresh = true)
        }

        result.onSuccess { releases ->
            val latest = when (channel) {
                UpdateChannel.DAILY_NIGHTLY -> releases.firstOrNull()
                else -> Updater.findLatestRelease(releases)
            }

            if (latest != null) {
                val versionName =
                    if (channel == UpdateChannel.DAILY_NIGHTLY) {
                        Updater.getCanaryReleaseVersionName(latest)
                    } else {
                        Updater.getReleaseVersionName(latest)
                    }
                val currentVersion = BuildConfig.VERSION_NAME
                if (Updater.isUpdateAvailable(versionName, currentVersion)) {
                    val downloadUrl = when (channel) {
                        UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryDownloadUrl()
                        else -> Updater.getLatestDownloadUrl()
                    }

                    val isCritical = latest.body?.contains("[CRITICAL]", ignoreCase = true) == true

                    emit(
                        AppUpdateInfo(
                            versionCode = 0,
                            versionName = versionName,
                            updateUrl = downloadUrl,
                            isCritical = isCritical,
                            changelog = MarkdownCleaner.clean(latest.body),
                            imageUrl = latest.imageUrl
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