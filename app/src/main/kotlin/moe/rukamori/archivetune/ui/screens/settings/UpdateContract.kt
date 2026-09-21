/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.utils.GitCommit

internal object UpdateContract {
    const val GITHUB_RELEASES_URL = "https://github.com/MuwMx/YumaPlayer/releases"
    const val CHANGELOG_ROUTE_PREFIX = "settings/changelog?channel="
    const val AUTOSTART_ARG_KEY = "autostart"
    const val DOWNLOAD_ARG_KEY = "download"

    val UpdateNotificationKey = EnableUpdateNotificationKey
    val ChannelKey = UpdateChannelKey
    val DefaultChannel = defaultUpdateChannel
}

@Immutable
data class UpdateUiState(
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val latestImageUrl: String? = null,
    val updateChannel: UpdateChannel = defaultUpdateChannel,
    val isUpdateAvailable: Boolean = false,
    val isCheckingForUpdate: Boolean = false,
    val isDownloadReady: Boolean = false,
    val enableUpdateNotification: Boolean = false,
    val commits: List<GitCommit> = emptyList(),
    val isLoadingCommits: Boolean = true,
    val isExpanded: Boolean = true,
    val hasNotificationPermission: Boolean = false,
)

@Immutable
data class UpdateUiActions(
    val onDownloadApk: () -> Unit = {},
    val onCheckForUpdate: () -> Unit = {},
    val onOpenChangelog: () -> Unit = {},
    val onOpenAllReleases: () -> Unit = {},
    val onStableChannelSelected: () -> Unit = {},
    val onCanaryChannelSelected: () -> Unit = {},
    val onInstallNightly: () -> Unit = {},
    val onToggleExpanded: () -> Unit = {},
    val onCommitClick: (GitCommit) -> Unit = {},
    val onEnableUpdateNotificationChange: (Boolean) -> Unit = {},
)
