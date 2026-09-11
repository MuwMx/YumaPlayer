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
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.crossfade
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
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SegmentedPreference
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.GitCommit
import moe.rukamori.archivetune.utils.UpdateNotificationManager
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    onUpToDate: () -> Unit = {},
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
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
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

    val updateSheetContent: @Composable ColumnScope.() -> Unit = {
        Text(
            text = stringResource(R.string.new_update_available),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = updateSheetVersion ?: "",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
        ) {
            val notes = updateSheetNotes
            if (notes != null && notes.isNotBlank()) {
                MarkdownText(
                    markdown = notes,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = stringResource(R.string.release_notes_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val downloadUrl =
            when (updateChannel) {
                UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryDownloadUrl()
                else -> Updater.getLatestDownloadUrl()
            }

        Button(
            onClick = { installUpdate(downloadUrl) },
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(text = stringResource(R.string.update_text))
        }

        Spacer(Modifier.height(12.dp))
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
                    val versionResult =
                        when (updateChannel) {
                            UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryVersionName()
                            else -> Updater.getLatestVersionName()
                        }
                    versionResult.onSuccess { version ->
                        updateSheetVersion = version
                        latestVersion = version
                        val available = Updater.isUpdateAvailable(version, BuildConfig.VERSION_NAME)
                        if (available) {
                            val notesResult =
                                when (updateChannel) {
                                    UpdateChannel.DAILY_NIGHTLY -> Updater.getLatestCanaryReleaseNotes()
                                    else -> Updater.getLatestReleaseNotes()
                                }
                            updateSheetNotes = notesResult.getOrNull()
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
        AlertDialog(
            onDismissRequest = { showEnableUpdateNotificationConfirmDialog = false },
            title = { Text(stringResource(R.string.enable_update_notification)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_source),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.updates_channel_warning_nightly_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.updates_nightly_hosting_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_nightly_risk),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_unstable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_acknowledgement),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEnableUpdateNotificationConfirmDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onEnableUpdateNotificationChange(true)
                            UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableUpdateNotificationConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showNightlyChannelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNightlyChannelConfirmDialog = false },
            title = { Text(stringResource(R.string.channel_nightly)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.updates_channel_warning_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_source),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_stable_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.updates_channel_warning_nightly_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.updates_nightly_hosting_description),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_warning_nightly_risk),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text(
                        text = stringResource(R.string.updates_channel_warning_nightly_unstable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.updates_channel_warning_acknowledgement),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNightlyChannelConfirmDialog = false
                        onUpdateChannelChange(UpdateChannel.NIGHTLY)
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNightlyChannelConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showDailyNightlyChannelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDailyNightlyChannelConfirmDialog = false },
            title = { Text(stringResource(R.string.channel_daily_nightly)) },
            text = {
                Text(
                    text = stringResource(R.string.updates_daily_channel_confirmation),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDailyNightlyChannelConfirmDialog = false
                        onUpdateChannelChange(UpdateChannel.DAILY_NIGHTLY)
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDailyNightlyChannelConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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
                latestVersion = release.tagName
                latestImageUrl = release.imageUrl
                if (!Updater.isUpdateAvailable(release.tagName, BuildConfig.VERSION_NAME)) {
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
                UpdateSummaryCard(
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = latestVersion,
                    updateChannel = updateChannel,
                    isUpdateAvailable = isUpdateAvailable,
                    isCheckingForUpdate = isCheckingForUpdate,
                    imageUrl = latestImageUrl,
                    onCheckForUpdate = handleCheckForUpdate,
                    onOpenChangelog = {
                        navController.navigate("settings/changelog?channel=$updateChannel")
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
    }

    if (updateSheetLoading) {
        AlertDialog(
            onDismissRequest = {},
            icon = {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.updates_status_checking),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            confirmButton = {},
        )
    }

    if (showUpdateDownloadDialog) {
        val progress = updateDownloadProgress
        val animatedProgress by animateFloatAsState(
            targetValue = progress ?: 0f,
            animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "updateDownloadProgress",
        )
        val centeredDialogContentModifier = remember { Modifier.fillMaxWidth() }
        val determinateProgressModifier = remember { Modifier.size(96.dp) }
        val determinateIndicatorModifier = remember { Modifier.fillMaxSize() }
        val indeterminateIndicatorModifier = remember { Modifier.size(72.dp) }

        val downloadTitle =
            buildString {
                when (updateChannel) {
                    UpdateChannel.DAILY_NIGHTLY -> append("${context.getString(R.string.app_name)} Nightly")
                    else -> append(context.getString(R.string.app_name))
                }
                append(' ')
                if (updateChannel == UpdateChannel.NIGHTLY) {
                    append(latestCommit?.sha?.take(7) ?: updateSheetVersion ?: "?")
                } else {
                    append(updateSheetVersion ?: "?")
                }
            }

        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = downloadTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = centeredDialogContentModifier,
                )
            },
            text = {
                Column(
                    modifier = centeredDialogContentModifier,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (progress != null) {
                        Box(
                            modifier = determinateProgressModifier,
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = determinateIndicatorModifier,
                            )
                            Text(
                                text =
                                    stringResource(
                                        R.string.download_progress_percent,
                                        (animatedProgress * 100f).roundToInt().coerceIn(0, 100),
                                    ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        CircularWavyProgressIndicator(
                            modifier = indeterminateIndicatorModifier,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateDownloadJob?.cancel()
                        updateDownloadJob = null
                        updateDownloadProgress = null
                        showUpdateDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showUpdateUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateUpToDateDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.updates_status_current),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = updateSheetVersion ?: BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = { showUpdateUpToDateDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
        )
    }

    if (showUpdateErrorDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateErrorDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.error),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.error_loading_changelog),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = updateSheetError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpdateErrorDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateSummaryCard(
    currentVersion: String,
    latestVersion: String?,
    updateChannel: UpdateChannel,
    isUpdateAvailable: Boolean,
    isCheckingForUpdate: Boolean,
    imageUrl: String?,
    onCheckForUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val channelLabel =
        when (updateChannel) {
            UpdateChannel.STABLE -> stringResource(R.string.channel_stable)
            else -> stringResource(R.string.channel_canary)
        }
    val supportingText =
        when {
            latestVersion == null -> stringResource(R.string.updates_status_checking)
            isUpdateAvailable -> stringResource(R.string.latest_version_format, latestVersion)
            else -> stringResource(R.string.updates_status_current)
        }
    val statusContainerColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val statusContentColor =
        if (isUpdateAvailable) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

    val colors = LocalYumaColors.current
    val cardShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(16.dp)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = cardShape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .padding(SettingsDimensions.BannerContentPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!imageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageModel = remember(context, imageUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(imageShape),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeatureIcon(
                    iconRes = R.drawable.update,
                    containerColor = statusContainerColor,
                    contentColor = statusContentColor,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.current_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currentVersion,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                StatusBadgeChip(
                    text = channelLabel,
                    iconRes = R.drawable.tune,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                StatusBadgeChip(
                    text = supportingText,
                    iconRes = if (isUpdateAvailable) R.drawable.download else R.drawable.check,
                    containerColor = if (isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isCheckingForUpdate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.check_for_update),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    InteractiveChip(
                        label = stringResource(R.string.check_for_update),
                        iconResId = R.drawable.sync,
                        onClick = onCheckForUpdate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    InteractiveChip(
                        label = stringResource(R.string.view_changelog),
                        iconResId = R.drawable.update,
                        onClick = onOpenChangelog,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeChip(
    text: String,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun InteractiveChip(
    label: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .yumaClickable(pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                backgroundColor = colors.glassBackground,
                borderColor = colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateChannelPanel(
    updateChannel: UpdateChannel,
    onStableSelected: () -> Unit,
    onCanarySelected: () -> Unit,
) {
    val isCanary = updateChannel != UpdateChannel.STABLE
    PreferenceEntry(
        title = { Text(text = stringResource(R.string.update_channel)) },
        description = stringResource(R.string.update_channel_desc),
        icon = {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = null,
            )
        },
        content = {
            Spacer(Modifier.height(10.dp))
            val colors = LocalYumaColors.current
            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.glassBackground)
                    .border(SettingsDimensions.GlassBorderThickness, colors.glassBorder, barShape)
                    .padding(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChannelSelectChip(
                        label = stringResource(R.string.channel_stable),
                        isSelected = !isCanary,
                        onClick = onStableSelected,
                        modifier = Modifier.weight(1f),
                    )
                    ChannelSelectChip(
                         label = stringResource(R.string.channel_canary_soon),
                         isSelected = isCanary,
                        onClick = onCanarySelected,
                        modifier = Modifier.weight(1f),
                        enabled = false,
                    )
                }
            }
        },
    )
}

@Composable
private fun ChannelSelectChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else if (!enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier = modifier
            .yumaClickable(enabled = enabled, pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NightlyInstallPanel(
    latestCommit: GitCommit?,
    onInstallNightly: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.channel_nightly)) {
        item {
            PreferenceEntry(
                title = { Text(text = stringResource(R.string.updates_nightly_title)) },
                description = stringResource(R.string.updates_nightly_description) + "\n" +
                        stringResource(R.string.updates_latest_commit, latestCommit?.sha ?: "-"),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                    )
                },
                onClick = onInstallNightly,
            )
        }
    }
}

@Composable
private fun CommitHistorySection(
    commits: List<GitCommit>,
    isLoading: Boolean,
    isExpanded: Boolean,
    rotationAngle: Float,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
    onCommitClick: (GitCommit) -> Unit,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedListItem(
            onClick = onToggleExpanded,
            shapes =
                ListItemDefaults.shapes(
                    shape = MaterialTheme.shapes.extraLarge,
                ),
            modifier = Modifier.fillMaxWidth(),
            colors =
                ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            leadingContent = {
                FeatureIcon(
                    iconRes = R.drawable.history,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle),
                )
            },
            supportingContent = {
                Text(
                    text =
                        when {
                            isLoading -> {
                                stringResource(R.string.updates_loading_commits)
                            }

                            commits.isEmpty() -> {
                                stringResource(R.string.updates_no_commits)
                            }

                            else -> {
                                stringResource(
                                    R.string.updates_recent_commits_count,
                                    commits.size,
                                )
                            }
                        },
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.recent_commits),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )

        AnimatedVisibility(visible = isExpanded) {
            when {
                isLoading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                                Text(
                                    text = stringResource(R.string.updates_loading_commits),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                commits.isEmpty() -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_no_commits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        commits.forEachIndexed { index, commit ->
                            key(commit.sha) {
                                CommitItem(
                                    commit = commit,
                                    index = index,
                                    count = commits.size,
                                    onClick = { onCommitClick(commit) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureIcon(
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier =
                Modifier
                    .padding(12.dp)
                    .size(22.dp),
        )
    }
}

@Composable
private fun CommitItem(
    commit: GitCommit,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        leadingContent = {
            CommitAvatar(avatarUrl = commit.authorAvatarUrl)
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = commit.sha,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        if (commit.date.isNotEmpty()) {
                            commit.author + " - " + formatCommitDate(commit.date)
                        } else {
                            commit.author
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            Text(
                text = commit.message,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun CommitAvatar(avatarUrl: String?) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun formatCommitDate(isoDate: String): String =
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(isoDate)
        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        outputFormat.format(date!!)
    } catch (e: Exception) {
        isoDate.take(10)
    }

@ThemePreviews
@Composable
private fun UpdateScreenPreview() {
    TestThemeWrapper {
        UpdateScreen(navController = rememberNavController())
    }
}
