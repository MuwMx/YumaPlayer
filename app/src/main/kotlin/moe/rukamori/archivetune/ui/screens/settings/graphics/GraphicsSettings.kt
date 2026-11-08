/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.constants.RandomHomeBackgroundOnStartupKey
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.ApplyRefreshRate
import moe.rukamori.archivetune.ui.screens.settings.BlurRadiusSliderItem
import moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSelector
import moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSliderItem
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.screens.settings.components.playerAwareBottomPadding
import moe.rukamori.archivetune.ui.screens.settings.rememberSupportedHighestFps
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
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

    val (blurNavBar, onBlurNavBarChange) = rememberPreference(BlurNavBarKey, defaultValue = true)
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = SettingsDimensions.BlurRadiusDefault)
    val (homeBackgroundStyle, onHomeBackgroundStyleChange) = rememberEnumPreference(HomeBackgroundStyleKey, defaultValue = HomeBackgroundStyle.TONAL)
    val (randomHomeBackgroundOnStartup, onRandomHomeBackgroundOnStartupChange) = rememberPreference(RandomHomeBackgroundOnStartupKey, defaultValue = false)
    val (homeBackgroundParallaxEnabled, onHomeBackgroundParallaxEnabledChange) = rememberPreference(HomeBackgroundParallaxEnabledKey, defaultValue = true)
    val (homeBackgroundParallaxStrength, onHomeBackgroundParallaxStrengthChange) = rememberPreference(HomeBackgroundParallaxStrengthKey, defaultValue = 0.6f)
    val (homeBackgroundBrightness, onHomeBackgroundBrightnessChange) = rememberPreference(HomeBackgroundBrightnessKey, defaultValue = 1f)
    val (forceHighRefreshRate, onForceHighRefreshRateChange) = rememberPreference(ForceHighRefreshRateKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) = rememberPreference(DisableAnimationsKey, defaultValue = defaultDisableAnimations)

    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > GraphicsContract.HIGH_REFRESH_RATE_THRESHOLD_FPS
    ApplyRefreshRate(isEnabled = forceHighRefreshRate && isHighRefreshRateSupported, targetFps = supportedHighestFps)

    val state = GraphicsSettingsUiState(
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
    )

    val actions = remember(navController) {
        GraphicsSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onBlurNavBarChange = onBlurNavBarChange,
            onBlurRadiusChange = onBlurRadiusChange,
            onHomeBackgroundStyleChange = onHomeBackgroundStyleChange,
            onRandomHomeBackgroundOnStartupChange = onRandomHomeBackgroundOnStartupChange,
            onHomeBackgroundParallaxEnabledChange = onHomeBackgroundParallaxEnabledChange,
            onHomeBackgroundParallaxStrengthChange = onHomeBackgroundParallaxStrengthChange,
            onHomeBackgroundBrightnessChange = onHomeBackgroundBrightnessChange,
            onForceHighRefreshRateChange = onForceHighRefreshRateChange,
            onDisableAnimationsChange = onDisableAnimationsChange,
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
    val loadFraction = remember(
        state.blurNavBar,
        state.blurRadius,
        state.homeBackgroundStyle,
        state.homeBackgroundParallaxEnabled,
        state.forceHighRefreshRate,
        state.disableAnimations,
    ) {
        calculateGpuLoad(state)
    }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.settings_graphics_title)) },
        onBackClick = actions.onNavigateUp,
        onBackLongClick = actions.onNavigateHome,
        modifier = modifier,
    ) {
        DeviceLoadHudCard(
            fraction = loadFraction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .height(110.dp),
        )

        PreferenceGroup(title = stringResource(R.string.settings_graphics_blur_group)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.blur_nav_bar)) },
                        description = stringResource(R.string.blur_nav_bar_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null, modifier = Modifier.size(24.dp)) },
                        checked = state.blurNavBar,
                        onCheckedChange = actions.onBlurNavBarChange,
                    )
                }
                item(visible = state.blurNavBar) {
                    BlurRadiusSliderItem(
                        value = state.blurRadius,
                        onValueChangeFinished = actions.onBlurRadiusChange,
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

                val isProceduralBackground =
                    state.homeBackgroundStyle != HomeBackgroundStyle.OFF &&
                        state.homeBackgroundStyle != HomeBackgroundStyle.TONAL

                item(visible = isProceduralBackground) {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.home_background_parallax)) },
                        icon = { Icon(painterResource(R.drawable.speed), null, modifier = Modifier.size(24.dp)) },
                        checked = state.homeBackgroundParallaxEnabled,
                        onCheckedChange = actions.onHomeBackgroundParallaxEnabledChange,
                    )
                }
                item(visible = isProceduralBackground && state.homeBackgroundParallaxEnabled) {
                    HomeBackgroundSliderItem(
                        title = stringResource(R.string.home_background_parallax_strength),
                        value = state.homeBackgroundParallaxStrength,
                        onValueChangeFinished = actions.onHomeBackgroundParallaxStrengthChange,
                        valueRange = GraphicsContract.HOME_BACKGROUND_PARALLAX_RANGE,
                        valueText = { strength -> String.format(Locale.US, "%.1f", strength) },
                    )
                }
                item(visible = isProceduralBackground) {
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
                        description = stringResource(R.string.max_supported_refresh_rate, state.supportedHighestFps.roundToInt()),
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
            }
    }
}

