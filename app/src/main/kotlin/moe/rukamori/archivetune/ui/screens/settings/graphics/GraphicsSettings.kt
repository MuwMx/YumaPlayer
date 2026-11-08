/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import java.util.Locale
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.constants.LyricsAnimationStyle
import moe.rukamori.archivetune.constants.LyricsAnimationStyleKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.RandomHomeBackgroundOnStartupKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.ApplyRefreshRate
import moe.rukamori.archivetune.ui.screens.settings.BlurRadiusSliderItem
import moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSelector
import moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSliderItem
import moe.rukamori.archivetune.ui.screens.settings.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.screens.settings.rememberSupportedHighestFps
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun GraphicsSettings(navController: NavController) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }

    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (blurNavBar, onBlurNavBarChange) = rememberPreference(BlurNavBarKey, defaultValue = true)
    val (blurRadius, onBlurRadiusChange) =
        rememberPreference(BlurRadiusKey, defaultValue = SettingsDimensions.BlurRadiusDefault)

    val (homeBackgroundStyle, onHomeBackgroundStyleChange) =
        rememberEnumPreference(HomeBackgroundStyleKey, defaultValue = HomeBackgroundStyle.TONAL)
    val (randomHomeBackgroundOnStartup, onRandomHomeBackgroundOnStartupChange) =
        rememberPreference(RandomHomeBackgroundOnStartupKey, defaultValue = false)
    val (homeBackgroundParallaxEnabled, onHomeBackgroundParallaxEnabledChange) =
        rememberPreference(HomeBackgroundParallaxEnabledKey, defaultValue = true)
    val (homeBackgroundParallaxStrength, onHomeBackgroundParallaxStrengthChange) =
        rememberPreference(HomeBackgroundParallaxStrengthKey, defaultValue = 0.6f)
    val (homeBackgroundBrightness, onHomeBackgroundBrightnessChange) =
        rememberPreference(HomeBackgroundBrightnessKey, defaultValue = 1f)

    val (forceHighRefreshRate, onForceHighRefreshRateChange) =
        rememberPreference(ForceHighRefreshRateKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(DisableAnimationsKey, defaultValue = defaultDisableAnimations)

    val (lyricsLineBlur, onLyricsLineBlurChange) = rememberPreference(LyricsLineBlurKey, defaultValue = true)
    val (lyricsAnimationStyle, onLyricsAnimationStyleChange) =
        rememberEnumPreference(LyricsAnimationStyleKey, defaultValue = LyricsAnimationStyle.NONE)

    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > GraphicsContract.HIGH_REFRESH_RATE_THRESHOLD_FPS

    ApplyRefreshRate(
        isEnabled = forceHighRefreshRate && isHighRefreshRateSupported,
        targetFps = supportedHighestFps,
    )

    val state = GraphicsSettingsUiState(
        disableBlur = disableBlur,
        blurNavBar = blurNavBar,
        blurRadius = blurRadius,
        homeBackgroundStyle = homeBackgroundStyle,
        randomHomeBackgroundOnStartup = randomHomeBackgroundOnStartup,
        homeBackgroundParallaxEnabled = homeBackgroundParallaxEnabled,
        homeBackgroundParallaxStrength = homeBackgroundParallaxStrength,
        homeBackgroundBrightness = homeBackgroundBrightness,
        forceHighRefreshRate = forceHighRefreshRate,
        isHighRefreshRateSupported = isHighRefreshRateSupported,
        supportedHighestFps = supportedHighestFps,
        disableAnimations = disableAnimations,
        lyricsLineBlur = lyricsLineBlur,
        lyricsAnimationStyle = lyricsAnimationStyle,
    )

    val actions = remember(navController) {
        GraphicsSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onDisableBlurChange = onDisableBlurChange,
            onBlurNavBarChange = onBlurNavBarChange,
            onBlurRadiusChange = onBlurRadiusChange,
            onHomeBackgroundStyleChange = onHomeBackgroundStyleChange,
            onRandomHomeBackgroundOnStartupChange = onRandomHomeBackgroundOnStartupChange,
            onHomeBackgroundParallaxEnabledChange = onHomeBackgroundParallaxEnabledChange,
            onHomeBackgroundParallaxStrengthChange = onHomeBackgroundParallaxStrengthChange,
            onHomeBackgroundBrightnessChange = onHomeBackgroundBrightnessChange,
            onForceHighRefreshRateChange = onForceHighRefreshRateChange,
            onDisableAnimationsChange = onDisableAnimationsChange,
            onLyricsLineBlurChange = onLyricsLineBlurChange,
            onLyricsAnimationStyleChange = onLyricsAnimationStyleChange,
        )
    }

    GraphicsSettingsScreen(state = state, actions = actions)
}

