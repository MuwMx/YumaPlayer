/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

object GraphicsContract {
    const val GRAPHICS_SETTINGS_ROUTE = "settings/graphics"

    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f

    val HOME_BACKGROUND_PARALLAX_RANGE = 0.1f..1.5f
    val HOME_BACKGROUND_BRIGHTNESS_RANGE = 0.1f..1.5f

    const val BLUR_NAV_BAR_BASE_SCORE = 20f
    const val BLUR_NAV_BAR_MAX_RADIUS_SCORE = 20f
    const val PROCEDURAL_BG_BASE_SCORE = 25f
    const val PROCEDURAL_BG_PARALLAX_SCORE = 10f
    const val HIGH_REFRESH_RATE_SCORE = 15f
    const val DISABLE_ANIMATIONS_SCORE_REDUCTION = 10f
    const val MAX_GPU_SCORE = 100f

    val LOAD_COLOR_OPTIMAL = Color(0xFF34C759)
    val LOAD_COLOR_ALERT = Color(0xFFFF9F0A)
    val LOAD_COLOR_THROTTLE = Color(0xFFFF453A)
}

enum class GpuLoadTier(
    @StringRes val statusRes: Int,
    @DrawableRes val drawableRes: Int,
) {
    OPTIMAL(
        statusRes = R.string.settings_graphics_hud_status_optimal,
        drawableRes = moe.rukamori.archivetune.designsystem.R.drawable.yuma_chill,
    ),
    ALERT(
        statusRes = R.string.settings_graphics_hud_status_alert,
        drawableRes = moe.rukamori.archivetune.designsystem.R.drawable.yuma_alert,
    ),
    HIGH_LOAD(
        statusRes = R.string.settings_graphics_hud_status_high,
        drawableRes = moe.rukamori.archivetune.designsystem.R.drawable.yuma_stress,
    ),
    THROTTLE(
        statusRes = R.string.settings_graphics_hud_status_throttle,
        drawableRes = moe.rukamori.archivetune.designsystem.R.drawable.yuma_panic,
    );

    companion object {
        fun fromFraction(fraction: Float): GpuLoadTier {
            val percentage = (fraction.coerceIn(0f, 1f) * 100f).roundToInt()
            return when {
                percentage <= 35 -> OPTIMAL
                percentage <= 60 -> ALERT
                percentage <= 80 -> HIGH_LOAD
                else -> THROTTLE
            }
        }
    }
}

@Immutable
data class GraphicsSettingsUiState(
    val blurNavBar: Boolean = true,
    val blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    val homeBackgroundStyle: HomeBackgroundStyle = HomeBackgroundStyle.TONAL,
    val randomHomeBackgroundOnStartup: Boolean = false,
    val homeBackgroundParallaxEnabled: Boolean = true,
    val homeBackgroundParallaxStrength: Float = 0.6f,
    val homeBackgroundBrightness: Float = 1f,
    val forceHighRefreshRate: Boolean = false,
    val isHighRefreshRateSupported: Boolean = false,
    val supportedHighestFps: Float = 60f,
    val disableAnimations: Boolean = false,
)

@Immutable
data class GraphicsSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onBlurNavBarChange: (Boolean) -> Unit = {},
    val onBlurRadiusChange: (Float) -> Unit = {},
    val onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit = {},
    val onRandomHomeBackgroundOnStartupChange: (Boolean) -> Unit = {},
    val onHomeBackgroundParallaxEnabledChange: (Boolean) -> Unit = {},
    val onHomeBackgroundParallaxStrengthChange: (Float) -> Unit = {},
    val onHomeBackgroundBrightnessChange: (Float) -> Unit = {},
    val onForceHighRefreshRateChange: (Boolean) -> Unit = {},
    val onDisableAnimationsChange: (Boolean) -> Unit = {},
)
