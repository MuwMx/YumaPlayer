package moe.rukamori.archivetune

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.RandomHomeBackgroundOnStartupKey
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.screens.onboarding.OnboardingRoute
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun YumaApp(
    activity: ComponentActivity,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    playerConnection: PlayerConnection?,
    systemBarController: SystemBarController,
    playerViewModel: PlayerViewModel,
    onboardingViewModel: OnboardingViewModel,
    pendingIntent: Intent?,
    onClearPendingIntent: () -> Unit,
    pendingBackupRestoreUri: Uri?,
    onClearPendingBackupRestoreUri: () -> Unit,
    aodModeLaunchRequestCount: Int,
    onResetAodLaunchRequestCount: () -> Unit,
    onNavControllerCreated: (NavHostController) -> Unit,
    onHandleIntent: (Intent?, NavHostController) -> Unit,
    isOnboardingCompleted: Boolean?,
    isReady: Boolean,
    onReadyChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    DisposableEffect(navController) {
        onNavControllerCreated(navController)
        onDispose {}
    }

    var playerExpansionFractionProvider by remember { mutableStateOf<() -> Float>({ 0f }) }
    val isHomeScreenVisible by remember {
        derivedStateOf { playerExpansionFractionProvider() < 0.99f }
    }

    val updateChannel by rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)

    val bottomSheetPageState = remember { BottomSheetPageState() }
    val menuState = remember { MenuState() }

    val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val defaultDisableAnimations = remember(activity) { activity.applicationContext.isLowRamDevice() }
    val disableAnimations by rememberPreference(
        DisableAnimationsKey,
        defaultValue = defaultDisableAnimations,
    )
    val splashState =
        rememberAppSplashState(
            activity = activity,
            isReady = isReady,
            disableAnimations = disableAnimations,
            onReadyChange = onReadyChange,
        )
    val homeBackgroundStyle by
    rememberEnumPreference(
        HomeBackgroundStyleKey,
        HomeBackgroundStyle.TONAL,
    )

    val randomHomeBackgroundOnStartup by
    rememberPreference(
        RandomHomeBackgroundOnStartupKey,
        defaultValue = false,
    )

    val effectiveHomeBackgroundStyle =
        remember(randomHomeBackgroundOnStartup) {
            if (randomHomeBackgroundOnStartup) {
                HomeBackgroundStyle.entries.random()
            } else {
                homeBackgroundStyle
            }
        }

    val homeBackgroundParallaxEnabled by
    rememberPreference(
        HomeBackgroundParallaxEnabledKey,
        defaultValue = true,
    )
    val homeBackgroundParallaxStrength by rememberPreference(HomeBackgroundParallaxStrengthKey, defaultValue = 0.6f)
    val homeBackgroundBrightness by rememberPreference(HomeBackgroundBrightnessKey, defaultValue = 1f)
    val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
    val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
    val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val pureBlack = pureBlackEnabled && useDarkTheme

    val dynamicThemeColorState =
        rememberDynamicThemeColor(
            activity = activity,
            playerConnection = playerConnection,
            enableDynamicTheme = enableDynamicTheme,
            customThemeColorValue = customThemeColorValue,
            isSystemInDarkTheme = isSystemInDarkTheme,
        )

    LaunchedEffect(legacyUseSystemFont) {
        if (!legacyUseSystemFont) return@LaunchedEffect
        val preferences = activity.dataStore.data.first()
        if (preferences[FontPreferenceKey] == null) {
            activity.dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
        }
    }

    ArchiveTuneTheme(
        darkTheme = useDarkTheme,
        pureBlack = pureBlack,
        themeColor = dynamicThemeColorState.themeColor,
        seedPalette = if (!enableDynamicTheme) dynamicThemeColorState.seedPalette else null,
        disableAnimations = disableAnimations,
        fontPreference = fontPreference,
        customFontUri = customFontUri,
    ) {
        if (isOnboardingCompleted == null || !isReady) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return@ArchiveTuneTheme
        }

        if (isOnboardingCompleted == false || activity.intent.getBooleanExtra("force_onboarding", false)) {
            OnboardingRoute(viewModel = onboardingViewModel)
            return@ArchiveTuneTheme
        }

        ScaffoldShell(
            activity = activity,
            navController = navController,
            database = database,
            downloadUtil = downloadUtil,
            syncUtils = syncUtils,
            playerConnection = playerConnection,
            systemBarController = systemBarController,
            playerViewModel = playerViewModel,
            bottomSheetPageState = bottomSheetPageState,
            menuState = menuState,
            updateChannel = updateChannel,
            isHomeScreenVisible = isHomeScreenVisible,
            onExpansionFraction = { playerExpansionFractionProvider = it },
            disableAnimations = disableAnimations,
            splashEnabled = splashState.splashEnabled,
            useDarkTheme = useDarkTheme,
            pureBlack = pureBlack,
            homeBackgroundStyle = effectiveHomeBackgroundStyle,
            homeBackgroundParallaxEnabled = homeBackgroundParallaxEnabled,
            homeBackgroundParallaxStrength = homeBackgroundParallaxStrength,
            homeBackgroundBrightness = homeBackgroundBrightness,
            contentAlpha = splashState.contentAlpha,
            contentVisible = splashState.contentVisible,
            coldSplash = splashState.coldSplash,
            onBurstStart = splashState.onBurstStart,
            splashDone = splashState.splashDone,
            onSplashDismiss = splashState.onSplashDismiss,
            pendingIntent = pendingIntent,
            onClearPendingIntent = onClearPendingIntent,
            pendingBackupRestoreUri = pendingBackupRestoreUri,
            onClearPendingBackupRestoreUri = onClearPendingBackupRestoreUri,
            aodModeLaunchRequestCount = aodModeLaunchRequestCount,
            onResetAodLaunchRequestCount = onResetAodLaunchRequestCount,
            onHandleIntent = onHandleIntent,
        )
    }
}
