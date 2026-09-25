/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun AppearanceThemeSection(
    state: AppearanceUiState,
    actions: AppearanceUiActions,
    modifier: Modifier = Modifier,
) {
    AppearanceThemeSection(
        dynamicTheme = state.dynamicTheme,
        onDynamicThemeChange = actions.onDynamicThemeChange,
        randomThemeOnStartup = state.randomThemeOnStartup,
        onRandomThemeOnStartupChange = actions.onRandomThemeOnStartupChange,
        onNavigatePalettePicker = actions.onNavigatePalettePicker,
        darkMode = state.darkMode,
        onDarkModeChange = actions.onDarkModeChange,
        useDarkTheme = state.useDarkTheme,
        pureBlack = state.pureBlack,
        onPureBlackChange = actions.onPureBlackChange,
        blurNavBar = state.blurNavBar,
        onBlurNavBarChange = actions.onBlurNavBarChange,
        blurRadius = state.blurRadius,
        onBlurRadiusChange = actions.onBlurRadiusChange,
        disableAnimations = state.disableAnimations,
        onDisableAnimationsChange = actions.onDisableAnimationsChange,
        splashOverlayEnabled = state.splashOverlayEnabled,
        onSplashOverlayEnabledChange = actions.onSplashOverlayEnabledChange,
        archiveTuneCanvas = state.archiveTuneCanvas,
        onArchiveTuneCanvasChange = actions.onArchiveTuneCanvasChange,
        homeBackgroundStyle = state.homeBackgroundStyle,
        onHomeBackgroundStyleChange = actions.onHomeBackgroundStyleChange,
        homeBackgroundParallaxEnabled = state.homeBackgroundParallaxEnabled,
        onHomeBackgroundParallaxEnabledChange = actions.onHomeBackgroundParallaxEnabledChange,
        homeBackgroundParallaxStrength = state.homeBackgroundParallaxStrength,
        onHomeBackgroundParallaxStrengthChange = actions.onHomeBackgroundParallaxStrengthChange,
        homeBackgroundBrightness = state.homeBackgroundBrightness,
        onHomeBackgroundBrightnessChange = actions.onHomeBackgroundBrightnessChange,
        forceHighRefreshRate = state.forceHighRefreshRate,
        onForceHighRefreshRateChange = actions.onForceHighRefreshRateChange,
        isHighRefreshRateSupported = state.isHighRefreshRateSupported,
        supportedHighestFps = state.supportedHighestFps,
        fontPreference = state.fontPreference,
        onFontPreferenceChange = actions.onFontPreferenceChange,
        customFontUri = state.customFontUri,
        customFontName = state.customFontName,
        onPickCustomFont = actions.onPickCustomFont,
        modifier = modifier,
    )
}

