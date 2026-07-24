package moe.rukamori.archivetune.domain.repository

import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.models.AppUpdateInfo
import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    fun checkForUpdates(channel: UpdateChannel): Flow<AppUpdateInfo?>
    fun forceCheckForUpdates(channel: UpdateChannel): Flow<AppUpdateInfo?>
}