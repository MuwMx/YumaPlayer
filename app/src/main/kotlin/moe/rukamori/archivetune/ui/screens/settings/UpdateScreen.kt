/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    onUpToDate: () -> Unit = {},
    autostart: Boolean = false,
) {
    val context = LocalContext.current; val uriHandler = LocalUriHandler.current; val coroutineScope = rememberCoroutineScope()
    val (enableUpdateNotification, onEnableUpdateNotificationChange) = rememberPreference(EnableUpdateNotificationKey, defaultValue = false)
    val (updateChannel, onUpdateChannelChange) = rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)

    var commits by remember { mutableStateOf<List<GitCommit>>(emptyList()) }
    var isLoadingCommits by remember { mutableStateOf(true) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var latestImageUrl by remember { mutableStateOf<String?>(null) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }
    val showNightlyDialog = rememberSaveable { mutableStateOf(false) }
    val showDailyNightlyDialog = rememberSaveable { mutableStateOf(false) }
    val showEnableNotificationDialog = rememberSaveable { mutableStateOf(false) }
    val showDownloadDialog = remember { mutableStateOf(false) }
    val showUpToDateDialog = remember { mutableStateOf(false) }
    val showErrorDialog = remember { mutableStateOf(false) }
    val updateSheetState = remember { BottomSheetPageState() }
    var updateSheetLoading by remember { mutableStateOf(false) }
    var updateSheetVersion by remember { mutableStateOf<String?>(null) }
    var updateSheetNotes by remember { mutableStateOf<String?>(null) }
    var updateSheetError by remember { mutableStateOf<String?>(null) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
    val useInAppUpdateInstaller = BuildConfig.DISTRIBUTION == "gms"
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotificationPermission by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val isNightlyChannel = updateChannel == UpdateChannel.NIGHTLY
    val isUpdateAvailable by remember(latestVersion) {
        derivedStateOf { BuildConfig.UPDATER_AVAILABLE && (latestVersion?.let { Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME) } ?: false) }
    }
    val latestCommit by remember(commits) { derivedStateOf { commits.firstOrNull() } }

    val openUpdateUrl: (String) -> Unit = { url ->
        if (url.isBlank()) { updateSheetError = context.getString(R.string.error_unknown); showErrorDialog.value = true } else {
            runCatching { uriHandler.openUri(url) }.onFailure { e ->
                val msg = e.message ?: context.getString(R.string.error_unknown)
                updateSheetError = msg; showErrorDialog.value = true; coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val installUpdate: (String) -> Unit = { url ->
        if (url.isBlank()) { updateSheetError = "Update download URL unavailable"; showErrorDialog.value = true }
        else if (!useInAppUpdateInstaller) openUpdateUrl(url)
        else if (updateDownloadJob?.isActive != true) {
            updateDownloadProgress = null; updateSheetError = null; showErrorDialog.value = false; showDownloadDialog.value = true
            updateDownloadJob = coroutineScope.launchUpdateDownload(
                context = context, url = url, onProgress = { updateDownloadProgress = it },
                onSuccess = { showDownloadDialog.value = false; coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.download_complete)) } },
                onFailure = { showDownloadDialog.value = false; updateSheetError = it; showErrorDialog.value = true },
            )
        }
    }

    val downloadUrl = remember(updateChannel, latestVersion) {
        if (updateChannel == UpdateChannel.DAILY_NIGHTLY) Updater.getLatestCanaryDownloadUrl() else Updater.getLatestDownloadUrl()
    }
    val shouldAutostart = autostart || remember(navController) {
        navController.currentBackStackEntry?.arguments?.let { it.getString("autostart") ?: it.getString("download") }?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: false
    }
    var hasAutostarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shouldAutostart, downloadUrl) {
        if (shouldAutostart && !hasAutostarted && downloadUrl.isNotBlank()) { hasAutostarted = true; installUpdate(downloadUrl) }
    }

    val updateSheetContent: @Composable ColumnScope.() -> Unit = {
        UpdateReleaseSheetContent(
            version = updateSheetVersion, notes = updateSheetNotes, useInAppUpdateInstaller = useInAppUpdateInstaller,
            onDownloadOrInstall = { if (!useInAppUpdateInstaller) openUpdateUrl(downloadUrl) else installUpdate(downloadUrl) },
        )
    }

    val handleCheckForUpdate: () -> Unit = {
        if (!isCheckingForUpdate && BuildConfig.UPDATER_AVAILABLE) {
            coroutineScope.launch {
                isCheckingForUpdate = true; updateSheetLoading = true; updateSheetVersion = null; updateSheetNotes = null; updateSheetError = null
                runCatching {
                    checkReleaseUpdate(
                        updateChannel = updateChannel,
                        onAvailable = { v, notes -> updateSheetVersion = v; latestVersion = v; updateSheetNotes = notes; updateSheetLoading = false; updateSheetState.show(updateSheetContent) },
                        onUpToDate = { updateSheetLoading = false; showUpToDateDialog.value = true },
                        onError = { updateSheetLoading = false; updateSheetError = it.ifBlank { context.getString(R.string.error_unknown) }; showErrorDialog.value = true },
                    )
                }.onFailure { updateSheetLoading = false; updateSheetError = context.getString(R.string.error_unknown); showErrorDialog.value = true }
                isCheckingForUpdate = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) { onEnableUpdateNotificationChange(true); UpdateNotificationManager.schedulePeriodicUpdateCheck(context) }
    }

    LaunchedEffect(updateChannel) {
        isLoadingCommits = true
        fetchLatestVersionInfo(updateChannel) { v, img ->
            latestVersion = v; latestImageUrl = img
            if (!Updater.isUpdateAvailable(v, BuildConfig.VERSION_NAME)) onUpToDate()
        }
        Updater.getCommitHistory(30).onSuccess { commits = it }.onFailure { commits = emptyList() }
        isLoadingCommits = false
    }

    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rotation")

    UpdateScreenContent(
        navController = navController, updateChannel = updateChannel, isNightlyChannel = isNightlyChannel,
        enableUpdateNotification = enableUpdateNotification,
        onEnableNotificationCheckedChange = {
            if (it) showEnableNotificationDialog.value = true else {
                onEnableUpdateNotificationChange(false); UpdateNotificationManager.cancelPeriodicUpdateCheck(context)
            }
        },
        onStableSelected = { onUpdateChannelChange(UpdateChannel.STABLE) },
        onCanarySelected = { if (updateChannel != UpdateChannel.DAILY_NIGHTLY) showDailyNightlyDialog.value = true },
        latestVersion = latestVersion, latestImageUrl = latestImageUrl, isUpdateAvailable = isUpdateAvailable,
        isCheckingForUpdate = isCheckingForUpdate, downloadUrl = downloadUrl,
        onDownloadApk = { if (!useInAppUpdateInstaller) openUpdateUrl(downloadUrl) else installUpdate(downloadUrl) },
        onCheckForUpdate = handleCheckForUpdate,
        onOpenChangelog = { navController.navigate("settings/changelog?channel=$updateChannel") },
        onOpenAllReleases = { openUpdateUrl("https://github.com/MuwMx/YumaPlayer/releases") },
        latestCommit = latestCommit, onInstallNightly = { installUpdate(Updater.getLatestDownloadUrl()) },
        commits = commits, isLoadingCommits = isLoadingCommits, isExpanded = isExpanded, rotationAngle = rotationAngle,
        onToggleExpanded = { isExpanded = !isExpanded }, onCommitClick = { commit -> uriHandler.openUri(commit.url) },
        sheetState = updateSheetState, snackbarHostState = snackbarHostState,
    )

    UpdateScreenDialogsHost(
        enableNotificationDialog = showEnableNotificationDialog,
        onConfirmEnableNotification = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onEnableUpdateNotificationChange(true); UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
            }
        },
        nightlyChannelDialog = showNightlyDialog, onConfirmNightly = { onUpdateChannelChange(UpdateChannel.NIGHTLY) },
        dailyNightlyChannelDialog = showDailyNightlyDialog, onConfirmDailyNightly = { onUpdateChannelChange(UpdateChannel.DAILY_NIGHTLY) },
        showCheckingDialog = updateSheetLoading, downloadDialog = showDownloadDialog,
        onCancelDownload = { updateDownloadJob?.cancel(); updateDownloadJob = null; updateDownloadProgress = null },
        updateChannel = updateChannel, latestCommitSha = latestCommit?.sha,
        downloadVersion = updateSheetVersion, downloadProgress = updateDownloadProgress,
        upToDateDialog = showUpToDateDialog, errorDialog = showErrorDialog, errorMessage = updateSheetError,
    )
}

@ThemePreviews
@Composable
private fun UpdateScreenPreview() { TestThemeWrapper { UpdateScreen(rememberNavController()) } }
