/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.utils.AppUpdateInstaller

@Composable
internal fun UpdateScreenDialogsHost(
    enableNotificationDialog: MutableState<Boolean>,
    onConfirmEnableNotification: () -> Unit,
    nightlyChannelDialog: MutableState<Boolean>,
    onConfirmNightly: () -> Unit,
    dailyNightlyChannelDialog: MutableState<Boolean>,
    onConfirmDailyNightly: () -> Unit,
    showCheckingDialog: Boolean,
    downloadDialog: MutableState<Boolean>,
    onCancelDownload: () -> Unit,
    updateChannel: UpdateChannel,
    latestCommitSha: String?,
    downloadVersion: String?,
    downloadProgress: Float?,
    upToDateDialog: MutableState<Boolean>,
    errorDialog: MutableState<Boolean>,
    errorMessage: String?,
) {
    if (enableNotificationDialog.value) {
        EnableUpdateNotificationConfirmDialog(
            onConfirm = { enableNotificationDialog.value = false; onConfirmEnableNotification() },
            onDismiss = { enableNotificationDialog.value = false },
        )
    }
    if (nightlyChannelDialog.value) {
        NightlyChannelConfirmDialog(
            onConfirm = { nightlyChannelDialog.value = false; onConfirmNightly() },
            onDismiss = { nightlyChannelDialog.value = false },
        )
    }
    if (dailyNightlyChannelDialog.value) {
        DailyNightlyChannelConfirmDialog(
            onConfirm = { dailyNightlyChannelDialog.value = false; onConfirmDailyNightly() },
            onDismiss = { dailyNightlyChannelDialog.value = false },
        )
    }
    if (showCheckingDialog) UpdateCheckingDialog()
    if (downloadDialog.value) {
        UpdateDownloadDialog(
            updateChannel = updateChannel, latestCommitSha = latestCommitSha, version = downloadVersion, progress = downloadProgress,
            onCancel = { downloadDialog.value = false; onCancelDownload() },
        )
    }
    if (upToDateDialog.value) {
        UpdateUpToDateDialog(version = downloadVersion, onDismiss = { upToDateDialog.value = false })
    }
    if (errorDialog.value) {
        UpdateErrorDialog(errorMessage = errorMessage, onDismiss = { errorDialog.value = false })
    }
}

internal fun CoroutineScope.launchUpdateDownload(
    context: Context, url: String, onProgress: (Float?) -> Unit, onSuccess: () -> Unit, onFailure: (String) -> Unit,
): Job = launch {
    AppUpdateInstaller.downloadAndInstall(context, url) { onProgress(it.fraction) }
        .onSuccess { onSuccess() }
        .onFailure { onFailure(it.message ?: context.getString(R.string.error_unknown)) }
}
