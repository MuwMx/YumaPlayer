/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsAnimationStyle
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

object GraphicsContract {
    const val GRAPHICS_SETTINGS_ROUTE = "settings/graphics"

    const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f

    val HOME_BACKGROUND_PARALLAX_RANGE = 0.1f..1.5f
    val HOME_BACKGROUND_BRIGHTNESS_RANGE = 0.1f..1.5f
}

@Immutable
data class GraphicsSettingsUiState(
    val disableBlur: Boolean = false,
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
    val lyricsLineBlur: Boolean = true,
    val lyricsAnimationStyle: LyricsAnimationStyle = LyricsAnimationStyle.NONE,
)

@Immutable
data class GraphicsSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onDisableBlurChange: (Boolean) -> Unit = {},
    val onBlurNavBarChange: (Boolean) -> Unit = {},
    val onBlurRadiusChange: (Float) -> Unit = {},
    val onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit = {},
    val onRandomHomeBackgroundOnStartupChange: (Boolean) -> Unit = {},
    val onHomeBackgroundParallaxEnabledChange: (Boolean) -> Unit = {},
    val onHomeBackgroundParallaxStrengthChange: (Float) -> Unit = {},
    val onHomeBackgroundBrightnessChange: (Float) -> Unit = {},
    val onForceHighRefreshRateChange: (Boolean) -> Unit = {},
    val onDisableAnimationsChange: (Boolean) -> Unit = {},
    val onLyricsLineBlurChange: (Boolean) -> Unit = {},
    val onLyricsAnimationStyleChange: (LyricsAnimationStyle) -> Unit = {},
)