@Composable
fun AppearanceThemeSection(
    dynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    randomThemeOnStartup: Boolean,
    onRandomThemeOnStartupChange: (Boolean) -> Unit,
    onNavigatePalettePicker: () -> Unit,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    useDarkTheme: Boolean,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    blurNavBar: Boolean,
    onBlurNavBarChange: (Boolean) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    disableAnimations: Boolean,
    onDisableAnimationsChange: (Boolean) -> Unit,
    splashOverlayEnabled: Boolean,
    onSplashOverlayEnabledChange: (Boolean) -> Unit,
    archiveTuneCanvas: Boolean,
    onArchiveTuneCanvasChange: (Boolean) -> Unit,
    homeBackgroundStyle: HomeBackgroundStyle,
    onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit,
    homeBackgroundParallaxEnabled: Boolean,
    onHomeBackgroundParallaxEnabledChange: (Boolean) -> Unit,
    homeBackgroundParallaxStrength: Float,
    onHomeBackgroundParallaxStrengthChange: (Float) -> Unit,
    homeBackgroundBrightness: Float,
    onHomeBackgroundBrightnessChange: (Float) -> Unit,
    forceHighRefreshRate: Boolean,
    onForceHighRefreshRateChange: (Boolean) -> Unit,
    isHighRefreshRateSupported: Boolean,
    supportedHighestFps: Float,
    fontPreference: AppFontPreference,
    onFontPreferenceChange: (AppFontPreference) -> Unit,
    customFontUri: String,
    customFontName: String,
    onPickCustomFont: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.theme),
        modifier = modifier,
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_dynamic_theme)) },
                icon = { Icon(painterResource(R.drawable.ic_palette), null, modifier = Modifier.size(24.dp)) },
                checked = dynamicTheme,
                onCheckedChange = onDynamicThemeChange,
            )
        }

        item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            SwitchPreference(
                title = { Text(stringResource(R.string.random_theme_on_startup)) },
                description = stringResource(R.string.random_theme_on_startup_desc),
                icon = { Icon(painterResource(R.drawable.shuffle), null, modifier = Modifier.size(24.dp)) },
                checked = randomThemeOnStartup,
                onCheckedChange = onRandomThemeOnStartupChange,
            )
        }

        item(visible = !dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.color_palette)) },
                description = stringResource(R.string.customize_theme_colors),
                icon = { Icon(painterResource(R.drawable.format_paint), null, modifier = Modifier.size(24.dp)) },
                onClick = onNavigatePalettePicker,
                showChevron = true,
            )
        }

        item {
            DarkModeSelector(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
            )
        }

        item(visible = useDarkTheme) {
            SwitchPreference(
                title = { Text(stringResource(R.string.pure_black)) },
                icon = { Icon(painterResource(R.drawable.contrast), null, modifier = Modifier.size(24.dp)) },
                checked = pureBlack,
                onCheckedChange = onPureBlackChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.blur_nav_bar)) },
                description = stringResource(R.string.blur_nav_bar_desc),
                icon = { Icon(painterResource(R.drawable.blur_on), null, modifier = Modifier.size(24.dp)) },
                checked = blurNavBar,
                onCheckedChange = onBlurNavBarChange,
            )
        }

        item {
            androidx.compose.animation.AnimatedVisibility(
                visible = blurNavBar,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                BlurRadiusSliderItem(
                    value = blurRadius,
                    onValueChangeFinished = onBlurRadiusChange,
                )
            }
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.disable_animations)) },
                description = stringResource(R.string.disable_animations_desc),
                icon = { Icon(painterResource(R.drawable.animation), null, modifier = Modifier.size(24.dp)) },
                checked = disableAnimations,
                onCheckedChange = onDisableAnimationsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.splash_overlay_enabled)) },
                description = stringResource(R.string.splash_overlay_enabled_desc),
                icon = { Icon(painterResource(R.drawable.auto_awesome), null, modifier = Modifier.size(24.dp)) },
                checked = splashOverlayEnabled,
                onCheckedChange = onSplashOverlayEnabledChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.archivetune_canvas)) },
                description = stringResource(R.string.archivetune_canvas_desc),
                icon = { Icon(painterResource(R.drawable.motion_photos_on), null, modifier = Modifier.size(24.dp)) },
                checked = archiveTuneCanvas,
                onCheckedChange = onArchiveTuneCanvasChange,
            )
        }

        item {
            HomeBackgroundSelector(
                homeBackgroundStyle = homeBackgroundStyle,
                onHomeBackgroundStyleChange = onHomeBackgroundStyleChange,
            )
        }

        item(visible = homeBackgroundStyle != HomeBackgroundStyle.TONAL) {
            SwitchPreference(
                title = { Text(stringResource(R.string.home_background_parallax)) },
                icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                checked = homeBackgroundParallaxEnabled,
                onCheckedChange = onHomeBackgroundParallaxEnabledChange,
            )
        }

        item(
            visible =
                homeBackgroundStyle != HomeBackgroundStyle.TONAL &&
                    homeBackgroundParallaxEnabled,
        ) {
            HomeBackgroundSliderItem(
                title = stringResource(R.string.home_background_parallax_strength),
                value = homeBackgroundParallaxStrength,
                onValueChangeFinished = onHomeBackgroundParallaxStrengthChange,
                valueRange = AppearanceContract.HOME_BACKGROUND_PARALLAX_RANGE,
                valueText = { strength ->
                    String.format(java.util.Locale.US, "%.1f", strength)
                },
            )
        }

        item(visible = homeBackgroundStyle != HomeBackgroundStyle.TONAL) {
            HomeBackgroundSliderItem(
                title = stringResource(R.string.home_background_brightness),
                value = homeBackgroundBrightness,
                onValueChangeFinished = onHomeBackgroundBrightnessChange,
                valueRange = AppearanceContract.HOME_BACKGROUND_BRIGHTNESS_RANGE,
                valueText = { brightness ->
                    "${(brightness * 100).roundToInt()}%"
                },
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.force_high_refresh_rate)) },
                description =
                    stringResource(
                        R.string.max_supported_refresh_rate,
                        supportedHighestFps.roundToInt(),
                    ),
                icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                checked = forceHighRefreshRate,
                onCheckedChange = onForceHighRefreshRateChange,
                isEnabled = isHighRefreshRateSupported,
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.font_preference)) },
                description = stringResource(R.string.font_preference_desc),
                icon = { Icon(painterResource(R.drawable.text_fields), null, modifier = Modifier.size(24.dp)) },
                selectedValue = fontPreference,
                onValueSelected = onFontPreferenceChange,
                valueText = {
                    when (it) {
                        AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                        AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                        AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                    }
                },
            )
        }

        item(visible = fontPreference == AppFontPreference.CUSTOM) {
            val customFontDescription =
                if (customFontName.isNotBlank()) {
                    customFontName
                } else if (customFontUri.isBlank()) {
                    stringResource(R.string.custom_font_desc)
                } else {
                    customFontUri
                }
            PreferenceEntry(
                title = { Text(stringResource(R.string.custom_font)) },
                description = customFontDescription,
                icon = { Icon(painterResource(R.drawable.text_fields), null, modifier = Modifier.size(24.dp)) },
                onClick = onPickCustomFont,
            )
        }
    }
}

