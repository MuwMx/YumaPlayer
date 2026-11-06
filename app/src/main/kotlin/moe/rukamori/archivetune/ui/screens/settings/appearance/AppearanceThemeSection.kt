/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.SplashShapePreference
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference

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
        splashShapePreference = state.splashShapePreference,
        onSplashShapePreferenceChange = actions.onSplashShapePreferenceChange,
        onNavigateCanvas = actions.onNavigateCanvas,
        homeBackgroundStyle = state.homeBackgroundStyle,
        onHomeBackgroundStyleChange = actions.onHomeBackgroundStyleChange,
        randomHomeBackgroundOnStartup = state.randomHomeBackgroundOnStartup,
        onRandomHomeBackgroundOnStartupChange = actions.onRandomHomeBackgroundOnStartupChange,
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
    splashShapePreference: SplashShapePreference,
    onSplashShapePreferenceChange: (SplashShapePreference) -> Unit,
    onNavigateCanvas: () -> Unit,
    homeBackgroundStyle: HomeBackgroundStyle,
    onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit,
    randomHomeBackgroundOnStartup: Boolean,
    onRandomHomeBackgroundOnStartupChange: (Boolean) -> Unit,
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
            AnimatedVisibility(
                visible = blurNavBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
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

        item(visible = splashOverlayEnabled) {
            EnumListPreference(
                title = { Text(stringResource(R.string.splash_shape_preference)) },
                description = stringResource(R.string.splash_shape_preference_desc),
                icon = { Icon(painterResource(R.drawable.bolt), null, modifier = Modifier.size(24.dp)) },
                selectedValue = splashShapePreference,
                onValueSelected = onSplashShapePreferenceChange,
                valueText = {
                    when (it) {
                        SplashShapePreference.LOGO -> stringResource(R.string.splash_shape_logo)
                        SplashShapePreference.BOLT -> stringResource(R.string.splash_shape_bolt)
                        SplashShapePreference.RANDOM -> stringResource(R.string.splash_shape_random)
                    }
                },
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.archivetune_canvas)) },
                description = stringResource(R.string.canvas_settings_subtitle),
                icon = { Icon(painterResource(R.drawable.motion_photos_on), null, modifier = Modifier.size(24.dp)) },
                onClick = onNavigateCanvas,
                showChevron = true,
            )
        }

        item {
            HomeBackgroundSelector(
                homeBackgroundStyle = homeBackgroundStyle,
                onHomeBackgroundStyleChange = onHomeBackgroundStyleChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.random_home_background_on_startup)) },
                description = stringResource(R.string.random_home_background_on_startup_desc),
                icon = { Icon(painterResource(R.drawable.shuffle), null, modifier = Modifier.size(24.dp)) },
                checked = randomHomeBackgroundOnStartup,
                onCheckedChange = onRandomHomeBackgroundOnStartupChange,
            )
        }
        val hasConfigurableEffects = homeBackgroundStyle != HomeBackgroundStyle.TONAL && homeBackgroundStyle != HomeBackgroundStyle.OFF
        item(visible = hasConfigurableEffects) {
            SwitchPreference(
                title = { Text(stringResource(R.string.home_background_parallax)) },
                icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                checked = homeBackgroundParallaxEnabled,
                onCheckedChange = onHomeBackgroundParallaxEnabledChange,
            )
        }

        item(
            visible = hasConfigurableEffects && homeBackgroundParallaxEnabled,
        ) {
            HomeBackgroundSliderItem(
                title = stringResource(R.string.home_background_parallax_strength),
                value = homeBackgroundParallaxStrength,
                onValueChangeFinished = onHomeBackgroundParallaxStrengthChange,
                valueRange = AppearanceContract.HOME_BACKGROUND_PARALLAX_RANGE,
                valueText = { strength ->
                    String.format(Locale.US, "%.1f", strength)
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
