/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.SplashShapePreference

internal object AppearanceContract {
    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
    const val DEFAULT_STANDARD_REFRESH_RATE_FPS = 60f
    const val DEFAULT_REFRESH_RATE_REQUEST = 0f

    val HOME_BACKGROUND_PARALLAX_RANGE = 0.1f..1.5f
    val HOME_BACKGROUND_BRIGHTNESS_RANGE = 0.1f..1.5f

    const val PALETTE_PICKER_ROUTE = "settings/appearance/palette_picker"
    const val CANVAS_ROUTE = "settings/appearance/canvas"
    const val GRAPHICS_ROUTE = "settings/graphics"
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    MOODANDGENRES,
    LIBRARY,
}

@Immutable
data class AppearanceSettingsUiState(
    val dynamicTheme: Boolean = true,
    val randomThemeOnStartup: Boolean = false,
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDarkTheme: Boolean = false,
    val pureBlack: Boolean = false,
    val splashOverlayEnabled: Boolean = true,
    val splashShapePreference: SplashShapePreference = SplashShapePreference.LOGO,
    val fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    val customFontUri: String = "",
    val customFontName: String = "",
    val quickPicksDisplayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.HERO,
    val defaultOpenTab: NavigationTab = NavigationTab.HOME,
    val defaultChip: LibraryFilter = LibraryFilter.LIBRARY,
    val showHomeCategoryChips: Boolean = true,
    val showTagsInLibrary: Boolean = true,
    val swipeToSong: Boolean = false,
    val marqueeEnabled: Boolean = true,
)

typealias AppearanceUiState = AppearanceSettingsUiState

@Immutable
data class AppearanceSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onNavigatePalettePicker: () -> Unit = {},
    val onNavigateGraphics: () -> Unit = {},
    val onNavigateCanvas: () -> Unit = {},
    val onDynamicThemeChange: (Boolean) -> Unit = {},
    val onRandomThemeOnStartupChange: (Boolean) -> Unit = {},
    val onDarkModeChange: (DarkMode) -> Unit = {},
    val onPureBlackChange: (Boolean) -> Unit = {},
    val onSplashOverlayEnabledChange: (Boolean) -> Unit = {},
    val onSplashShapePreferenceChange: (SplashShapePreference) -> Unit = {},
    val onFontPreferenceChange: (AppFontPreference) -> Unit = {},
    val onPickCustomFont: () -> Unit = {},
    val onQuickPicksDisplayModeChange: (QuickPicksDisplayMode) -> Unit = {},
    val onDefaultOpenTabChange: (NavigationTab) -> Unit = {},
    val onDefaultChipChange: (LibraryFilter) -> Unit = {},
    val onShowHomeCategoryChipsChange: (Boolean) -> Unit = {},
    val onShowTagsInLibraryChange: (Boolean) -> Unit = {},
    val onSwipeToSongChange: (Boolean) -> Unit = {},
    val onMarqueeEnabledChange: (Boolean) -> Unit = {},
)

typealias AppearanceUiActions = AppearanceSettingsUiActions
