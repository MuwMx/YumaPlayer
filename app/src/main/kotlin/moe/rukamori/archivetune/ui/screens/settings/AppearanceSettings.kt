/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.ChipSortTypeKey
import moe.rukamori.archivetune.constants.CustomFontNameKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.MarqueeEnabledKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.QuickPicksDisplayModeKey
import moe.rukamori.archivetune.constants.RandomThemeOnStartupKey
import moe.rukamori.archivetune.constants.ShowHomeCategoryChipsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.constants.SplashShapePreference
import moe.rukamori.archivetune.constants.SplashShapePreferenceKey
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.ui.theme.CustomFontLoader
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
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
    val (splashOverlayEnabled, onSplashOverlayEnabledChange) =
        rememberPreference(
            SplashOverlayEnabledKey,
            defaultValue = true,
        )
    val (splashShapePreference, onSplashShapePreferenceChange) =
        rememberEnumPreference(
            SplashShapePreferenceKey,
            defaultValue = SplashShapePreference.LOGO,
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
            defaultValue = QuickPicksDisplayMode.HERO,
        )
    val (marqueeEnabled, onMarqueeEnabledChange) =
        rememberPreference(
            MarqueeEnabledKey,
            defaultValue = true,
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

    val state =
        AppearanceUiState(
            dynamicTheme = dynamicTheme,
            randomThemeOnStartup = randomThemeOnStartup,
            darkMode = darkMode,
            useDarkTheme = useDarkTheme,
            pureBlack = pureBlack,
            splashOverlayEnabled = splashOverlayEnabled,
            splashShapePreference = splashShapePreference,
            fontPreference = fontPreference,
            customFontUri = customFontUri,
            customFontName = customFontName,
            quickPicksDisplayMode = quickPicksDisplayMode,
            defaultOpenTab = defaultOpenTab,
            defaultChip = defaultChip,
            showHomeCategoryChips = showHomeCategoryChips,
            showTagsInLibrary = showTagsInLibrary,
            swipeToSong = swipeToSong,
            marqueeEnabled = marqueeEnabled,
        )

    val actions =
        remember(navController) {
            AppearanceUiActions(
                onNavigateUp = navController::navigateUp,
                onNavigateHome = navController::backToMain,
                onNavigatePalettePicker = { navController.navigate(AppearanceContract.PALETTE_PICKER_ROUTE) },
                onNavigateGraphics = { navController.navigate(AppearanceContract.GRAPHICS_ROUTE) },
                onDynamicThemeChange = onDynamicThemeChange,
                onRandomThemeOnStartupChange = onRandomThemeOnStartupChange,
                onDarkModeChange = onDarkModeChange,
                onPureBlackChange = onPureBlackChange,
                onSplashOverlayEnabledChange = onSplashOverlayEnabledChange,
                onSplashShapePreferenceChange = onSplashShapePreferenceChange,
                onNavigateCanvas = { navController.navigate(AppearanceContract.CANVAS_ROUTE) },
                onFontPreferenceChange = onFontPreferenceChange,
                onPickCustomFont = pickCustomFont,
                onQuickPicksDisplayModeChange = onQuickPicksDisplayModeChange,
                onDefaultOpenTabChange = onDefaultOpenTabChange,
                onDefaultChipChange = onDefaultChipChange,
                onShowHomeCategoryChipsChange = onShowHomeCategoryChipsChange,
                onShowTagsInLibraryChange = onShowTagsInLibraryChange,
                onSwipeToSongChange = onSwipeToSongChange,
                onMarqueeEnabledChange = onMarqueeEnabledChange,
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
    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.appearance)) },
        onBackClick = actions.onNavigateUp,
        onBackLongClick = actions.onNavigateHome,
        modifier = modifier,
        scrollable = false,
    ) {
        AppearanceSettingsContent(
            state = state,
            actions = actions,
        )
    }
}

@ThemePreviews
@Composable
private fun AppearanceSettingsPreview() {
    TestThemeWrapper {
        AppearanceSettings(navController = rememberNavController())
    }
}