@Composable
internal fun GraphicsSettingsScreen(
    state: GraphicsSettingsUiState,
    actions: GraphicsSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.settings_graphics_title)) },
        onBackClick = actions.onNavigateUp,
        onBackLongClick = actions.onNavigateHome,
        modifier = modifier,
    ) {
        // Top HUD Slot: будет заменен на DeviceLoadHudCard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(110.dp)
                .yumaGlassCard(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.speed),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.settings_graphics_hud_placeholder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.settings_graphics_blur_group)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.disable_blur)) },
                    description = stringResource(R.string.disable_blur_desc),
                    icon = { Icon(painterResource(R.drawable.blur_off), null, modifier = Modifier.size(24.dp)) },
                    checked = state.disableBlur,
                    onCheckedChange = actions.onDisableBlurChange,
                )
            }
            item(visible = !state.disableBlur) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.blur_nav_bar)) },
                    description = stringResource(R.string.blur_nav_bar_desc),
                    icon = { Icon(painterResource(R.drawable.blur_on), null, modifier = Modifier.size(24.dp)) },
                    checked = state.blurNavBar,
                    onCheckedChange = actions.onBlurNavBarChange,
                )
            }
            item(visible = !state.disableBlur && state.blurNavBar) {
                BlurRadiusSliderItem(
                    value = state.blurRadius,
                    onValueChangeFinished = actions.onBlurRadiusChange,
                )
            }
            item(visible = !state.disableBlur) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.lyrics_line_blur)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null, modifier = Modifier.size(24.dp)) },
                    checked = state.lyricsLineBlur,
                    onCheckedChange = actions.onLyricsLineBlurChange,
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.home_background)) {
            item {
                HomeBackgroundSelector(
                    homeBackgroundStyle = state.homeBackgroundStyle,
                    onHomeBackgroundStyleChange = actions.onHomeBackgroundStyleChange,
                )
            }
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.random_home_background_on_startup)) },
                    description = stringResource(R.string.random_home_background_on_startup_desc),
                    icon = { Icon(painterResource(R.drawable.shuffle), null, modifier = Modifier.size(24.dp)) },
                    checked = state.randomHomeBackgroundOnStartup,
                    onCheckedChange = actions.onRandomHomeBackgroundOnStartupChange,
                )
            }

            val hasConfigurableEffects =
                state.homeBackgroundStyle != HomeBackgroundStyle.TONAL &&
                    state.homeBackgroundStyle != HomeBackgroundStyle.OFF
            item(visible = hasConfigurableEffects) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.home_background_parallax)) },
                    icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                    checked = state.homeBackgroundParallaxEnabled,
                    onCheckedChange = actions.onHomeBackgroundParallaxEnabledChange,
                )
            }
            item(visible = hasConfigurableEffects && state.homeBackgroundParallaxEnabled) {
                HomeBackgroundSliderItem(
                    title = stringResource(R.string.home_background_parallax_strength),
                    value = state.homeBackgroundParallaxStrength,
                    onValueChangeFinished = actions.onHomeBackgroundParallaxStrengthChange,
                    valueRange = GraphicsContract.HOME_BACKGROUND_PARALLAX_RANGE,
                    valueText = { strength -> String.format(Locale.US, "%.1f", strength) },
                )
            }
            item(visible = state.homeBackgroundStyle != HomeBackgroundStyle.TONAL) {
                HomeBackgroundSliderItem(
                    title = stringResource(R.string.home_background_brightness),
                    value = state.homeBackgroundBrightness,
                    onValueChangeFinished = actions.onHomeBackgroundBrightnessChange,
                    valueRange = GraphicsContract.HOME_BACKGROUND_BRIGHTNESS_RANGE,
                    valueText = { brightness -> "${(brightness * 100).roundToInt()}%" },
                )
            }
        }

        PreferenceGroup(title = stringResource(R.string.settings_graphics_performance_group)) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.force_high_refresh_rate)) },
                    description = stringResource(
                        R.string.max_supported_refresh_rate,
                        state.supportedHighestFps.roundToInt(),
                    ),
                    icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                    checked = state.forceHighRefreshRate,
                    onCheckedChange = actions.onForceHighRefreshRateChange,
                    isEnabled = state.isHighRefreshRateSupported,
                )
            }
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.disable_animations)) },
                    description = stringResource(R.string.disable_animations_desc),
                    icon = { Icon(painterResource(R.drawable.animation), null, modifier = Modifier.size(24.dp)) },
                    checked = state.disableAnimations,
                    onCheckedChange = actions.onDisableAnimationsChange,
                )
            }
            item {
                EnumListPreference(
                    title = { Text(stringResource(R.string.lyrics_animation_style)) },
                    icon = { Icon(painterResource(R.drawable.motion_photos_on), null, modifier = Modifier.size(24.dp)) },
                    selectedValue = state.lyricsAnimationStyle,
                    onValueSelected = actions.onLyricsAnimationStyleChange,
                    valueText = { style ->
                        when (style) {
                            LyricsAnimationStyle.NONE -> stringResource(R.string.lyrics_animation_style_none)
                            LyricsAnimationStyle.FADE -> stringResource(R.string.lyrics_animation_style_fade)
                            LyricsAnimationStyle.GLOW -> stringResource(R.string.lyrics_animation_style_glow)
                            LyricsAnimationStyle.SLIDE -> stringResource(R.string.lyrics_animation_style_slide)
                            LyricsAnimationStyle.KARAOKE -> stringResource(R.string.lyrics_animation_style_karaoke)
                            LyricsAnimationStyle.APPLE -> stringResource(R.string.lyrics_animation_style_apple)
                        }
                    },
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun GraphicsSettingsPreview() {
    TestThemeWrapper {
        GraphicsSettings(navController = rememberNavController())
    }
}
