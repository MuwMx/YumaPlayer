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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    updateState: UpdateState,
    onClearUpdateBadge: () -> Unit = {},
) {

    // Вычисляем hasUpdate из нового стейта
    val hasUpdate = updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate

    // Для бейджа и текста можно вытащить версию так:
    val latestVersionName = when (updateState) {
        is UpdateState.SoftUpdate -> updateState.versionName
        is UpdateState.CriticalUpdate -> updateState.versionName
        else -> BuildConfig.VERSION_NAME
    }


    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()

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

    val scrimAlpha by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val index = listState.firstVisibleItemIndex
            if (index > 0) 0.85f else (offset / 100f).coerceIn(0f, 0.85f)
        }
    }

    val primaryAccent = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgTopColor = remember(primaryAccent, surfaceColor) {
        primaryAccent.copy(alpha = 0.22f).compositeOver(surfaceColor)
    }
    val bgMidColor = remember(primaryAccent, surfaceColor) {
        primaryAccent.copy(alpha = 0.06f).compositeOver(surfaceColor)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgTopColor,
                        bgMidColor,
                        surfaceColor
                    )
                )
            )
    ) {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = scrimAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
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
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            /* verticalArrangement = Arrangement.spacedBy(2.dp), */
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            if (hasUpdate && !isUpdateDismissed) {
                item(key = "update", contentType = "settings_banner") {
                    SettingsUpdateBanner(
                        latestVersion = latestVersionName,
                        onClick = { navController.navigate("settings/update") },
                        onDismiss = { isUpdateDismissed = true },
                        modifier =
                            Modifier
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                .padding(bottom = SettingsDimensions.SectionSpacing),
                    )
                }
            }

            if (shouldShowPermissionHint) {
                item(key = "permission", contentType = "settings_banner") {
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
            }

            itemsIndexed(
                items = settingsGroups,
                key = { _, group -> group.title },
                contentType = { _, _ -> "settings_group" },
            ) { _, group ->
                SettingsGroupCard(
                    group = group,
                    modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                )
            }
        }
    }
}
}