@Composable
fun AppearanceMiscSection(
    state: AppearanceUiState,
    actions: AppearanceUiActions,
    modifier: Modifier = Modifier,
) {
    AppearanceMiscSection(
        quickPicksDisplayMode = state.quickPicksDisplayMode,
        onQuickPicksDisplayModeChange = actions.onQuickPicksDisplayModeChange,
        defaultOpenTab = state.defaultOpenTab,
        onDefaultOpenTabChange = actions.onDefaultOpenTabChange,
        defaultChip = state.defaultChip,
        onDefaultChipChange = actions.onDefaultChipChange,
        showHomeCategoryChips = state.showHomeCategoryChips,
        onShowHomeCategoryChipsChange = actions.onShowHomeCategoryChipsChange,
        showTagsInLibrary = state.showTagsInLibrary,
        onShowTagsInLibraryChange = actions.onShowTagsInLibraryChange,
        swipeToSong = state.swipeToSong,
        onSwipeToSongChange = actions.onSwipeToSongChange,
        modifier = modifier,
    )
}

@Composable
fun AppearanceMiscSection(
    quickPicksDisplayMode: QuickPicksDisplayMode,
    onQuickPicksDisplayModeChange: (QuickPicksDisplayMode) -> Unit,
    defaultOpenTab: NavigationTab,
    onDefaultOpenTabChange: (NavigationTab) -> Unit,
    defaultChip: LibraryFilter,
    onDefaultChipChange: (LibraryFilter) -> Unit,
    showHomeCategoryChips: Boolean,
    onShowHomeCategoryChipsChange: (Boolean) -> Unit,
    showTagsInLibrary: Boolean,
    onShowTagsInLibraryChange: (Boolean) -> Unit,
    swipeToSong: Boolean,
    onSwipeToSongChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.misc),
        modifier = modifier,
    ) {
        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.quick_picks_display_mode)) },
                icon = { Icon(painterResource(R.drawable.grid_view), null, modifier = Modifier.size(24.dp)) },
                selectedValue = quickPicksDisplayMode,
                onValueSelected = onQuickPicksDisplayModeChange,
                valueText = {
                    when (it) {
                        QuickPicksDisplayMode.CARD -> stringResource(R.string.quick_picks_display_mode_card)
                        QuickPicksDisplayMode.LIST -> stringResource(R.string.quick_picks_display_mode_list)
                    }
                },
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.default_open_tab)) },
                icon = { Icon(painterResource(R.drawable.nav_bar), null, modifier = Modifier.size(24.dp)) },
                selectedValue = defaultOpenTab,
                onValueSelected = onDefaultOpenTabChange,
                valueText = {
                    when (it) {
                        NavigationTab.HOME -> stringResource(R.string.home)
                        NavigationTab.SEARCH -> stringResource(R.string.search)
                        NavigationTab.MOODANDGENRES -> stringResource(R.string.mood_and_genres)
                        NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                    }
                },
            )
        }

        item {
            ListPreference(
                title = { Text(stringResource(R.string.default_lib_chips)) },
                icon = { Icon(painterResource(R.drawable.tab), null, modifier = Modifier.size(24.dp)) },
                selectedValue = defaultChip,
                values =
                    listOf(
                        LibraryFilter.LIBRARY,
                        LibraryFilter.PLAYLISTS,
                        LibraryFilter.SONGS,
                        LibraryFilter.ALBUMS,
                        LibraryFilter.ARTISTS,
                    ),
                valueText = {
                    when (it) {
                        LibraryFilter.SONGS -> stringResource(R.string.songs)
                        LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                        LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                        LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                        LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                        LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                    }
                },
                onValueSelected = onDefaultChipChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.show_home_category_chips)) },
                description = stringResource(R.string.show_home_category_chips_desc),
                icon = { Icon(painterResource(R.drawable.ic_home_outline), null, modifier = Modifier.size(24.dp)) },
                checked = showHomeCategoryChips,
                onCheckedChange = onShowHomeCategoryChipsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.show_tags_in_library)) },
                description = stringResource(R.string.show_tags_in_library_desc),
                icon = { Icon(painterResource(R.drawable.filter_alt), null, modifier = Modifier.size(24.dp)) },
                checked = showTagsInLibrary,
                onCheckedChange = onShowTagsInLibraryChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.swipe_song_to_add)) },
                icon = { Icon(painterResource(R.drawable.swipe), null, modifier = Modifier.size(24.dp)) },
                checked = swipeToSong,
                onCheckedChange = onSwipeToSongChange,
            )
        }
    }
}