internal fun calculateGpuLoad(state: GraphicsSettingsUiState): Float {
    var score = 0f

    if (state.blurNavBar) {
        val radiusRatio = ((state.blurRadius - SettingsDimensions.BlurRadiusMin) /
            (SettingsDimensions.BlurRadiusMax - SettingsDimensions.BlurRadiusMin)).coerceIn(0f, 1f)
        score += GraphicsContract.BLUR_NAV_BAR_BASE_SCORE + (radiusRatio * GraphicsContract.BLUR_NAV_BAR_MAX_RADIUS_SCORE)
    }

    val bgScore = when (state.homeBackgroundStyle) {
        HomeBackgroundStyle.OFF, HomeBackgroundStyle.TONAL -> 0f
        HomeBackgroundStyle.CIRCLES, HomeBackgroundStyle.RINGS, HomeBackgroundStyle.GRID -> 15f
        HomeBackgroundStyle.MESH, HomeBackgroundStyle.PARTICLES, HomeBackgroundStyle.SNOW, HomeBackgroundStyle.SPACE -> 25f
        HomeBackgroundStyle.LAVA -> 35f
    }
    if (bgScore > 0f) {
        score += bgScore + if (state.homeBackgroundParallaxEnabled) 15f else 0f
    }

    if (state.forceHighRefreshRate) {
        score += GraphicsContract.HIGH_REFRESH_RATE_SCORE
    }

    if (state.disableAnimations) {
        score = (score - GraphicsContract.DISABLE_ANIMATIONS_SCORE_REDUCTION).coerceAtLeast(0f)
    }

    return (score / GraphicsContract.MAX_GPU_SCORE).coerceIn(0f, 1f)
}

@Composable
internal fun DeviceLoadHudCard(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "DeviceLoadFraction",
    )

    val tier = remember(fraction) {
        GpuLoadTier.fromFraction(fraction)
    }

    val targetColor = remember(animatedFraction) {
        if (animatedFraction <= 0.5f) {
            val t = (animatedFraction / 0.5f).coerceIn(0f, 1f)
            lerp(GraphicsContract.LOAD_COLOR_OPTIMAL, GraphicsContract.LOAD_COLOR_ALERT, t)
        } else {
            val t = ((animatedFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
            lerp(GraphicsContract.LOAD_COLOR_ALERT, GraphicsContract.LOAD_COLOR_THROTTLE, t)
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "DeviceLoadColor",
    )

    Box(modifier = modifier.yumaGlassCard()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_graphics_hud_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )

                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = animatedColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                val percentage = (animatedFraction.coerceIn(0f, 1f) * 100f).roundToInt()
                Text(
                    text = stringResource(R.string.settings_graphics_hud_load_value, percentage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = tier,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "YumaMascotCrossfade",
                ) { currentTier ->
                    Image(
                        painter = painterResource(currentTier.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
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
