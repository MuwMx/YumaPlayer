/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.ChipSortTypeKey
import moe.rukamori.archivetune.constants.CustomFontNameKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.QuickPicksDisplayModeKey
import moe.rukamori.archivetune.constants.RandomThemeOnStartupKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.CustomFontLoader
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (dynamicTheme, onDynamicThemeChange) =
        rememberPreference(
            DynamicThemeKey,
            defaultValue = true,
        )
    val (randomThemeOnStartup, onRandomThemeOnStartupChange) =
        rememberPreference(
            RandomThemeOnStartupKey,
            defaultValue = false,
        )
    val (darkMode, onDarkModeChange) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )
    val (pureBlack, onPureBlackChange) = rememberPreference(PureBlackKey, defaultValue = false)
    val (blurNavBar, onBlurNavBarChange) = rememberPreference(BlurNavBarKey, defaultValue = true)
    val (blurRadius, onBlurRadiusChange) =
        rememberPreference(
            BlurRadiusKey,
            defaultValue = SettingsDimensions.BlurRadiusDefault,
        )
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(
            DisableAnimationsKey,
            defaultValue = defaultDisableAnimations,
        )
    val (splashOverlayEnabled, onSplashOverlayEnabledChange) =
        rememberPreference(
            SplashOverlayEnabledKey,
            defaultValue = true,
        )
    val (archiveTuneCanvas, onArchiveTuneCanvasChange) =
        rememberPreference(
            ArchiveTuneCanvasKey,
            defaultValue = false,
        )
    val (homeBackgroundStyle, onHomeBackgroundStyleChange) =
        rememberEnumPreference(
            HomeBackgroundStyleKey,
            defaultValue = HomeBackgroundStyle.TONAL,
        )
    val (homeBackgroundParallaxEnabled, onHomeBackgroundParallaxEnabledChange) =
        rememberPreference(HomeBackgroundParallaxEnabledKey, defaultValue = true)
    val (homeBackgroundParallaxStrength, onHomeBackgroundParallaxStrengthChange) =
        rememberPreference(HomeBackgroundParallaxStrengthKey, defaultValue = 0.6f)
    val (homeBackgroundBrightness, onHomeBackgroundBrightnessChange) =
        rememberPreference(HomeBackgroundBrightnessKey, defaultValue = 1f)
    val (forceHighRefreshRate, onForceHighRefreshRateChange) =
        rememberPreference(
            ForceHighRefreshRateKey,
            defaultValue = false,
        )
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )
    val (swipeToSong, onSwipeToSongChange) =
        rememberPreference(
            SwipeToSongKey,
            defaultValue = false,
        )
    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(
            ShowTagsInLibraryKey,
            defaultValue = true,
        )
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) =
        rememberPreference(
            ShowHomeCategoryChipsKey,
            defaultValue = true,
        )
    val (quickPicksDisplayMode, onQuickPicksDisplayModeChange) =
        rememberEnumPreference(
            QuickPicksDisplayModeKey,
            defaultValue = QuickPicksDisplayMode.CARD,
        )

    val fontPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                }

                val fontName = CustomFontLoader.displayName(context, uri)
                onCustomFontUriChange(uri.toString())
                onCustomFontNameChange(fontName)
                onFontPreferenceChange(AppFontPreference.CUSTOM)
            }
        }

    val pickCustomFont = {
        try {
            fontPickerLauncher.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "font/opentype",
                    "application/x-font-ttf",
                    "application/x-font-otf",
                    "application/font-sfnt",
                    "application/octet-stream",
                ),
            )
        } catch (_: Exception) {
        }
    }

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkMode, isSystemInDarkTheme) {
            if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
        }

    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > AppearanceContract.HIGH_REFRESH_RATE_THRESHOLD_FPS

    ApplyRefreshRate(
        isEnabled = forceHighRefreshRate && isHighRefreshRateSupported,
        targetFps = supportedHighestFps,
    )

    val state =
        AppearanceUiState(
            dynamicTheme = dynamicTheme,
            randomThemeOnStartup = randomThemeOnStartup,
            darkMode = darkMode,
            useDarkTheme = useDarkTheme,
            pureBlack = pureBlack,
            blurNavBar = blurNavBar,
            blurRadius = blurRadius,
            disableAnimations = disableAnimations,
            splashOverlayEnabled = splashOverlayEnabled,
            archiveTuneCanvas = archiveTuneCanvas,
            homeBackgroundStyle = homeBackgroundStyle,
            homeBackgroundParallaxEnabled = homeBackgroundParallaxEnabled,
            homeBackgroundParallaxStrength = homeBackgroundParallaxStrength,
            homeBackgroundBrightness = homeBackgroundBrightness,
            forceHighRefreshRate = forceHighRefreshRate,
            isHighRefreshRateSupported = isHighRefreshRateSupported,
            supportedHighestFps = supportedHighestFps,
            fontPreference = fontPreference,
            customFontUri = customFontUri,
            customFontName = customFontName,
            quickPicksDisplayMode = quickPicksDisplayMode,
            defaultOpenTab = defaultOpenTab,
            defaultChip = defaultChip,
            showHomeCategoryChips = showHomeCategoryChips,
            showTagsInLibrary = showTagsInLibrary,
            swipeToSong = swipeToSong,
        )

    val actions =
        remember(navController) {
            AppearanceUiActions(
                onNavigateUp = navController::navigateUp,
                onNavigateHome = navController::backToMain,
                onNavigatePalettePicker = { navController.navigate(AppearanceContract.PALETTE_PICKER_ROUTE) },
                onDynamicThemeChange = onDynamicThemeChange,
                onRandomThemeOnStartupChange = onRandomThemeOnStartupChange,
                onDarkModeChange = onDarkModeChange,
                onPureBlackChange = onPureBlackChange,
                onBlurNavBarChange = onBlurNavBarChange,
                onBlurRadiusChange = onBlurRadiusChange,
                onDisableAnimationsChange = onDisableAnimationsChange,
                onSplashOverlayEnabledChange = onSplashOverlayEnabledChange,
                onArchiveTuneCanvasChange = onArchiveTuneCanvasChange,
                onHomeBackgroundStyleChange = onHomeBackgroundStyleChange,
                onHomeBackgroundParallaxEnabledChange = onHomeBackgroundParallaxEnabledChange,
                onHomeBackgroundParallaxStrengthChange = onHomeBackgroundParallaxStrengthChange,
                onHomeBackgroundBrightnessChange = onHomeBackgroundBrightnessChange,
                onForceHighRefreshRateChange = onForceHighRefreshRateChange,
                onFontPreferenceChange = onFontPreferenceChange,
                onPickCustomFont = pickCustomFont,
                onQuickPicksDisplayModeChange = onQuickPicksDisplayModeChange,
                onDefaultOpenTabChange = onDefaultOpenTabChange,
                onDefaultChipChange = onDefaultChipChange,
                onShowHomeCategoryChipsChange = onShowHomeCategoryChipsChange,
                onShowTagsInLibraryChange = onShowTagsInLibraryChange,
                onSwipeToSongChange = onSwipeToSongChange,
            )
        }

    AppearanceSettingsScreen(
        state = state,
        actions = actions,
    )
}

@Composable
internal fun AppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    actions: AppearanceSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    SettingsScreenBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.appearance)) },
                    navigationIcon = {
                        IconButton(
                            onClick = actions.onNavigateUp,
                            onLongClick = actions.onNavigateHome,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { innerPadding ->
            val topPadding = innerPadding.calculateTopPadding()

            AppearanceSettingsContent(
                state = state,
                actions = actions,
                modifier = Modifier
                    .padding(top = topPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun AppearanceSettingsPreview() {
    TestThemeWrapper {
        AppearanceSettings(navController = rememberNavController())
    }
}