@Composable
fun ApplyRefreshRate(
    isEnabled: Boolean,
    targetFps: Float,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val requestedFps = if (isEnabled) targetFps else AppearanceContract.DEFAULT_REFRESH_RATE_REQUEST

    DisposableEffect(view, activity, requestedFps) {
        applyRefreshRate(
            view = view,
            activity = activity,
            requestedFps = requestedFps,
        )

        onDispose {
            applyRefreshRate(
                view = view,
                activity = activity,
                requestedFps = AppearanceContract.DEFAULT_REFRESH_RATE_REQUEST,
            )
        }
    }
}

@Composable
fun rememberSupportedHighestFps(): Float {
    val view = LocalView.current

    return remember(view) {
        val display = view.display
        display?.supportedModes
            ?.maxOfOrNull { mode -> mode.refreshRate }
            ?: display?.refreshRate
            ?: AppearanceContract.DEFAULT_STANDARD_REFRESH_RATE_FPS
    }
}

private fun applyRefreshRate(
    view: View,
    activity: Activity?,
    requestedFps: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        view.setRequestedFrameRate(requestedFps)
        return
    }

    activity?.window?.let { window ->
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != requestedFps) {
            attributes.preferredRefreshRate = requestedFps
            window.attributes = attributes
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun BlurRadiusSliderItem(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
) {
    var localValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }

    val steps = (SettingsDimensions.BlurRadiusMax - SettingsDimensions.BlurRadiusMin).roundToInt() - 1
    val sliderState =
        rememberSliderState(
            value = localValue,
            steps = steps,
            valueRange = SettingsDimensions.BlurRadiusMin..SettingsDimensions.BlurRadiusMax,
            onValueChangeFinished = { onValueChangeFinished(localValue.roundToInt().toFloat()) },
        )
    sliderState.onValueChange = { localValue = it.roundToInt().toFloat() }
    sliderState.value = localValue

    PreferenceEntry(
        title = { Text(stringResource(R.string.blur_radius)) },
        description = "${stringResource(R.string.blur_radius_desc)} (${localValue.roundToInt()} dp)",
        content = {
            Spacer(Modifier.height(4.dp))
            Slider(
                state = sliderState,
                modifier = Modifier.fillMaxWidth(),
                track = {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        trackCornerSize = 12.dp,
                    )
                },
            )
        },
    )
}

