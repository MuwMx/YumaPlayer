/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
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

internal object AppearanceContract {
    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
    const val DEFAULT_STANDARD_REFRESH_RATE_FPS = 60f
    const val DEFAULT_REFRESH_RATE_REQUEST = 0f

    val HOME_BACKGROUND_PARALLAX_RANGE = 0.1f..1.5f
    val HOME_BACKGROUND_BRIGHTNESS_RANGE = 0.1f..1.5f

    const val PALETTE_PICKER_ROUTE = "settings/appearance/palette_picker"

    val DynamicTheme = DynamicThemeKey
    val RandomThemeOnStartup = RandomThemeOnStartupKey
    val DarkModePref = DarkModeKey
    val PureBlack = PureBlackKey
    val BlurNavBar = BlurNavBarKey
    val BlurRadius = BlurRadiusKey
    val DisableAnimations = DisableAnimationsKey
    val SplashOverlayEnabled = SplashOverlayEnabledKey
    val ArchiveTuneCanvas = ArchiveTuneCanvasKey
    val HomeBackgroundStylePref = HomeBackgroundStyleKey
    val HomeBackgroundParallaxEnabled = HomeBackgroundParallaxEnabledKey
    val HomeBackgroundParallaxStrength = HomeBackgroundParallaxStrengthKey
    val HomeBackgroundBrightness = HomeBackgroundBrightnessKey
    val ForceHighRefreshRate = ForceHighRefreshRateKey
    val FontPreference = FontPreferenceKey
    val CustomFontUri = CustomFontUriKey
    val CustomFontName = CustomFontNameKey
    val DefaultOpenTab = DefaultOpenTabKey
    val ChipSortType = ChipSortTypeKey
    val SwipeToSong = SwipeToSongKey
    val ShowTagsInLibrary = ShowTagsInLibraryKey
    val ShowHomeCategoryChips = ShowHomeCategoryChipsKey
    val QuickPicksDisplayModePref = QuickPicksDisplayModeKey
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

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

@Immutable
data class AppearanceSettingsUiState(
    val dynamicTheme: Boolean = true,
    val randomThemeOnStartup: Boolean = false,
    val darkMode: DarkMode = DarkMode.AUTO,
    val useDarkTheme: Boolean = false,
    val pureBlack: Boolean = false,
    val blurNavBar: Boolean = true,
    val blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    val disableAnimations: Boolean = false,
    val splashOverlayEnabled: Boolean = true,
    val archiveTuneCanvas: Boolean = false,
    val homeBackgroundStyle: HomeBackgroundStyle = HomeBackgroundStyle.TONAL,
    val homeBackgroundParallaxEnabled: Boolean = true,
    val homeBackgroundParallaxStrength: Float = 0.6f,
    val homeBackgroundBrightness: Float = 1f,
    val forceHighRefreshRate: Boolean = false,
    val isHighRefreshRateSupported: Boolean = false,
    val supportedHighestFps: Float = 60f,
    val fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    val customFontUri: String = "",
    val customFontName: String = "",
    val quickPicksDisplayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.CARD,
    val defaultOpenTab: NavigationTab = NavigationTab.HOME,
    val defaultChip: LibraryFilter = LibraryFilter.LIBRARY,
    val showHomeCategoryChips: Boolean = true,
    val showTagsInLibrary: Boolean = true,
    val swipeToSong: Boolean = false,
)

typealias AppearanceUiState = AppearanceSettingsUiState

@Immutable
data class AppearanceSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onNavigatePalettePicker: () -> Unit = {},
    val onDynamicThemeChange: (Boolean) -> Unit = {},
    val onRandomThemeOnStartupChange: (Boolean) -> Unit = {},
    val onDarkModeChange: (DarkMode) -> Unit = {},
    val onPureBlackChange: (Boolean) -> Unit = {},
    val onBlurNavBarChange: (Boolean) -> Unit = {},
    val onBlurRadiusChange: (Float) -> Unit = {},
    val onDisableAnimationsChange: (Boolean) -> Unit = {},
    val onSplashOverlayEnabledChange: (Boolean) -> Unit = {},
    val onArchiveTuneCanvasChange: (Boolean) -> Unit = {},
    val onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit = {},
    val onHomeBackgroundParallaxEnabledChange: (Boolean) -> Unit = {},
    val onHomeBackgroundParallaxStrengthChange: (Float) -> Unit = {},
    val onHomeBackgroundBrightnessChange: (Float) -> Unit = {},
    val onForceHighRefreshRateChange: (Boolean) -> Unit = {},
    val onFontPreferenceChange: (AppFontPreference) -> Unit = {},
    val onPickCustomFont: () -> Unit = {},
    val onQuickPicksDisplayModeChange: (QuickPicksDisplayMode) -> Unit = {},
    val onDefaultOpenTabChange: (NavigationTab) -> Unit = {},
    val onDefaultChipChange: (LibraryFilter) -> Unit = {},
    val onShowHomeCategoryChipsChange: (Boolean) -> Unit = {},
    val onShowTagsInLibraryChange: (Boolean) -> Unit = {},
    val onSwipeToSongChange: (Boolean) -> Unit = {},
)

typealias AppearanceUiActions = AppearanceSettingsUiActions
typealias AppearanceActions = AppearanceSettingsUiActions
typealias AppearanceSettingsActions = AppearanceSettingsUiActions
