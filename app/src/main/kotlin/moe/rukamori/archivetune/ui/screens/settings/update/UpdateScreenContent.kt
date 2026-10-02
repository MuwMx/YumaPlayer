/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.GitCommit
import moe.rukamori.archivetune.utils.Updater

@Composable
internal fun UpdateScreenContent(
    navController: NavController, updateChannel: UpdateChannel, isNightlyChannel: Boolean,
    enableUpdateNotification: Boolean, onEnableNotificationCheckedChange: (Boolean) -> Unit,
    onStableSelected: () -> Unit, onCanarySelected: () -> Unit,
    latestVersion: String?, latestImageUrl: String?, isUpdateAvailable: Boolean, isCheckingForUpdate: Boolean,
    downloadUrl: String, onDownloadApk: () -> Unit, onCheckForUpdate: () -> Unit,
    onOpenChangelog: () -> Unit, onOpenAllReleases: () -> Unit,
    latestCommit: GitCommit?, onInstallNightly: () -> Unit,
    commits: List<GitCommit>, isLoadingCommits: Boolean, isExpanded: Boolean,
    rotationAngle: Float, onToggleExpanded: () -> Unit, onCommitClick: (GitCommit) -> Unit,
    sheetState: BottomSheetPageState, snackbarHostState: SnackbarHostState,
) {
    val topBarSubtitle = if (updateChannel == UpdateChannel.NIGHTLY) {
        stringResource(R.string.updates_subtitle_nightly)
    } else {
        stringResource(R.string.updates_subtitle_stable)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        YumaSettingsScaffold(
            title = {
                Column {
                    Text(text = stringResource(R.string.updates), fontWeight = FontWeight.Bold)
                    Text(text = topBarSubtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            onBackClick = navController::navigateUp,
            onBackLongClick = navController::backToMain,
            scrollable = false,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    val isDownloadReady = !isCheckingForUpdate && downloadUrl.isNotBlank() && (isUpdateAvailable || latestVersion != null)
                    UpdateSummaryCard(
                        currentVersion = BuildConfig.VERSION_NAME, latestVersion = latestVersion, updateChannel = updateChannel,
                        isUpdateAvailable = isUpdateAvailable, isCheckingForUpdate = isCheckingForUpdate, imageUrl = latestImageUrl,
                        isDownloadReady = isDownloadReady, onDownloadApk = onDownloadApk, onCheckForUpdate = onCheckForUpdate,
                        onOpenChangelog = onOpenChangelog, onOpenAllReleases = onOpenAllReleases,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(text = stringResource(R.string.enable_update_notification)) },
                        description = stringResource(R.string.enable_update_notification_desc),
                        icon = { Icon(painter = painterResource(R.drawable.new_release), contentDescription = null) },
                        checked = enableUpdateNotification, onCheckedChange = onEnableNotificationCheckedChange,
                    )
                }
                item {
                    UpdateChannelPanel(updateChannel = updateChannel, onStableSelected = onStableSelected, onCanarySelected = onCanarySelected)
                }
                item {
                    AnimatedVisibility(visible = isNightlyChannel) {
                        NightlyInstallPanel(latestCommit = latestCommit, onInstallNightly = onInstallNightly)
                    }
                }
                item {
                    CommitHistorySection(
                        commits = commits, isLoading = isLoadingCommits, isExpanded = isExpanded, rotationAngle = rotationAngle,
                        onToggleExpanded = onToggleExpanded, onCommitClick = onCommitClick, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding + playerAwareBottomInset()))
                }
            }
        }
        BottomSheetPage(
            state = sheetState, modifier = Modifier.align(Alignment.BottomCenter),
            contentWindowInsets = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
        )
        SnackbarHost(
            hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        )
    }
}

internal suspend fun fetchLatestVersionInfo(
    updateChannel: UpdateChannel, onSuccess: (version: String, imageUrl: String?) -> Unit,
) {
    if (!BuildConfig.UPDATER_AVAILABLE) return
    val result = if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getLatestCanaryReleaseInfo() else Updater.getLatestReleaseInfo()
    result.onSuccess { release ->
        val v = if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getCanaryReleaseVersionName(release) else Updater.getReleaseVersionName(release)
        onSuccess(v, release.imageUrl)
    }.onFailure {
        val vResult = if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getLatestCanaryVersionName() else Updater.getLatestVersionName()
        vResult.onSuccess { v -> onSuccess(v, null) }
    }
}

internal suspend fun checkReleaseUpdate(
    updateChannel: UpdateChannel,
    onAvailable: (version: String, notes: String?) -> Unit,
    onUpToDate: () -> Unit,
    onError: (String) -> Unit,
) {
    val result = if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getLatestCanaryReleaseInfo() else Updater.getLatestReleaseInfo(forceRefresh = true)
    result.onSuccess { release ->
        val v = if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getCanaryReleaseVersionName(release) else Updater.getReleaseVersionName(release)
        if (Updater.isUpdateAvailable(v, BuildConfig.VERSION_NAME)) onAvailable(v, release.body) else onUpToDate()
    }.onFailure { onError(it.message ?: "") }
}
