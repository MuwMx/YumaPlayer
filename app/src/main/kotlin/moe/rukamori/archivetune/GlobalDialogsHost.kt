package moe.rukamori.archivetune

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.HasPressedStarKey
import moe.rukamori.archivetune.constants.LaunchCountKey
import moe.rukamori.archivetune.constants.RemindAfterKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.network.NetworkBannerUiState
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetMenu
import moe.rukamori.archivetune.ui.component.BottomSheetPage
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NetworkStatusBanner
import moe.rukamori.archivetune.ui.component.SineWaveLine
import moe.rukamori.archivetune.ui.component.StarDialog
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.utils.UpdateNotificationManager
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import kotlin.time.Duration.Companion.days

@Composable
fun BoxScope.GlobalDialogsHost(
    navController: NavController,
    playerConnection: PlayerConnection?,
    bottomSheetPageState: BottomSheetPageState,
    menuState: MenuState,
    networkBannerState: NetworkBannerUiState,
    pendingBackupRestoreUri: Uri?,
    onDismissBackupRestore: () -> Unit,
    splashDone: Boolean,
    shouldShowTopBar: Boolean = false,
    topInset: Dp = WindowInsets.systemBars.asPaddingValues().calculateTopPadding(),
    updateChannel: UpdateChannel = defaultUpdateChannel,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var latestVersionName by remember { mutableStateOf(BuildConfig.VERSION_NAME) }
    var latestUpdateChannel by remember { mutableStateOf(defaultUpdateChannel) }
    var latestImageUrl by remember { mutableStateOf<String?>(null) }
    val releaseNotesState = remember { mutableStateOf<String?>(null) }

    val updateSheetContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "v$latestVersionName",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SineWaveLine(
                modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                animate = true,
                animationDurationMillis = 2000,
                samples = 400,
            )
        }
        latestImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(16.dp)),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
        ) {
            val notes = releaseNotesState.value
            if (notes != null && notes.isNotBlank()) {
                MarkdownText(
                    markdown = notes,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
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
        androidx.compose.material3.Button(
            onClick = {
                bottomSheetPageState.dismiss()
                if (BuildConfig.DISTRIBUTION == "gms") {
                    navController.navigate("settings/update?autostart=1") {
                        launchSingleTop = true
                    }
                } else {
                    val releaseUrl =
                        if (latestUpdateChannel == UpdateChannel.DAILY_NIGHTLY) {
                            Updater.getLatestCanaryDownloadUrl().ifBlank {
                                "https://github.com/MuwMx/YumaCanary/releases/latest"
                            }
                        } else {
                            Updater.getLatestDownloadUrl().ifBlank {
                                "https://github.com/MuwMx/YumaPlayer/releases/latest"
                            }
                        }
                    try {
                        uriHandler.openUri(releaseUrl)
                    } catch (_: Exception) {
                        navController.navigate("settings/update") {
                            launchSingleTop = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(text = stringResource(R.string.update_text))
        }
    }

    LaunchedEffect(Unit) {
        while (playerConnection == null) {
            delay(100)
        }
        delay(500)
        if (
            BuildConfig.UPDATER_AVAILABLE &&
            System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds
        ) {
            val isCanary =
                BuildConfig.VERSION_NAME.startsWith("canary.") ||
                    BuildConfig.NIGHTLY_BUILD_HASH.isNotBlank()
            val targetChannel = if (isCanary) UpdateChannel.DAILY_NIGHTLY else UpdateChannel.STABLE
            val versionResult =
                if (targetChannel == UpdateChannel.DAILY_NIGHTLY) {
                    Updater.getLatestCanaryVersionName()
                } else {
                    Updater.getLatestVersionName()
                }
            versionResult.onSuccess {
                if (Updater.isUpdateAvailable(it, BuildConfig.VERSION_NAME)) {
                    latestUpdateChannel = targetChannel
                    latestVersionName = it
                    latestImageUrl =
                        if (targetChannel == UpdateChannel.DAILY_NIGHTLY) {
                            Updater.getLatestCanaryReleaseInfo().getOrNull()?.imageUrl
                        } else {
                            Updater.getLatestReleaseInfo().getOrNull()?.imageUrl
                        }
                }
            }
        }
        UpdateNotificationManager.checkForUpdates(context)
    }

    LaunchedEffect(latestVersionName, latestUpdateChannel, updateChannel, splashDone) {
        val isCanary =
            BuildConfig.VERSION_NAME.startsWith("canary.") ||
                BuildConfig.NIGHTLY_BUILD_HASH.isNotBlank()
        val expectedChannel = if (isCanary) UpdateChannel.DAILY_NIGHTLY else UpdateChannel.STABLE
        if (
            splashDone &&
            BuildConfig.UPDATER_AVAILABLE &&
            latestUpdateChannel == expectedChannel &&
            Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
        ) {
            val releaseNotesResult =
                if (latestUpdateChannel == UpdateChannel.DAILY_NIGHTLY) {
                    Updater.getLatestCanaryReleaseNotes()
                } else {
                    Updater.getLatestReleaseNotes()
                }
            releaseNotesResult.onSuccess {
                releaseNotesState.value = it
            }.onFailure {
                releaseNotesState.value = null
            }
            latestImageUrl =
                if (latestUpdateChannel == UpdateChannel.DAILY_NIGHTLY) {
                    Updater.getLatestCanaryReleaseInfo().getOrNull()?.imageUrl
                } else {
                    Updater.getLatestReleaseInfo().getOrNull()?.imageUrl
                }
            bottomSheetPageState.show(updateSheetContent)
        }
    }

    var showStarDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)

        val (newCount, hasPressed, remindAfter) =
            withContext(Dispatchers.IO) {
                val current = context.dataStore[LaunchCountKey] ?: 0
                val updated = current + 1
                context.dataStore.edit { prefs ->
                    prefs[LaunchCountKey] = updated
                }
                val hp = context.dataStore[HasPressedStarKey] ?: false
                val ra = context.dataStore[RemindAfterKey] ?: 3
                Triple(updated, hp, ra)
            }

        if (!hasPressed && newCount >= remindAfter) {
            var waited = 0L
            val waitStep = 500L
            val maxWait = 30_000L
            while (bottomSheetPageState.isVisible && waited < maxWait) {
                delay(waitStep)
                waited += waitStep
            }
            showStarDialog = true
        }
    }

    if (showStarDialog) {
        val deferStarPrompt: () -> Unit = {
            coroutineScope.launch {
                try {
                    val launch = withContext(Dispatchers.IO) { context.dataStore[LaunchCountKey] ?: 0 }
                    withContext(Dispatchers.IO) {
                        context.dataStore.edit { prefs ->
                            prefs[RemindAfterKey] = launch + 20
                        }
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    showStarDialog = false
                }
            }
        }

        StarDialog(
            onDismissRequest = deferStarPrompt,
            onSupport = {
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            context.dataStore.edit { prefs ->
                                prefs[HasPressedStarKey] = true
                                prefs[RemindAfterKey] = Int.MAX_VALUE
                            }
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    } finally {
                        showStarDialog = false
                    }
                }
            },
            onLater = deferStarPrompt,
        )
    }

    BottomSheetMenu(
        state = menuState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )

    BottomSheetPage(
        state = bottomSheetPageState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )

    var sharedSong by remember { mutableStateOf<SongItem?>(null) }

    sharedSong?.let { song ->
        playerConnection?.let {
            Dialog(
                onDismissRequest = { sharedSong = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        YouTubeSongMenu(
                            song = song,
                            navController = navController,
                            onDismiss = { sharedSong = null },
                        )
                    }
                }
            }
        }
    }

    NetworkStatusBanner(
        state = networkBannerState,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .padding(
                    top = if (shouldShowTopBar) topInset + AppBarHeight + 8.dp else topInset + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ).zIndex(10f),
    )

    pendingBackupRestoreUri?.let { uri ->
        BackupRestoreFromIntentDialog(
            uri = uri,
            onDismiss = onDismissBackupRestore,
        )
    }
}

@Composable
private fun BackupRestoreFromIntentDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.restore), null) },
        title = { Text(stringResource(R.string.restore_options_title)) },
        text = {
            Column {
                BackupCategory.entries.forEach { category ->
                    val isChecked = category in selected
                    val labelRes =
                        when (category) {
                            BackupCategory.LIBRARY -> R.string.backup_category_library
                            BackupCategory.ACCOUNT -> R.string.backup_category_account
                            BackupCategory.SETTINGS -> R.string.backup_category_settings
                        }
                    val descRes =
                        when (category) {
                            BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                            BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                            BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                        }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Transparent,
                        onClick = {
                            selected = if (isChecked) selected - category else selected + category
                        },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + category else selected - category
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    viewModel.restore(context, uri, selected)
                },
                enabled = selected.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.action_restore))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