@Composable
private fun HomeBackgroundSliderItem(
    title: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
) {
    var localValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }

    val sliderState =
        rememberSliderState(
            value = localValue,
            steps = 19,
            valueRange = valueRange,
            onValueChangeFinished = { onValueChangeFinished(localValue) },
        )
    sliderState.onValueChange = { localValue = it }
    sliderState.value = localValue

    PreferenceEntry(
        title = { Text(title) },
        description = valueText(localValue),
        content = {
            Spacer(Modifier.height(4.dp))
            Slider(
                state = sliderState,
                modifier = Modifier.fillMaxWidth(),
                track = {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        trackCornerSize = 12.dp,
                    )
                },
            )
        },
    )
}

@Composable
fun DarkModeSelector(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
) {
    EnumListPreference(
        title = { Text(stringResource(R.string.dark_theme)) },
        icon = { Icon(painterResource(R.drawable.dark_mode), null, modifier = Modifier.size(24.dp)) },
        selectedValue = darkMode,
        onValueSelected = onDarkModeChange,
        valueText = {
            when (it) {
                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
            }
        },
    )
}

@Composable
fun HomeBackgroundSelector(
    homeBackgroundStyle: HomeBackgroundStyle,
    onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit,
) {
    EnumListPreference(
        title = { Text(stringResource(R.string.home_background)) },
        icon = { Icon(painterResource(R.drawable.image), null, modifier = Modifier.size(24.dp)) },
        selectedValue = homeBackgroundStyle,
        onValueSelected = onHomeBackgroundStyleChange,
        valueText = {
            when (it) {
                HomeBackgroundStyle.TONAL -> stringResource(R.string.home_background_tonal)
                HomeBackgroundStyle.CIRCLES -> stringResource(R.string.home_background_circles)
                HomeBackgroundStyle.RINGS -> stringResource(R.string.home_background_rings)
                HomeBackgroundStyle.MESH -> stringResource(R.string.home_background_mesh)
                HomeBackgroundStyle.GRID -> stringResource(R.string.home_background_grid)
                HomeBackgroundStyle.PARTICLES -> stringResource(R.string.home_background_particles)
                HomeBackgroundStyle.SNOW -> stringResource(R.string.home_background_snow)
                HomeBackgroundStyle.SPACE -> stringResource(R.string.home_background_space)
            }
        },
    )
}

@Composable
internal fun AppearanceSettingsContent(
    state: AppearanceSettingsUiState,
    actions: AppearanceSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = SettingsDimensions.ScreenBottomPadding),
    ) {
        AppearanceThemeSection(
            state = state,
            actions = actions,
        )
        AppearanceMiscSection(
            state = state,
            actions = actions,
        )
    }
}
