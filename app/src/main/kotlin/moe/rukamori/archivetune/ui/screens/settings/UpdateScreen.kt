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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.GitCommit
import moe.rukamori.archivetune.utils.UpdateNotificationManager
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    onUpToDate: () -> Unit = {},
    autostart: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = appBarScrollBehavior()
    val coroutineScope = rememberCoroutineScope()

    val (enableUpdateNotification, onEnableUpdateNotificationChange) =
        rememberPreference(
            EnableUpdateNotificationKey,
            defaultValue = false,
        )
    val (updateChannel, onUpdateChannelChange) =
        rememberEnumPreference(
            UpdateChannelKey,
            defaultValue = defaultUpdateChannel,
        )

    var commits by remember { mutableStateOf<List<GitCommit>>(emptyList()) }
    var isLoadingCommits by remember { mutableStateOf(true) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var latestImageUrl by remember { mutableStateOf<String?>(null) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var showNightlyChannelConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showDailyNightlyChannelConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showEnableUpdateNotificationConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    val isNightlyChannel = updateChannel == UpdateChannel.NIGHTLY
    val isUpdateAvailable by remember(latestVersion) {
        derivedStateOf {
            BuildConfig.UPDATER_AVAILABLE &&
                (latestVersion?.let { Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME) } ?: false)
        }
    }
    val latestCommit by remember(commits) {
        derivedStateOf { commits.firstOrNull() }
    }

    val updateSheetState = remember { BottomSheetPageState() }
    var updateSheetLoading by remember { mutableStateOf(false) }
    var updateSheetVersion by remember { mutableStateOf<String?>(null) }
    var updateSheetNotes by remember { mutableStateOf<String?>(null) }
    var updateSheetError by remember { mutableStateOf<String?>(null) }
    var updateSheetIsSameVersion by remember { mutableStateOf(false) }
    var showUpdateUpToDateDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var updateDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var updateDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showUpdateDownloadDialog by remember { mutableStateOf(false) }
    val useInAppUpdateInstaller = BuildConfig.DISTRIBUTION == "gms"
    val snackbarHostState = remember { SnackbarHostState() }

    val openUpdateUrl: (String) -> Unit = { url ->
        if (url.isBlank()) {
            updateSheetError = context.getString(R.string.error_unknown)
            showUpdateErrorDialog = true
        } else {
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                val errorMessage = e.message ?: context.getString(R.string.error_unknown)
                updateSheetError = errorMessage
                showUpdateErrorDialog = true
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            }
        }
    }

    val installUpdate: (String) -> Unit = { url ->
        if (url.isBlank()) {
            updateSheetError = "Update download URL unavailable"
            showUpdateErrorDialog = true
        } else if (!useInAppUpdateInstaller) {
            openUpdateUrl(url)
        } else if (updateDownloadJob?.isActive != true) {
            updateDownloadProgress = null
            updateSheetError = null
            showUpdateErrorDialog = false
            showUpdateDownloadDialog = true
            updateDownloadJob =
                coroutineScope.launch {
                    AppUpdateInstaller
                        .downloadAndInstall(context, url) { progress ->
                            updateDownloadProgress = progress.fraction
                        }.onSuccess {
                            showUpdateDownloadDialog = false
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.download_complete),
                            )
                        }.onFailure { error ->
                            showUpdateDownloadDialog = false
                            updateSheetError = error.message ?: context.getString(R.string.error_unknown)
                            showUpdateErrorDialog = true
                        }
                }
        }
    }

    val downloadUrl = remember(updateChannel, latestVersion) {
        when (updateChannel) {
            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryDownloadUrl()
            else -> Updater.getLatestDownloadUrl()
        }
    }

    val shouldAutostart = autostart || remember(navController) {
        val args = navController.currentBackStackEntry?.arguments
        val raw = args?.getString("autostart") ?: args?.getString("download")
        raw == "1" || raw.equals("true", ignoreCase = true)
    }

    var hasAutostarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shouldAutostart, downloadUrl) {
        if (shouldAutostart && !hasAutostarted && downloadUrl.isNotBlank()) {
            hasAutostarted = true
            installUpdate(downloadUrl)
        }
    }

    val updateSheetContent: @Composable ColumnScope.() -> Unit = {
        UpdateReleaseSheetContent(
            version = updateSheetVersion,
            notes = updateSheetNotes,
            useInAppUpdateInstaller = useInAppUpdateInstaller,
            onDownloadOrInstall = {
                if (!useInAppUpdateInstaller) {
                    openUpdateUrl(downloadUrl)
                } else {
                    installUpdate(downloadUrl)
                }
            },
        )
    }

    val handleCheckForUpdate: () -> Unit = {
        run {
            if (isCheckingForUpdate || !BuildConfig.UPDATER_AVAILABLE) return@run
            coroutineScope.launch {
                isCheckingForUpdate = true
                updateSheetLoading = true
                updateSheetVersion = null
                updateSheetNotes = null
                updateSheetError = null
                try {
                    val releaseResult =
                        when (updateChannel) {
                            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryReleaseInfo()
                            else -> Updater.getLatestReleaseInfo(forceRefresh = true)
                        }
                    releaseResult.onSuccess { release ->
                        val version =
                            if (updateChannel == UpdateChannel.DAILY_NIGHTLY) {
                                Updater.getCanaryReleaseVersionName(release)
                            } else {
                                Updater.getReleaseVersionName(release)
                            }
                        updateSheetVersion = version
                        latestVersion = version
                        val available = Updater.isUpdateAvailable(version, BuildConfig.VERSION_NAME)
                        if (available) {
                            updateSheetNotes = release.body
                            updateSheetLoading = false
                            updateSheetIsSameVersion = false
                            updateSheetState.show(updateSheetContent)
                        } else {
                            updateSheetLoading = false
                            showUpdateUpToDateDialog = true
                        }
                    }.onFailure { error ->
                        updateSheetLoading = false
                        updateSheetError = error.message ?: context.getString(R.string.error_unknown)
                        showUpdateErrorDialog = true
                    }
                } catch (_: Exception) {
                    updateSheetLoading = false
                    updateSheetError = context.getString(R.string.error_unknown)
                    showUpdateErrorDialog = true
                }
                isCheckingForUpdate = false
            }
        }
    }



    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                onEnableUpdateNotificationChange(true)
                UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
            }
        }

    if (showEnableUpdateNotificationConfirmDialog) {
        EnableUpdateNotificationConfirmDialog(
            onConfirm = {
                showEnableUpdateNotificationConfirmDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onEnableUpdateNotificationChange(true)
                    UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
                }
            },
            onDismiss = { showEnableUpdateNotificationConfirmDialog = false },
        )
    }

    if (showNightlyChannelConfirmDialog) {
        NightlyChannelConfirmDialog(
            onConfirm = {
                showNightlyChannelConfirmDialog = false
                onUpdateChannelChange(UpdateChannel.NIGHTLY)
            },
            onDismiss = { showNightlyChannelConfirmDialog = false },
        )
    }

    if (showDailyNightlyChannelConfirmDialog) {
        DailyNightlyChannelConfirmDialog(
            onConfirm = {
                showDailyNightlyChannelConfirmDialog = false
                onUpdateChannelChange(UpdateChannel.DAILY_NIGHTLY)
            },
            onDismiss = { showDailyNightlyChannelConfirmDialog = false },
        )
    }

    LaunchedEffect(updateChannel) {
        isLoadingCommits = true
        if (BuildConfig.UPDATER_AVAILABLE) {
            val releaseResult =
                when (updateChannel) {
                    UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryReleaseInfo()
                    else -> Updater.getLatestReleaseInfo()
                }
            releaseResult.onSuccess { release ->
                val version =
                    if (updateChannel == UpdateChannel.DAILY_NIGHTLY) {
                        Updater.getCanaryReleaseVersionName(release)
                    } else {
                        Updater.getReleaseVersionName(release)
                    }
                latestVersion = version
                latestImageUrl = release.imageUrl
                if (!Updater.isUpdateAvailable(version, BuildConfig.VERSION_NAME)) {
                    onUpToDate()
                }
            }.onFailure {
                val versionResult =
                    when (updateChannel) {
                        UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryVersionName()
                        else -> Updater.getLatestVersionName()
                    }
                versionResult.onSuccess {
                    latestVersion = it
                    if (!Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME)) {
                        onUpToDate()
                    }
                }
            }
        }

        Updater
            .getCommitHistory(30)
            .onSuccess {
                commits = it
            }.onFailure {
                commits = emptyList()
            }
        isLoadingCommits = false
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotation",
    )
    val topBarSubtitle =
        when (updateChannel) {
            UpdateChannel.NIGHTLY -> stringResource(R.string.updates_subtitle_nightly)
            else -> stringResource(R.string.updates_subtitle_stable)
        }

    YumaSettingsScaffold(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.updates),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = topBarSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onBackClick = navController::navigateUp,
        onBackLongClick = navController::backToMain,
        scrollable = false,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                val isDownloadReady = !isCheckingForUpdate && downloadUrl.isNotBlank() && (isUpdateAvailable || latestVersion != null)
                UpdateSummaryCard(
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = latestVersion,
                    updateChannel = updateChannel,
                    isUpdateAvailable = isUpdateAvailable,
                    isCheckingForUpdate = isCheckingForUpdate,
                    imageUrl = latestImageUrl,
                    isDownloadReady = isDownloadReady,
                    onDownloadApk = {
                        if (!useInAppUpdateInstaller) {
                            openUpdateUrl(downloadUrl)
                        } else {
                            installUpdate(downloadUrl)
                        }
                    },
                    onCheckForUpdate = handleCheckForUpdate,
                    onOpenChangelog = {
                        navController.navigate("settings/changelog?channel=$updateChannel")
                    },
                    onOpenAllReleases = {
                        openUpdateUrl("https://github.com/MuwMx/YumaPlayer/releases")
                    },
                )
            }

            item {
                SwitchPreference(
                    title = { Text(text = stringResource(R.string.enable_update_notification)) },
                    description = stringResource(R.string.enable_update_notification_desc),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.new_release),
                            contentDescription = null,
                        )
                    },
                    checked = enableUpdateNotification,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showEnableUpdateNotificationConfirmDialog = true
                        } else {
                            onEnableUpdateNotificationChange(false)
                            UpdateNotificationManager.cancelPeriodicUpdateCheck(context)
                        }
                    },
                )
            }

            item {
                UpdateChannelPanel(
                    updateChannel = updateChannel,
                    onStableSelected = { onUpdateChannelChange(UpdateChannel.STABLE) },
                    onCanarySelected = {
                        if (updateChannel != UpdateChannel.DAILY_NIGHTLY) {
                            showDailyNightlyChannelConfirmDialog = true
                        }
                    },
                )
            }

            item {
                AnimatedVisibility(visible = isNightlyChannel) {
                    NightlyInstallPanel(
                        latestCommit = latestCommit,
                        onInstallNightly = { installUpdate(Updater.getLatestDownloadUrl()) },
                    )
                }
            }

            item {
                CommitHistorySection(
                    commits = commits,
                    isLoading = isLoadingCommits,
                    isExpanded = isExpanded,
                    rotationAngle = rotationAngle,
                    onToggleExpanded = { isExpanded = !isExpanded },
                    onCommitClick = { commit -> uriHandler.openUri(commit.url) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetPage(
            state = updateSheetState,
            modifier = Modifier.align(Alignment.BottomCenter),
            contentWindowInsets = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        )
    }

    if (updateSheetLoading) {
        UpdateCheckingDialog()
    }

    if (showUpdateDownloadDialog) {
        UpdateDownloadDialog(
            updateChannel = updateChannel,
            latestCommitSha = latestCommit?.sha,
            version = updateSheetVersion,
            progress = updateDownloadProgress,
            onCancel = {
                updateDownloadJob?.cancel()
                updateDownloadJob = null
                updateDownloadProgress = null
                showUpdateDownloadDialog = false
            },
        )
    }

    if (showUpdateUpToDateDialog) {
        UpdateUpToDateDialog(
            version = updateSheetVersion,
            onDismiss = { showUpdateUpToDateDialog = false },
        )
    }

    if (showUpdateErrorDialog) {
        UpdateErrorDialog(
            errorMessage = updateSheetError,
            onDismiss = { showUpdateErrorDialog = false },
        )
    }
}

@ThemePreviews
@Composable
private fun UpdateScreenPreview() {
    TestThemeWrapper {
        UpdateScreen(navController = rememberNavController())
    }
}
