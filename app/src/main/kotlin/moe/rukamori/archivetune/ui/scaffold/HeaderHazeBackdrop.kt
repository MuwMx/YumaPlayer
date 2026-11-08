/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.scaffold

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun HeaderHazeBackdrop(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    hasCollapsibleHeader: Boolean = false,
    hazeState: HazeState?,
    pureBlack: Boolean? = null,
    blurRadius: Float? = null,
    height: Dp? = null,
    alwaysShowFallback: Boolean = false,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
) {
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurNavBar) = rememberPreference(BlurNavBarKey, true)
    val animationsDisabled = LocalAnimationsDisabled.current
    val (pureBlackPref) = rememberPreference(PureBlackKey, false)
    val (blurRadiusPref) = rememberPreference(BlurRadiusKey, SettingsDimensions.BlurRadiusDefault)

    val effectivePureBlack = pureBlack ?: pureBlackPref
    val effectiveBlurRadius = blurRadius ?: blurRadiusPref

    val isBlurActive = blurNavBar && !disableBlur &&
        !animationsDisabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hazeState != null

    val statusBarTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
    val effectiveHeight = (height ?: (AppBarHeight + statusBarTop)) + SettingsDimensions.HeaderFadeFeather

    val hazeStyle = if (isBlurActive) {
        remember(surfaceColor, effectiveBlurRadius, effectivePureBlack) {
            val fixedTintAlpha = if (effectivePureBlack) {
                SettingsDimensions.HazePureBlackTintAlpha
            } else {
                SettingsDimensions.HazeDefaultTintAlpha
            }
            val tintEffect = HazeColorEffect.tint(surfaceColor.copy(alpha = fixedTintAlpha))
            HazeBlurStyle {
                blurEnabled(true)
                backgroundColor(if (effectivePureBlack) Color.Black else surfaceColor)
                colorEffects(listOf(tintEffect))
                fallbackColorEffect(tintEffect)
                blurRadius(effectiveBlurRadius.dp)
                noiseFactor(SettingsDimensions.HazeNoiseFactor)
                mask(
                    Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.65f to Color.Black.copy(alpha = 0.8f),
                        1.0f to Color.Transparent,
                    ),
                )
            }
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(effectiveHeight)
            .then(
                if (isBlurActive && hazeStyle != null) {
                    Modifier
                        .graphicsLayer {
                            val p = progress()
                            alpha = if (hasCollapsibleHeader) {
                                smoothstep(0.70f, 0.95f, p)
                            } else {
                                p
                            }
                        }
                        .hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = hazeStyle,
                            performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    surfaceColor.copy(alpha = 0.75f),
                                    surfaceColor.copy(alpha = 0.50f),
                                    surfaceColor.copy(alpha = 0.20f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                } else if (alwaysShowFallback || !blurNavBar) {
                    Modifier
                        .graphicsLayer {
                            val p = progress()
                            alpha = if (hasCollapsibleHeader) {
                                smoothstep(0.70f, 0.95f, p)
                            } else {
                                if (alwaysShowFallback) 1f else p
                            }
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    surfaceColor.copy(alpha = 0.95f),
                                    surfaceColor.copy(alpha = 0.85f),
                                    surfaceColor.copy(alpha = 0.6f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                } else {
                    Modifier
                },
            ),
    )
}

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
