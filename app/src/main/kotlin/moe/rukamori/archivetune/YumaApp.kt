package moe.rukamori.archivetune

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.AppLanguageKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.constants.OnboardingCompletedKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.SYSTEM_DEFAULT
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.ui.component.splash.SplashSlots
import moe.rukamori.archivetune.ui.component.splash.SplashVectorLoader
import moe.rukamori.archivetune.ui.screens.onboarding.OnboardingRoute
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.ui.theme.extractSeedColor
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.setAppLocale
import java.util.Locale

fun setupMainActivity(
    activity: ComponentActivity,
    splashScreen: SplashScreen,
    isReady: () -> Boolean,
    isOnboardingCompleted: () -> Boolean?,
    onOnboardingCompleted: (Boolean) -> Unit,
) {
    splashScreen.setKeepOnScreenCondition {
        isOnboardingCompleted() == null || !isReady()
    }
    activity.lifecycleScope.launch {
        activity.dataStore.data
            .map { it[OnboardingCompletedKey] ?: false }
            .distinctUntilChanged()
            .collectLatest { completed ->
                onOnboardingCompleted(completed)
            }
    }
    activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val initialLocale =
            PreferenceStore
                .get(AppLanguageKey)
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
        setAppLocale(activity, initialLocale)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                activity.dataStore.data.first()[AppLanguageKey]
            }.onSuccess { lang ->
                val targetLocale =
                    lang
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()
                if (targetLocale != initialLocale) {
                    withContext(Dispatchers.Main) {
                        setAppLocale(activity, targetLocale)
                        activity.recreate()
                    }
                }
            }
        }
    }

    activity.lifecycleScope.launch(Dispatchers.IO) {
        activity.dataStore.data
            .map { it[DisableScreenshotKey] ?: false }
            .distinctUntilChanged()
            .collectLatest {
                withContext(Dispatchers.Main) {
                    if (it) {
                        activity.window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
    }

    activity.lifecycleScope.launch(Dispatchers.Default) {
        val path = SplashVectorLoader.loadPath(activity, R.drawable.about_splash)
        withContext(Dispatchers.Main) {
            SplashSlots.customVectorPath = path
            SplashSlots.vectorVersion++
        }
    }
}

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
    val splashEnabled by rememberPreference(SplashOverlayEnabledKey, defaultValue = true)
    LaunchedEffect(Unit) {
        activity.dataStore.data.first()
        snapshotFlow { splashEnabled }.first()
        onReadyChange(true)
    }
    var coldSplash by remember(isReady) { mutableStateOf(splashEnabled) }
    var contentVisible by remember(isReady) { mutableStateOf(!coldSplash || disableAnimations) }
    var splashDone by remember(isReady) { mutableStateOf(!splashEnabled || disableAnimations) }
    LaunchedEffect(contentVisible) {
        if (contentVisible && (!splashEnabled || disableAnimations)) {
            splashDone = true
        }
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (disableAnimations) 0 else SplashConfig.Reveal.DURATION_MS, easing = EaseOut),
        label = "splashContentAlpha",
    )
    val homeBackgroundStyle by rememberEnumPreference(HomeBackgroundStyleKey, HomeBackgroundStyle.TONAL)
    val homeBackgroundParallaxEnabled by rememberPreference(HomeBackgroundParallaxEnabledKey, defaultValue = true)
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

    val customThemeSeedPalette =
        remember(customThemeColorValue) {
            if (customThemeColorValue.startsWith("seedPalette:")) {
                ThemeSeedPaletteCodec.decodeFromPreference(customThemeColorValue)
            } else {
                null
            }
        }

    val customThemeColor =
        remember(customThemeColorValue, customThemeSeedPalette) {
            if (customThemeColorValue.startsWith("#")) {
                try {
                    val colorString = customThemeColorValue.removePrefix("#")
                    Color(android.graphics.Color.parseColor("#$colorString"))
                } catch (e: Exception) {
                    DefaultThemeColor
                }
            } else {
                customThemeSeedPalette?.primary ?: DefaultThemeColor
            }
        }

    var themeColor by rememberSaveable(stateSaver = ColorSaver) {
        mutableStateOf(DefaultThemeColor)
    }

    LaunchedEffect(legacyUseSystemFont) {
        if (!legacyUseSystemFont) return@LaunchedEffect
        val preferences = activity.dataStore.data.first()
        if (preferences[FontPreferenceKey] == null) {
            activity.dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
        }
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
        val playerConnection = playerConnection
        if (!enableDynamicTheme || playerConnection == null) {
            themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
            return@LaunchedEffect
        }
        playerConnection.service.currentMediaMetadata.collectLatest { song ->
            if (song != null) {
                withContext(Dispatchers.Default) {
                    try {
                        val result =
                            activity.imageLoader.execute(
                                ImageRequest
                                    .Builder(activity.applicationContext)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .build(),
                            )
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val accurateSeed = extractSeedColor(bitmap)
                            withContext(Dispatchers.Main) {
                                themeColor = accurateSeed
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                themeColor = DefaultThemeColor
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            themeColor = DefaultThemeColor
                        }
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    themeColor = DefaultThemeColor
                } else {
                    themeColor = customThemeColor
                }
            }
        }
    }

    ArchiveTuneTheme(
        darkTheme = useDarkTheme,
        pureBlack = pureBlack,
        themeColor = themeColor,
        seedPalette = if (!enableDynamicTheme) customThemeSeedPalette else null,
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
            splashEnabled = splashEnabled,
            useDarkTheme = useDarkTheme,
            pureBlack = pureBlack,
            homeBackgroundStyle = homeBackgroundStyle,
            homeBackgroundParallaxEnabled = homeBackgroundParallaxEnabled,
            homeBackgroundParallaxStrength = homeBackgroundParallaxStrength,
            homeBackgroundBrightness = homeBackgroundBrightness,
            contentAlpha = contentAlpha,
            contentVisible = contentVisible,
            coldSplash = coldSplash,
            onBurstStart = {
                contentVisible = true
                coldSplash = false
            },
            splashDone = splashDone,
            onSplashDismiss = { splashDone = true },
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
