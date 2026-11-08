/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppFontPreference
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
        onNavigateGraphics = actions.onNavigateGraphics,
        darkMode = state.darkMode,
        onDarkModeChange = actions.onDarkModeChange,
        useDarkTheme = state.useDarkTheme,
        pureBlack = state.pureBlack,
        onPureBlackChange = actions.onPureBlackChange,
        splashOverlayEnabled = state.splashOverlayEnabled,
        onSplashOverlayEnabledChange = actions.onSplashOverlayEnabledChange,
        splashShapePreference = state.splashShapePreference,
        onSplashShapePreferenceChange = actions.onSplashShapePreferenceChange,
        onNavigateCanvas = actions.onNavigateCanvas,
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
    onNavigateGraphics: () -> Unit,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    useDarkTheme: Boolean,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    splashOverlayEnabled: Boolean,
    onSplashOverlayEnabledChange: (Boolean) -> Unit,
    splashShapePreference: SplashShapePreference,
    onSplashShapePreferenceChange: (SplashShapePreference) -> Unit,
    onNavigateCanvas: () -> Unit,
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
            PreferenceEntry(
                title = { Text(stringResource(R.string.settings_graphics_title)) },
                description = stringResource(R.string.settings_graphics_summary),
                icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                onClick = onNavigateGraphics,
                showChevron = true,
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
