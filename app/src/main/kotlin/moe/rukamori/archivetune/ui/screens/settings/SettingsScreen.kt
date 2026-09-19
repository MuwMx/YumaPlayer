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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    updateState: UpdateState,
    onClearUpdateBadge: () -> Unit = {},
) {

    val hasUpdate = updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate

    val latestVersionName = when (updateState) {
        is UpdateState.SoftUpdate -> updateState.versionName
        is UpdateState.CriticalUpdate -> updateState.versionName
        else -> BuildConfig.VERSION_NAME
    }


    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scrollState = rememberScrollState()

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted = result[notificationPermission] == true || isNotificationGranted
            }
        }

    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    var isUpdateDismissed by remember { mutableStateOf(false) }
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)

    SettingsScreenBackground {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box {
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                val offset = scrollState.value
                                val alpha = (offset / 100f).coerceIn(0f, 0.85f)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            surfaceColor.copy(alpha = alpha),
                                            Color.Transparent,
                                        ),
                                    ),
                                )
                            },
                    )
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.settings),
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = navController::navigateUp,
                                onLongClick = navController::backToMain,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_back),
                                    contentDescription = stringResource(R.string.back_button_desc),
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .verticalScroll(scrollState)
                    .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            if (hasUpdate && !isUpdateDismissed) {
                SettingsUpdateBanner(
                    latestVersion = latestVersionName,
                    onClick = {
                        if (BuildConfig.DISTRIBUTION == "gms") {
                            navController.navigate("settings/update")
                        } else {
                            val releaseUrl = Updater.getLatestDownloadUrl().ifBlank {
                                "https://github.com/MuwMx/YumaPlayer/releases/latest"
                            }
                            try {
                                uriHandler.openUri(releaseUrl)
                            } catch (_: Exception) {
                                navController.navigate("settings/update")
                            }
                        }
                    },
                    onDismiss = { isUpdateDismissed = true },
                    modifier =
                        Modifier
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .padding(bottom = SettingsDimensions.SectionSpacing),
                )
            }

            if (shouldShowPermissionHint) {
                SettingsPermissionBanner(
                    onRequestPermission = {
                        val toRequest =
                            buildList {
                                if (!isStorageGranted) add(storagePermission)
                                if (!isNotificationGranted && notificationPermission != null) {
                                    add(notificationPermission)
                                }
                            }
                        if (toRequest.isNotEmpty()) {
                            permissionLauncher.launch(toRequest.toTypedArray())
                        }
                    },
                    modifier =
                        Modifier
                            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                            .padding(bottom = SettingsDimensions.SectionSpacing),
                )
            }

            settingsGroups.forEachIndexed { groupIndex, group ->
                SettingsSectionLabel(
                    text = group.title,
                    modifier = Modifier
                        .padding(
                            top = if (groupIndex == 0) 0.dp else SettingsDimensions.SectionSpacing,
                            bottom = SettingsDimensions.SectionHeaderBottomPadding
                        )
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                )

                group.items.forEachIndexed { index, settingsItem ->
                    SettingsSegmentedItem(
                        item = settingsItem,
                        index = index,
                        count = group.items.size,
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.SegmentedGroupHorizontalPadding)
                                .padding(
                                    bottom =
                                        if (index < group.items.lastIndex) {
                                            SettingsDimensions.SegmentedItemGap
                                        } else {
                                            0.dp
                                        },
                                ),
                    )
                }
            }
        }
    }
}
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    TestThemeWrapper {
        SettingsScreen(
            navController = androidx.navigation.compose.rememberNavController(),
            updateState = UpdateState.NoUpdate,
            onClearUpdateBadge = {}
        )
    }
}